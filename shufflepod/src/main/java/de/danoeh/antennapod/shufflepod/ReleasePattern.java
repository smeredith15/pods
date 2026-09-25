package de.danoeh.antennapod.shufflepod;

import java.util.Arrays;

/**
 * Classifies a show by its release dates for the smart folders: dormant (nothing new for months) and
 * seasonal (runs of episodes separated by long breaks).
 */
public final class ReleasePattern {
    public static final int DORMANT = 1;
    public static final int SEASONAL = 2;
    /**
     * Not computed by {@link #classify}: the feed says the series is complete ({@link CompletedShows}).
     */
    public static final int FINISHED = 4;

    static final long DAY = 24L * 60 * 60 * 1000;
    static final long DORMANT_AFTER = 90 * DAY;
    static final long MIN_BREAK = 60 * DAY;
    static final int BREAK_FACTOR = 5;
    static final int MIN_EPISODES_SEASONAL = 6;
    static final int MIN_EPISODES_PER_SEASON = 3;

    private ReleasePattern() {
    }

    /**
     * A break is a gap of at least 60 days that is also at least five times the show's usual (median) gap.
     * Seasonal shows have at least two runs of three or more episodes separated by a break. Dormant shows
     * have had nothing new for 90 days, unless they are seasonal and the current gap is no longer than
     * one and a half times their longest earlier break (then they're probably just between seasons).
     *
     * @param dates publish times in milliseconds, sorted ascending
     * @return a combination of {@link #DORMANT} and {@link #SEASONAL}
     */
    public static int classify(long[] dates, long now) {
        int count = dates.length;
        if (count == 0) {
            return 0;
        }
        long sinceLast = now - dates[count - 1];
        if (count < 2) {
            return sinceLast >= DORMANT_AFTER ? DORMANT : 0;
        }
        long[] gaps = new long[count - 1];
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = dates[i + 1] - dates[i];
        }
        long[] sorted = gaps.clone();
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long breakThreshold = Math.max(MIN_BREAK, BREAK_FACTOR * median);

        int seasons = 0;
        int runLength = 1;
        int breaks = 0;
        long longestBreak = 0;
        for (long gap : gaps) {
            if (gap >= breakThreshold) {
                breaks++;
                longestBreak = Math.max(longestBreak, gap);
                if (runLength >= MIN_EPISODES_PER_SEASON) {
                    seasons++;
                }
                runLength = 1;
            } else {
                runLength++;
            }
        }
        if (runLength >= MIN_EPISODES_PER_SEASON) {
            seasons++;
        }

        boolean seasonal = count >= MIN_EPISODES_SEASONAL && breaks >= 1 && seasons >= 2;
        boolean betweenSeasons = seasonal && sinceLast <= longestBreak * 3 / 2;
        boolean dormant = sinceLast >= DORMANT_AFTER && !betweenSeasons;
        return (dormant ? DORMANT : 0) | (seasonal ? SEASONAL : 0);
    }
}
