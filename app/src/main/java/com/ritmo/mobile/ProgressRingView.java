package com.ritmo.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ProgressRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int value = 0;
    private int trackColor = 0x22FFFFFF;
    private int progressColor = 0xFF2DD4BF;
    private int textColor = 0xFFFFFFFF;
    private float strokeDp = 8f;
    private boolean showText = true;

    public ProgressRingView(Context context) { super(context); init(); }
    public ProgressRingView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        track.setStyle(Paint.Style.STROKE);
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    public void setValue(int value) { this.value = Math.max(0, Math.min(100, value)); invalidate(); }
    public int getValue() { return value; }
    public void setColors(int progressColor, int trackColor, int textColor) {
        this.progressColor = progressColor;
        this.trackColor = trackColor;
        this.textColor = textColor;
        invalidate();
    }
    public void setStrokeDp(float strokeDp) { this.strokeDp = strokeDp; invalidate(); }
    public void setShowText(boolean showText) { this.showText = showText; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float stroke = strokeDp * d;
        float pad = stroke / 2f + 2f * d;
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f + pad;
        float top = (getHeight() - size) / 2f + pad;
        oval.set(left, top, left + size - pad * 2f, top + size - pad * 2f);

        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        track.setColor(trackColor);
        progress.setColor(progressColor);
        canvas.drawArc(oval, -90f, 360f, false, track);
        canvas.drawArc(oval, -90f, value * 3.6f, false, progress);

        if (showText) {
            text.setColor(textColor);
            text.setTextSize(Math.max(14f * d, size * .22f));
            Paint.FontMetrics fm = text.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(value + "%", getWidth() / 2f, y, text);
        }
    }
}
