package com.example.samplebarcodescanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.List;

public class BarcodeOverlayView extends View {
    private List<Barcode> barcodes;
    private final Paint boundingRectPaint;
    private final Paint contentRectPaint;
    private final Paint contentTextPaint;
    private final int contentPadding = 25;
    private int previewWidth; // Ensure these are declared
    private int previewHeight; // Ensure these are declared

    public BarcodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boundingRectPaint = new Paint();
        boundingRectPaint.setStyle(Paint.Style.STROKE);
        boundingRectPaint.setColor(Color.GREEN);
        boundingRectPaint.setStrokeWidth(5F);
        boundingRectPaint.setAlpha(200);

        contentRectPaint = new Paint();
        contentRectPaint.setStyle(Paint.Style.FILL);
        contentRectPaint.setColor(Color.GREEN);
        contentRectPaint.setAlpha(255);

        contentTextPaint = new Paint();
        contentTextPaint.setColor(Color.DKGRAY);
        contentTextPaint.setAlpha(255);
        contentTextPaint.setTextSize(36F);
    }

    public void setBarcodes(List<Barcode> barcodes, int previewWidth, int previewHeight) {
        this.barcodes = barcodes;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (barcodes != null) {
            for (Barcode barcode : barcodes) {
                Rect boundingBox = barcode.getBoundingBox();
                if (boundingBox != null) {
                    float scaleX = getWidth() / (float) previewWidth;
                    float scaleY = getHeight() / (float) previewHeight;

                    float left = boundingBox.left * scaleX;
                    float top = boundingBox.top * scaleY;
                    float right = boundingBox.right * scaleX;
                    float bottom = boundingBox.bottom * scaleY;

                    canvas.drawRect(left, top, right, bottom, boundingRectPaint);

                    String barcodeContent = barcode.getRawValue() != null ? barcode.getRawValue() : "";
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
                }
            }
        }
    }
}
