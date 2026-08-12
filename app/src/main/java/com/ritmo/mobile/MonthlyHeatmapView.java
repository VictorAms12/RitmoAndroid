package com.ritmo.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class MonthlyHeatmapView extends View {
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] values = new int[30];
    private int brand = 0xFF6366F1;
    private int empty = 0xFFE2E8F0;
    private int muted = 0xFF64748B;

    public MonthlyHeatmapView(Context context) {
        super(context);
        label.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(int[] values) {
        this.values = values == null ? new int[30] : values.clone();
        invalidate();
    }

    public void setColors(int brand, int empty, int muted) {
        this.brand = brand; this.empty = empty; this.muted = muted; invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int cols = 7;
        int rows = 5;
        float gap = 6f * d;
        float available = getWidth() - gap * (cols - 1);
        float w = available / cols;
        float h = Math.min(w, (getHeight() - 22f * d - gap * (rows - 1)) / rows);
        float top = 2f * d;
        float radius = 6f * d;

        for (int i = 0; i < 30; i++) {
            int row = i / cols;
            int col = i % cols;
            float left = col * (w + gap);
            float y = top + row * (h + gap);
            int v = Math.max(0, Math.min(100, values[i]));
            int color;
            if (v <= 0) color = empty;
            else {
                int a = 55 + Math.round(v * 2f);
                if (a > 255) a = 255;
                color = (a << 24) | (brand & 0x00FFFFFF);
            }
            cell.setColor(color);
            canvas.drawRoundRect(new RectF(left, y, left + w, y + h), radius, radius, cell);
        }

        String[] names = {"S", "T", "Q", "Q", "S", "S", "D"};
        label.setColor(muted);
        label.setTextSize(9f * d);
        float labelY = top + rows * (h + gap) + 10f * d;
        for (int col = 0; col < cols; col++) {
            float x = col * (w + gap) + w / 2f;
            canvas.drawText(names[col], x, labelY, label);
        }
    }
}
