package com.leosprojects.busdisplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class LedMatrixView extends View {
    private static final int ROWS = 11;
    private static final int COLS = 44;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint offPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint onPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean[][] matrix = new boolean[ROWS][COLS];

    public LedMatrixView(Context context) {
        super(context);
        init();
    }

    public LedMatrixView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint.setColor(Color.rgb(5, 5, 5));
        offPaint.setColor(Color.rgb(35, 28, 20));
        onPaint.setColor(Color.rgb(255, 145, 20));
        gridPaint.setColor(Color.rgb(45, 45, 45));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setMatrix(boolean[][] matrix) {
        if (matrix == null || matrix.length != ROWS || matrix[0].length != COLS) {
            throw new IllegalArgumentException("Matrix must be 11x44");
        }
        this.matrix = matrix;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = 800;
        int desiredHeight = Math.max(180, Math.round(width * 0.30f));
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float pad = 12f;
        RectF bg = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawRoundRect(bg, 18f, 18f, backgroundPaint);

        float usableW = bg.width() - 20f;
        float usableH = bg.height() - 20f;
        float cellW = usableW / COLS;
        float cellH = usableH / ROWS;
        float cell = Math.min(cellW, cellH);
        float gridW = cell * COLS;
        float gridH = cell * ROWS;
        float startX = bg.centerX() - gridW / 2f;
        float startY = bg.centerY() - gridH / 2f;
        float radius = Math.max(1.4f, cell * 0.25f);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                float left = startX + col * cell;
                float top = startY + row * cell;
                canvas.drawRect(left, top, left + cell, top + cell, gridPaint);

                float cx = left + cell / 2f;
                float cy = top + cell / 2f;
                if (matrix[row][col]) {
                    onPaint.setShadowLayer(radius * 1.4f, 0, 0,
                            Color.rgb(255, 105, 0));
                    canvas.drawCircle(cx, cy, radius, onPaint);
                    onPaint.clearShadowLayer();
                } else {
                    canvas.drawCircle(cx, cy, radius * 0.55f, offPaint);
                }
            }
        }
    }
}
