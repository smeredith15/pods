package de.danoeh.antennapod.ui.statistics.listening;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.DateFormatSymbols;
import java.util.Calendar;

import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.statistics.R;

/**
 * SHUFFLEPOD: a simple line chart of a value over time, used for the News queue length and the seasons chart.
 */
public class ShufflepodLineChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint referencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private long[] times = new long[0];
    private long[] values = new long[0];
    private long referenceValue = -1;
    private boolean monthLabels = false;

    public ShufflepodLineChartView(Context context) {
        this(context, null);
    }

    public ShufflepodLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        linePaint.setColor(ThemeUtils.getColorFromAttr(context, R.attr.colorAccent));
        final int secondary = ThemeUtils.getColorFromAttr(context, android.R.attr.textColorSecondary);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        gridPaint.setColor(secondary);
        gridPaint.setAlpha(80);
        gridPaint.setStrokeWidth(density);
        textPaint.setColor(secondary);
        textPaint.setTextSize(12 * density);
        referencePaint.setColor(secondary);
        referencePaint.setStyle(Paint.Style.STROKE);
        referencePaint.setStrokeWidth(1.5f * density);
        referencePaint.setPathEffect(new DashPathEffect(new float[] {6 * density, 4 * density}, 0));
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

    /**
     * A dashed horizontal line at this value, or -1 for none.
     */
    public void setReferenceValue(long referenceValue) {
        this.referenceValue = referenceValue;
        invalidate();
    }

    /**
     * Label the start of each month along the bottom, instead of only the first and last date.
     */
    public void setMonthLabels(boolean monthLabels) {
        this.monthLabels = monthLabels;
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
        long maxValue = Math.max(60 * 60 * 1000, referenceValue);
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
        if (referenceValue >= 0) {
            float y = bottom - (bottom - top) * referenceValue / (float) maxValue;
            path.reset();
            path.moveTo(left, y);
            path.lineTo(right, y);
            canvas.drawPath(path, referencePaint);
        }
        canvas.drawText(Converter.getDurationStringLocalized(getResources(), maxValue, false),
                left, textHeight, textPaint);
        if (monthLabels) {
            drawMonthLabels(canvas, left, right, top, bottom, minTime, maxTime);
            return;
        }
        String start = DateUtils.formatDateTime(getContext(), minTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        canvas.drawText(start, left, getHeight() - 2 * density, textPaint);
        String end = DateUtils.formatDateTime(getContext(), maxTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        canvas.drawText(end, right - textPaint.measureText(end), getHeight() - 2 * density, textPaint);
    }

    private void drawMonthLabels(Canvas canvas, float left, float right, float top, float bottom,
                                 long minTime, long maxTime) {
        String[] months = DateFormatSymbols.getInstance().getShortMonths();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(minTime);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.MONTH, 1);
        while (calendar.getTimeInMillis() < maxTime) {
            float x = left + (right - left) * (calendar.getTimeInMillis() - minTime) / (float) (maxTime - minTime);
            canvas.drawLine(x, top, x, bottom, gridPaint);
            String label = months[calendar.get(Calendar.MONTH)];
            if (x + textPaint.measureText(label) <= right) {
                canvas.drawText(label, x + 2 * density, getHeight() - 2 * density, textPaint);
            }
            calendar.add(Calendar.MONTH, 1);
        }
    }
}
