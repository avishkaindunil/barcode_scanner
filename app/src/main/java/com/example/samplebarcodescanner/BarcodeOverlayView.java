package com.example.samplebarcodescanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

import java.util.List;
import java.util.Map;

public class BarcodeOverlayView extends View {
    private List<MainActivity.StabilizedBarcode> barcodes;
    private Map<String, Integer> barcodeColors;
    private final Paint boundingRectPaint;
    private final Paint contentRectPaint;
    private final Paint contentTextPaint;
    private final int contentPadding = 25;
    private int previewWidth;
    private int previewHeight;

    public BarcodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boundingRectPaint = new Paint();
        boundingRectPaint.setStyle(Paint.Style.STROKE);
        boundingRectPaint.setStrokeWidth(5F);
        boundingRectPaint.setAlpha(200);

        contentRectPaint = new Paint();
        contentRectPaint.setStyle(Paint.Style.FILL);
        contentRectPaint.setColor(Color.GREEN);
        contentRectPaint.setAlpha(255);

        contentTextPaint = new Paint();
        contentTextPaint.setColor(Color.WHITE);
        contentTextPaint.setAlpha(255);
        contentTextPaint.setTextSize(36F);
    }

    public void setBarcodes(List<MainActivity.StabilizedBarcode> barcodes, Map<String, Integer> barcodeColors, int previewWidth, int previewHeight) {
        this.barcodes = barcodes;
        this.barcodeColors = barcodeColors;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
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

                // Set color for each barcode using consistent color mapping
                int color = barcodeColors.getOrDefault(barcode.getValue(), Color.GREEN);
                boundingRectPaint.setColor(color);

                // Draw the bounding box on the canvas
                canvas.drawRect(left, top, right, bottom, boundingRectPaint);

                // Draw barcode content text below bounding box
                String barcodeContent = barcode.getValue();
                float textWidth = contentTextPaint.measureText(barcodeContent);

                // Draw a filled rectangle below the bounding box to display barcode content
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
            }
        } else {
            Log.d("BarcodeOverlayView", "No barcodes to draw");
        }
    }
}
