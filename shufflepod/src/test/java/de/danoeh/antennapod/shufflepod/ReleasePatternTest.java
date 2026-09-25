package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReleasePatternTest {
    private static final long DAY = ReleasePattern.DAY;
    private static final long NOW = 2000 * DAY;

    private static long[] weekly(long firstDay, int episodes) {
        long[] dates = new long[episodes];
        for (int i = 0; i < episodes; i++) {
            dates[i] = (firstDay + 7L * i) * DAY;
        }
        return dates;
    }

    private static long[] seasons(long firstDay, int seasons, int episodesPerSeason, long breakDays) {
        List<Long> dates = new ArrayList<>();
        long day = firstDay;
        for (int s = 0; s < seasons; s++) {
            for (int e = 0; e < episodesPerSeason; e++) {
                dates.add(day * DAY);
                day += 7;
            }
            day += breakDays;
        }
        long[] result = new long[dates.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = dates.get(i);
        }
        return result;
    }

    @Test
    public void activeWeeklyShowIsNeither() {
        assertEquals(0, ReleasePattern.classify(weekly(2000 - 7 * 99, 100), NOW));
    }

    @Test
    public void stoppedWeeklyShowIsDormant() {
        assertEquals(ReleasePattern.DORMANT, ReleasePattern.classify(weekly(2000 - 200 - 7 * 99, 100), NOW));
    }

    @Test
    public void seasonalShowBetweenSeasonsIsOnlySeasonal() {
        long[] dates = seasons(1000, 3, 8, 150);
        long now = dates[dates.length - 1] + 120 * DAY;
        assertEquals(ReleasePattern.SEASONAL, ReleasePattern.classify(dates, now));
    }

    @Test
    public void seasonalShowThatEndedLongAgoIsBoth() {
        long[] dates = seasons(100, 3, 8, 150);
        long now = dates[dates.length - 1] + 700 * DAY;
        assertEquals(ReleasePattern.SEASONAL | ReleasePattern.DORMANT, ReleasePattern.classify(dates, now));
    }

    @Test
    public void singleGapInDailyShowIsNotSeasonal() {
        long[] first = weekly(1000, 2);
        long[] dates = {first[0], first[0] + 100 * DAY, NOW - DAY};
        assertEquals(0, ReleasePattern.classify(dates, NOW));
    }

    @Test
    public void emptyShowIsNeither() {
        assertEquals(0, ReleasePattern.classify(new long[0], NOW));
    }
}
