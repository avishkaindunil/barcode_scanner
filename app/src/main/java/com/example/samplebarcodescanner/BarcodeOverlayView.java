package com.example.samplebarcodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Button;
import android.view.View.OnClickListener;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BarcodeOverlayView extends View {
    private List<MainActivity.StabilizedBarcode> barcodes;
    private Map<String, Integer> barcodeColors;
    private final Paint boundingRectPaint;
    private final Paint contentRectPaint;
    private final Paint contentTextPaint;
    private final Paint iconPaint;
    private final Paint borderPaint;
    private final int contentPadding = 25;
    private int previewWidth;
    private int previewHeight;
    private Context context;
    private Bitmap appleBitmap; // Bitmap for apple.jpg
    private Map<String, PopupWindow> activePopups = new HashMap<>();

    public BarcodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        boundingRectPaint = new Paint();
        boundingRectPaint.setStyle(Paint.Style.STROKE);
        boundingRectPaint.setStrokeWidth(7F);
        boundingRectPaint.setAlpha(200);

        contentRectPaint = new Paint();
        contentRectPaint.setStyle(Paint.Style.FILL);
        contentRectPaint.setColor(Color.GREEN);
        contentRectPaint.setAlpha(255);

        contentTextPaint = new Paint();
        contentTextPaint.setColor(Color.WHITE);
        contentTextPaint.setAlpha(255);
        contentTextPaint.setTextSize(36F);

        iconPaint = new Paint();
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setAntiAlias(true);
        borderPaint.setStrokeWidth(4F);

        // Load apple.jpg from resources
        appleBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.apple);
    }

    public void setBarcodes(List<MainActivity.StabilizedBarcode> barcodes, Map<String, Integer> barcodeColors, int previewWidth, int previewHeight) {
        this.barcodes = barcodes;
        this.barcodeColors = barcodeColors;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long startTime = System.nanoTime();

        super.onDraw(canvas);

        if (barcodes != null && previewWidth > 0 && previewHeight > 0) {
            float scaleX = getWidth() / (float) previewWidth;
            float scaleY = getHeight() / (float) previewHeight;

            for (MainActivity.StabilizedBarcode barcode : barcodes) {
                Rect boundingBox = barcode.getBoundingBox();
                float left = boundingBox.left * scaleX;
                float top = boundingBox.top * scaleY;
                float right = boundingBox.right * scaleX;
                float bottom = boundingBox.bottom * scaleY;

                int color = barcodeColors.getOrDefault(barcode.getValue(), Color.GREEN);
                boundingRectPaint.setColor(color);
                iconPaint.setColor(color);
                borderPaint.setColor(color);

                canvas.drawRect(left, top, right, bottom, boundingRectPaint);

                String barcodeContent = barcode.getValue();
                float textWidth = contentTextPaint.measureText(barcodeContent);

                canvas.drawRect(
                        left,
                        bottom + contentPadding / 2,
                        left + textWidth + contentPadding * 2,
                        bottom + contentTextPaint.getTextSize() + contentPadding,
                        contentRectPaint
                );

                canvas.drawText(
                        barcodeContent,
                        left + contentPadding,
                        bottom + contentPadding * 2 + contentTextPaint.getTextSize(),
                        contentTextPaint
                );

                float centerX = left + (right - left) / 2;
                float centerY = top + (bottom - top) / 2;

                drawPlusIconWithBorder(canvas, centerX, centerY, color);

                barcode.setIconBounds(centerX, centerY, 50);

                if (activePopups.containsKey(barcode.getValue())) {
                    PopupWindow popupWindow = activePopups.get(barcode.getValue());
                    int popupX = (int) (boundingBox.left * scaleX);
                    int popupY = (int) (boundingBox.bottom * scaleY);
                    popupWindow.update(popupX, popupY, -1, -1);
                }
            }
        } else {
            Log.d("BarcodeOverlayView", "No barcodes to draw");
        }

        long endTime = System.nanoTime();

        long totalDuration = (endTime - startTime) / 1000;
        Log.d("BarcodeOverlayView", "Total onDraw execution time: " + totalDuration + " μs");
    }

    private void drawPlusIconWithBorder(Canvas canvas, float centerX, float centerY, int color) {
        float iconSize = 30;
        float barThickness = 5;
        float borderRadius = 50;
        float borderSize = iconSize + 15;

        RectF borderRect = new RectF(
                centerX - borderSize / 2,
                centerY - borderSize / 2,
                centerX + borderSize / 2,
                centerY + borderSize / 2
        );
        canvas.drawRoundRect(borderRect, borderRadius, borderRadius, borderPaint);

        iconPaint.setColor(color);

        canvas.drawRect(
                centerX - barThickness / 2,
                centerY - iconSize / 2,
                centerX + barThickness / 2,
                centerY + iconSize / 2,
                iconPaint
        );

        canvas.drawRect(
                centerX - iconSize / 2,
                centerY - barThickness / 2,
                centerX + iconSize / 2,
                centerY + barThickness / 2,
                iconPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float touchX = event.getX();
            float touchY = event.getY();

            for (MainActivity.StabilizedBarcode barcode : barcodes) {
                RectF iconBounds = barcode.getIconBounds();
                if (iconBounds != null && iconBounds.contains(touchX, touchY)) {
                    showBarcodeMenu(barcode);
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private void showBarcodeMenu(MainActivity.StabilizedBarcode barcode) {
        if (activePopups.containsKey(barcode.getValue())) {
            return;
        }

        PopupWindow popupWindow = new PopupWindow(context);
        popupWindow.setWidth(770);
        popupWindow.setHeight(700);
        popupWindow.setFocusable(true);

        View menuView = View.inflate(context, R.layout.barcode_menu, null);

        ImageView barcodeImageView = menuView.findViewById(R.id.barcodeImageView);
        TextView barcodeDetailsTextView = menuView.findViewById(R.id.barcodeDetailsTextView);
        Button cancelButton = menuView.findViewById(R.id.cancelButton);
        Button okButton = menuView.findViewById(R.id.okButton);

        // Set apple image (make sure the image is correctly placed in drawable resources)
        barcodeImageView.setImageBitmap(Bitmap.createScaledBitmap(appleBitmap, 100, 126, false));
        barcodeDetailsTextView.setText("Barcode Value: " + (barcode.getValue() != null ? barcode.getValue() : "No value found"));

        cancelButton.setOnClickListener(v -> {
            popupWindow.dismiss();
        });

        okButton.setOnClickListener(v -> {
            popupWindow.dismiss();
        });

        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
        backgroundDrawable.setColor(Color.WHITE);
        backgroundDrawable.setCornerRadius(0);
        backgroundDrawable.setStroke(0, Color.WHITE);

        menuView.setBackground(backgroundDrawable);

        popupWindow.setContentView(menuView);

        Rect boundingBox = barcode.getBoundingBox();
        float scaleX = getWidth() / (float) previewWidth;
        float scaleY = getHeight() / (float) previewHeight;

        int popupX = (int) (boundingBox.left * scaleX);
        int popupY = (int) (boundingBox.bottom * scaleY);
        popupWindow.showAtLocation(this, 0, popupX, popupY);

        activePopups.put(barcode.getValue(), popupWindow);
    }
}
