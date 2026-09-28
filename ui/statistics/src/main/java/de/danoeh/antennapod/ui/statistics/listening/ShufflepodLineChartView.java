package de.danoeh.antennapod.ui.statistics.listening;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import de.danoeh.antennapod.ui.common.Converter;

/**
 * SHUFFLEPOD: a simple line chart of a value over time, used for the News queue length.
 */
public class ShufflepodLineChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private long[] times = new long[0];
    private long[] values = new long[0];

    public ShufflepodLineChartView(Context context) {
        this(context, null);
    }

    public ShufflepodLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        int[] colorAttrs = {android.R.attr.colorAccent, android.R.attr.textColorSecondary};
        TypedArray colors = context.obtainStyledAttributes(colorAttrs);
        linePaint.setColor(colors.getColor(0, 0xff2196f3));
        final int secondary = colors.getColor(1, 0xff888888);
        colors.recycle();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        gridPaint.setColor(secondary);
        gridPaint.setAlpha(80);
        gridPaint.setStrokeWidth(density);
        textPaint.setColor(secondary);
        textPaint.setTextSize(12 * density);
    }

    /**
     * @param times  timestamps in milliseconds, ascending
     * @param values durations in milliseconds
     */
    public void setData(long[] times, long[] values) {
        this.times = times.clone();
        this.values = values.clone();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (160 * density);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (times.length < 2) {
            return;
        }
        float textHeight = textPaint.getTextSize();
        float left = 0;
        float right = getWidth();
        float top = textHeight + 4 * density;
        float bottom = getHeight() - textHeight - 6 * density;
        long minTime = times[0];
        long maxTime = Math.max(times[times.length - 1], minTime + 1);
        long maxValue = 60 * 60 * 1000;
        for (long value : values) {
            maxValue = Math.max(maxValue, value);
        }
        canvas.drawLine(left, bottom, right, bottom, gridPaint);
        canvas.drawLine(left, top, right, top, gridPaint);
        path.reset();
        for (int i = 0; i < times.length; i++) {
            float x = left + (right - left) * (times[i] - minTime) / (float) (maxTime - minTime);
            float y = bottom - (bottom - top) * values[i] / (float) maxValue;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, linePaint);
        canvas.drawText(Converter.getDurationStringLocalized(getResources(), maxValue, false),
                left, textHeight, textPaint);
        String start = DateUtils.formatDateTime(getContext(), minTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        canvas.drawText(start, left, getHeight() - 2 * density, textPaint);
        String end = DateUtils.formatDateTime(getContext(), maxTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        canvas.drawText(end, right - textPaint.measureText(end), getHeight() - 2 * density, textPaint);
    }
}
