package com.example.samplebarcodescanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.List;

public class BarcodeOverlayView extends View {
    private List<Barcode> barcodes;
    private final Paint paint;

    public BarcodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8.0f);
    }

    public void setBarcodes(List<Barcode> barcodes) {
        this.barcodes = barcodes;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (barcodes != null) {
            for (Barcode barcode : barcodes) {
                canvas.drawRect(barcode.getBoundingBox(), paint);
            }
        }
    }
}
