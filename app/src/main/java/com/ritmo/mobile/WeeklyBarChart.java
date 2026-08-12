package com.ritmo.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class WeeklyBarChart extends View {
    private int[] values = new int[7];
    private String[] labels = new String[]{"","","","","","",""};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int barColor = 0xff0f5f4d;
    private int mutedColor = 0xff6b7b75;
    private int trackColor = 0xffedf3f0;

    public WeeklyBarChart(Context context) { super(context); }

    public void setData(int[] values, String[] labels) {
        this.values = values == null ? new int[7] : values;
        this.labels = labels == null ? this.labels : labels;
        invalidate();
    }

    public void setColors(int bar, int muted, int track) {
        barColor = bar; mutedColor = muted; trackColor = track; invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int pad = dp(8), labelH = dp(28);
        int chartH = Math.max(1, h - labelH - pad);
        int max = 1;
        for (int v : values) max = Math.max(max, v);
        float slot = (w - pad * 2f) / 7f;
        float barW = slot * 0.46f;

        for (int i = 0; i < 7; i++) {
            float cx = pad + slot * i + slot / 2f;
            float left = cx - barW / 2f;
            float right = cx + barW / 2f;
            float bottom = chartH;
            float topTrack = dp(8);
            paint.setColor(trackColor);
            canvas.drawRoundRect(new RectF(left, topTrack, right, bottom), barW / 2f, barW / 2f, paint);

            float ratio = values.length > i ? values[i] / (float) max : 0f;
            float top = bottom - Math.max(values.length > i && values[i] > 0 ? dp(8) : 0, ratio * (bottom - topTrack));
            paint.setColor(barColor);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), barW / 2f, barW / 2f, paint);

            paint.setColor(mutedColor);
            paint.setTextSize(dp(10));
            paint.setTextAlign(Paint.Align.CENTER);
            String label = labels.length > i ? labels[i] : "";
            canvas.drawText(label, cx, h - dp(7), paint);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
