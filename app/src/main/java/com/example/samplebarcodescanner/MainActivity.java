package com.example.samplebarcodescanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.annotation.OptIn;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private BarcodeOverlayView barcodeOverlayView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private Button imageCaptureButton;
    private MediaPlayer mediaPlayer;

    private static final String TAG = "BarcodeScanner";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private boolean isCaptureMode = false;

    private Map<String, List<StabilizedBarcode>> trackedBarcodes = new HashMap<>();
    private Map<String, Integer> barcodeColors = new HashMap<>();
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        previewView = findViewById(R.id.previewView);
        barcodeOverlayView = findViewById(R.id.barcodeOverlay);
        imageCaptureButton = findViewById(R.id.imageCaptureButton);

        mediaPlayer = MediaPlayer.create(this, R.raw.beep);

        cameraExecutor = Executors.newFixedThreadPool(10);
        barcodeScanner = BarcodeScanning.getClient();

        imageCaptureButton.setOnClickListener(view -> {
            if (!isCaptureMode) {
                if (allPermissionsGranted()) {
                    startCamera();
                    imageCaptureButton.setText("CAPTURE");
                    isCaptureMode = true;

                    previewView.setVisibility(View.VISIBLE);
                    barcodeOverlayView.setVisibility(View.VISIBLE);
                } else {
                    ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                }
            } else {
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    Log.d(TAG, "Beep sound played.");
                } else {
                    Log.e(TAG, "MediaPlayer is not initialized.");
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1080, 1920))
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(1080, 1920))
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1080, 1920))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::scanBarcodes);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Binding failed", e);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void scanBarcodes(ImageProxy image) {
        try {
            if (image.getImage() == null || image.getFormat() != ImageFormat.YUV_420_888) {
                image.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(this::processBarcodes)
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                    .addOnCompleteListener(task -> image.close());
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            image.close();
        }
    }

    private void processBarcodes(List<Barcode> barcodes) {
        Log.d(TAG, "Number of barcodes detected: " + barcodes.size());

        Map<String, List<StabilizedBarcode>> currentBarcodes = new HashMap<>();

        for (Barcode barcode : barcodes) {
            if (barcode.getBoundingBox() != null) {
                String barcodeValue = barcode.getRawValue();
                if (barcodeValue == null) continue;

                Rect boundingBox = barcode.getBoundingBox();

                barcodeColors.putIfAbsent(barcodeValue, getRandomColor());

                StabilizedBarcode stabilizedBarcode = new StabilizedBarcode(barcodeValue, boundingBox);
                if (trackedBarcodes.containsKey(barcodeValue)) {
                    List<StabilizedBarcode> existingBarcodes = trackedBarcodes.get(barcodeValue);
                    boolean matched = false;
                    for (StabilizedBarcode existing : existingBarcodes) {
                        if (existing.isCloseTo(stabilizedBarcode)) {
                            existing.update(boundingBox);
                            matched = true;
                            if (!currentBarcodes.containsKey(barcodeValue)) {
                                currentBarcodes.put(barcodeValue, new ArrayList<>());
                            }
                            currentBarcodes.get(barcodeValue).add(existing);
                            break;
                        }
                    }
                    if (!matched) {
                        if (!currentBarcodes.containsKey(barcodeValue)) {
                            currentBarcodes.put(barcodeValue, new ArrayList<>());
                        }
                        currentBarcodes.get(barcodeValue).add(stabilizedBarcode);
                    }
                } else {
                    List<StabilizedBarcode> newList = new ArrayList<>();
                    newList.add(stabilizedBarcode);
                    currentBarcodes.put(barcodeValue, newList);
                }
            }
        }

        trackedBarcodes = currentBarcodes;

        List<StabilizedBarcode> allStabilizedBarcodes = new ArrayList<>();
        for (List<StabilizedBarcode> barcodeList : trackedBarcodes.values()) {
            allStabilizedBarcodes.addAll(barcodeList);
        }
        barcodeOverlayView.setBarcodes(allStabilizedBarcodes, barcodeColors, previewView.getWidth(), previewView.getHeight());
        barcodeOverlayView.invalidate();
    }

    private int getRandomColor() {
        return Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                imageCaptureButton.setText("CAPTURE");
                isCaptureMode = true;
                previewView.setVisibility(View.VISIBLE);
                barcodeOverlayView.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public static class StabilizedBarcode {
        private static final int SMOOTHING_WINDOW_SIZE = 10; // Number of frames for stabilization

        private final String value;
        private KalmanFilter kalmanFilter;
        private Rect boundingBox;
        private RectF iconBounds;
        private final LinkedList<Rect> boundingBoxHistory = new LinkedList<>();

        StabilizedBarcode(String value, Rect boundingBox) {
            this.value = value;
            this.boundingBox = boundingBox;
            this.kalmanFilter = new KalmanFilter(boundingBox);
            setIconBounds((boundingBox.left + boundingBox.right) / 2, (boundingBox.top + boundingBox.bottom) / 2, 50);
        }

        void update(Rect newBoundingBox) {
            if (boundingBoxHistory.size() >= SMOOTHING_WINDOW_SIZE) {
                boundingBoxHistory.poll();
            }
            boundingBoxHistory.add(newBoundingBox);
            this.boundingBox = kalmanFilter.predictAndUpdate(newBoundingBox);
            setIconBounds((boundingBox.left + boundingBox.right) / 2, (boundingBox.top + boundingBox.bottom) / 2, 50);
        }

        Rect getBoundingBox() {
            return boundingBox;
        }

        String getValue() {
            return value;
        }

        void setIconBounds(float centerX, float centerY, float size) {
            float halfSize = size / 2;
            this.iconBounds = new RectF(centerX - halfSize, centerY - halfSize, centerX + halfSize, centerY + halfSize);
        }

        RectF getIconBounds() {
            return iconBounds;
        }

        boolean isCloseTo(StabilizedBarcode other) {
            return Math.abs(this.boundingBox.left - other.boundingBox.left) < 30 &&
                    Math.abs(this.boundingBox.top - other.boundingBox.top) < 30 &&
                    Math.abs(this.boundingBox.right - other.boundingBox.right) < 30 &&
                    Math.abs(this.boundingBox.bottom - other.boundingBox.bottom) < 30;
        }
    }

    public static class KalmanFilter {
        private float[] state;  // [left, top, right, bottom]
        private float[][] errorCovariance;
        private float processNoise;
        private float measurementNoise;

        public KalmanFilter(Rect initial) {
            state = new float[]{initial.left, initial.top, initial.right, initial.bottom};
            errorCovariance = new float[][]{
                    {1, 0, 0, 0},
                    {0, 1, 0, 0},
                    {0, 0, 1, 0},
                    {0, 0, 0, 1}
            };
            processNoise = 1e-7f;  // Reduced process noise for smoother tracking
            measurementNoise = 1e-5f; // Reduced measurement noise to expect less variation
        }

        public Rect predictAndUpdate(Rect measurement) {
            // Prediction step
            for (int i = 0; i < state.length; i++) {
                errorCovariance[i][i] += processNoise;
            }

            // Update step
            float[] measurementVec = new float[]{measurement.left, measurement.top, measurement.right, measurement.bottom};
            float[] kalmanGain = new float[state.length];  // Calculate Kalman gain for each state
            for (int i = 0; i < state.length; i++) {
                kalmanGain[i] = errorCovariance[i][i] / (errorCovariance[i][i] + measurementNoise);
            }

            for (int i = 0; i < state.length; i++) {
                state[i] = state[i] + kalmanGain[i] * (measurementVec[i] - state[i]);
                errorCovariance[i][i] = (1 - kalmanGain[i]) * errorCovariance[i][i];
            }

            return new Rect((int) state[0], (int) state[1], (int) state[2], (int) state[3]);
        }
    }
}
