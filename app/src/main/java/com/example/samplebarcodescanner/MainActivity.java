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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.camera.view.PreviewView;

import androidx.annotation.OptIn;
import android.graphics.Rect;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private Map<String, StabilizedBarcode> trackedBarcodes = new HashMap<>();

    private static final float SMOOTHING_FACTOR = 0.30f; // Adjusted smoothing factor

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

        cameraExecutor = Executors.newSingleThreadExecutor();
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
        if (image.getImage() == null) {
            image.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(this::processBarcodes)
                .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                .addOnCompleteListener(task -> image.close());
    }

    private void processBarcodes(List<Barcode> barcodes) {
        Map<String, StabilizedBarcode> currentBarcodes = new HashMap<>();

        for (Barcode barcode : barcodes) {
            if (barcode.getBoundingBox() != null) {
                String barcodeValue = barcode.getRawValue();
                Rect boundingBox = barcode.getBoundingBox();

                if (trackedBarcodes.containsKey(barcodeValue)) {
                    trackedBarcodes.get(barcodeValue).update(boundingBox, SMOOTHING_FACTOR);
                } else {
                    trackedBarcodes.put(barcodeValue, new StabilizedBarcode(barcodeValue, boundingBox));
                }

                currentBarcodes.put(barcodeValue, trackedBarcodes.get(barcodeValue));
            }
        }

        trackedBarcodes = currentBarcodes; // Update tracked barcodes to only include current ones

        List<StabilizedBarcode> stabilizedBarcodes = new ArrayList<>(trackedBarcodes.values());
        barcodeOverlayView.setBarcodes(stabilizedBarcodes, previewView.getWidth(), previewView.getHeight());
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
        private final String value;
        private float left;
        private float top;
        private float right;
        private float bottom;

        StabilizedBarcode(String value, Rect boundingBox) {
            this.value = value;
            this.left = boundingBox.left;
            this.top = boundingBox.top;
            this.right = boundingBox.right;
            this.bottom = boundingBox.bottom;
        }

        void update(Rect boundingBox, float smoothingFactor) {
            // Only update if there's a significant change
            if (Math.abs(boundingBox.left - left) > 5 ||
                    Math.abs(boundingBox.top - top) > 5 ||
                    Math.abs(boundingBox.right - right) > 5 ||
                    Math.abs(boundingBox.bottom - bottom) > 5) {

                this.left = smoothingFactor * boundingBox.left + (1 - smoothingFactor) * this.left;
                this.top = smoothingFactor * boundingBox.top + (1 - smoothingFactor) * this.top;
                this.right = smoothingFactor * boundingBox.right + (1 - smoothingFactor) * this.right;
                this.bottom = smoothingFactor * boundingBox.bottom + (1 - smoothingFactor) * this.bottom;
            }
        }

        Rect getBoundingBox() {
            return new Rect((int) left, (int) top, (int) right, (int) bottom);
        }

        String getValue() {
            return value;
        }
    }
}
