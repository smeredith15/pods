package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class WeeklyReleasesTest {
    private static final long WEEK = WeeklyReleases.WEEK;
    private static final long FROM = 1000 * WEEK;

    @Test
    public void weekIndex() {
        assertEquals(-1, WeeklyReleases.weekIndex(FROM - 1, FROM, 52));
        assertEquals(0, WeeklyReleases.weekIndex(FROM, FROM, 52));
        assertEquals(0, WeeklyReleases.weekIndex(FROM + WEEK - 1, FROM, 52));
        assertEquals(1, WeeklyReleases.weekIndex(FROM + WEEK, FROM, 52));
        assertEquals(51, WeeklyReleases.weekIndex(FROM + 52 * WEEK - 1, FROM, 52));
        assertEquals(-1, WeeklyReleases.weekIndex(FROM + 52 * WEEK, FROM, 52));
    }

    @Test
    public void sumAddsSeriesWeekByWeek() {
        long[] total = WeeklyReleases.sum(Arrays.asList(new long[] {1, 2, 0}, new long[] {10, 0, 5}), 3);
        assertArrayEquals(new long[] {11, 2, 5}, total);
        assertArrayEquals(new long[] {0, 0, 0}, WeeklyReleases.sum(Arrays.asList(), 3));
    }

    @Test
    public void weeksAboveAndActive() {
        long[] values = {0, 5, 10, 0, 20};
        assertEquals(2, WeeklyReleases.weeksAbove(values, 5));
        assertEquals(3, WeeklyReleases.activeWeeks(values));
    }

    @Test
    public void peak() {
        assertEquals(4, WeeklyReleases.peak(new long[] {0, 5, 10, 0, 20}));
        assertEquals(1, WeeklyReleases.peak(new long[] {0, 7, 7}));
        assertEquals(-1, WeeklyReleases.peak(new long[] {0, 0}));
    }
}
