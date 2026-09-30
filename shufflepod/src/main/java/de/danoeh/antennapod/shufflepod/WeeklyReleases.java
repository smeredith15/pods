package de.danoeh.antennapod.shufflepod;

import java.util.List;

/**
 * Audio released per week over a period, for the seasonality chart.
 */
public final class WeeklyReleases {
    public static final long WEEK = 7 * ReleasePattern.DAY;

    private WeeklyReleases() {
    }

    /**
     * The week a time falls in, counting from {@code from}, or -1 if it is outside the {@code weeks} weeks.
     */
    public static int weekIndex(long time, long from, int weeks) {
        if (time < from) {
            return -1;
        }
        long index = (time - from) / WEEK;
        return index < weeks ? (int) index : -1;
    }

    public static long[] sum(List<long[]> series, int weeks) {
        long[] total = new long[weeks];
        for (long[] values : series) {
            for (int i = 0; i < weeks && i < values.length; i++) {
                total[i] += values[i];
            }
        }
        return total;
    }

    public static int weeksAbove(long[] values, long threshold) {
        int count = 0;
        for (long value : values) {
            if (value > threshold) {
                count++;
            }
        }
        return count;
    }

    public static int activeWeeks(long[] values) {
        return weeksAbove(values, 0);
    }

    /**
     * Index of the largest value, or -1 if all are 0.
     */
    public static int peak(long[] values) {
        int peak = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0 && (peak < 0 || values[i] > values[peak])) {
                peak = i;
            }
        }
        return peak;
    }
}
