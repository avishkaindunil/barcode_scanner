package com.example.samplebarcodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

public class BarcodeOverlayView extends View {
    private List<MainActivity.StabilizedBarcode> barcodes;
    private Map<String, Integer> barcodeColors;
    private final Paint boundingRectPaint;
    private final Paint contentRectPaint;
    private final Paint contentTextPaint;
    private final Paint iconPaint;
    private final Paint borderPaint; // Paint for the rounded border
    private final int contentPadding = 25;
    private int previewWidth;
    private int previewHeight;
    private Context context; // Reference for context to show the menu
    private Bitmap currentFrameBitmap; // Temporary storage for the barcode image

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
        iconPaint.setAntiAlias(true); // Smooth edges for the icon

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setAntiAlias(true); // Smooth edges for rounded border
        borderPaint.setStrokeWidth(4F); // Thickness of the border
    }

    public void setBarcodes(List<MainActivity.StabilizedBarcode> barcodes, Map<String, Integer> barcodeColors, int previewWidth, int previewHeight) {
        this.barcodes = barcodes;
        this.barcodeColors = barcodeColors;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        invalidate(); // Request a redraw
    }

    public void setCurrentFrameBitmap(Bitmap bitmap) {
        this.currentFrameBitmap = bitmap; // Store the current frame's bitmap
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

                // Set color for each barcode using consistent color mapping
                int color = barcodeColors.getOrDefault(barcode.getValue(), Color.GREEN);
                boundingRectPaint.setColor(color);
                iconPaint.setColor(color);
                borderPaint.setColor(color); // Border color matches the bounding box color

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

                // Calculate the center of the bounding box
                float centerX = left + (right - left) / 2;
                float centerY = top + (bottom - top) / 2;

                // Draw the plus icon at the center of the bounding box with a rounded border and gap
                drawPlusIconWithBorder(canvas, centerX, centerY, color);

                // Save the clickable region for the plus icon
                barcode.setIconBounds(centerX, centerY, 50); // Save icon bounds for touch detection
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

        // Draw vertical bar of the plus icon
        canvas.drawRect(
                centerX - barThickness / 2,
                centerY - iconSize / 2,
                centerX + barThickness / 2,
                centerY + iconSize / 2,
                iconPaint
        );

        // Draw horizontal bar of the plus icon
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
        // Create a popup window for the menu
        PopupWindow popupWindow = new PopupWindow(context);
        popupWindow.setWidth(900);
        popupWindow.setHeight(1200);
        popupWindow.setFocusable(true);

        // Inflate the custom layout for the menu
        View menuView = View.inflate(context, R.layout.barcode_menu, null);

        // Set up the image view and text view in the menu
        ImageView barcodeImageView = menuView.findViewById(R.id.barcodeImageView);
        TextView barcodeDetailsTextView = menuView.findViewById(R.id.barcodeDetailsTextView);

        // Display the barcode image and details
        barcodeImageView.setImageBitmap(currentFrameBitmap);
        barcodeDetailsTextView.setText("Barcode Value: " + barcode.getValue());

        popupWindow.setContentView(menuView);

        // Show the menu at the center of the screen
        popupWindow.showAtLocation(this, 0, getWidth() / 2, getHeight() / 2);
    }
}
