package com.world.cloudxsolution;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Live visualizer for a single analog stick: draws the travel boundary, the current deadzone
 * radius (dashed ring), the raw (unprocessed) stick position as a dim dot, and the
 * fully-processed position (deadzone + response curve + sensitivity applied) as a bright dot.
 *
 * Feed it live data from AndroidGamepadListener#getRawStickState and
 * AndroidGamepadListener#computeStickResponse. This view does no polling itself — the host
 * (e.g. the settings dialog) is responsible for calling setPositions()/setDeadzone() on a timer.
 *
 * X/Y are expected in standard stick range [-1, 1], Y following Android's convention where
 * negative is "up" / away from the player.
 */
public class StickTestView extends View {

    private final Paint boundaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deadzonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rawDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint processedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint processedDotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float rawX = 0f, rawY = 0f;
    private float processedX = 0f, processedY = 0f;
    private float deadzone = 0.12f;

    public StickTestView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(dp(2f));
        boundaryPaint.setColor(Color.parseColor("#4A4A4A"));

        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(dp(1f));
        crosshairPaint.setColor(Color.parseColor("#2E2E2E"));

        deadzonePaint.setStyle(Paint.Style.STROKE);
        deadzonePaint.setStrokeWidth(dp(1.5f));
        deadzonePaint.setColor(Color.parseColor("#E0483C"));
        deadzonePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(4f)}, 0f));

        rawDotPaint.setStyle(Paint.Style.FILL);
        rawDotPaint.setColor(Color.parseColor("#8A8A8A"));
        rawDotPaint.setAlpha(160);

        processedDotPaint.setStyle(Paint.Style.FILL);
        processedDotPaint.setColor(Color.parseColor("#8BC53F"));

        processedDotStrokePaint.setStyle(Paint.Style.STROKE);
        processedDotStrokePaint.setStrokeWidth(dp(1.5f));
        processedDotStrokePaint.setColor(Color.parseColor("#122611"));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** Updates the dashed deadzone ring radius. Call this as the deadzone slider is dragged. */
    public void setDeadzone(float deadzone) {
        float clamped = clampUnit(deadzone);
        if (this.deadzone != clamped) {
            this.deadzone = clamped;
            invalidate();
        }
    }

    /** Updates both dots. rawX/Y and processedX/Y are each expected in [-1, 1]. */
    public void setPositions(float rawX, float rawY, float processedX, float processedY) {
        this.rawX = rawX;
        this.rawY = rawY;
        this.processedX = processedX;
        this.processedY = processedY;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size <= 0) {
            size = (int) dp(120f);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dp(8f);
        if (radius <= 0f) return;

        canvas.drawCircle(cx, cy, radius, boundaryPaint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crosshairPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crosshairPaint);
        canvas.drawCircle(cx, cy, radius * deadzone, deadzonePaint);

        // Raw position: dim dot, shows exactly what the OS is reporting before any processing.
        float rawPx = cx + clampUnit1(rawX) * radius;
        float rawPy = cy + clampUnit1(rawY) * radius;
        canvas.drawCircle(rawPx, rawPy, dp(4.5f), rawDotPaint);

        // Processed position: bright dot with outline, shows what the game actually receives.
        float procPx = cx + clampUnit1(processedX) * radius;
        float procPy = cy + clampUnit1(processedY) * radius;
        canvas.drawCircle(procPx, procPy, dp(6.5f), processedDotPaint);
        canvas.drawCircle(procPx, procPy, dp(6.5f), processedDotStrokePaint);
    }

    private static float clampUnit(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float clampUnit1(float v) {
        if (v < -1f) return -1f;
        if (v > 1f) return 1f;
        return v;
    }
}