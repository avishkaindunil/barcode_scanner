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
    private final Paint paint;
    private int previewWidth;
    private int previewHeight;

    public BarcodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8.0f);
    }

    public void setBarcodes(List<Barcode> barcodes, int previewWidth, int previewHeight) {
        this.barcodes = barcodes;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        invalidate();
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
                    canvas.drawRect(left, top, right, bottom, paint);
                }
            }
        }
    }
}
