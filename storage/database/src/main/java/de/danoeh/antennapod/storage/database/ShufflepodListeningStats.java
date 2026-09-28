package de.danoeh.antennapod.storage.database;

import android.database.Cursor;

import java.util.Calendar;

/**
 * SHUFFLEPOD: average listening time per day of the week. AntennaPod doesn't log listening sessions, so each
 * episode's played time counts on the day it was last played. Must be called off the main thread.
 */
public final class ShufflepodListeningStats {

    private ShufflepodListeningStats() {
    }

    /**
     * @return averages in milliseconds, indexed by {@link Calendar#DAY_OF_WEEK} (index 0 unused)
     */
    public static long[] averagePerWeekday(long from, long to) {
        long now = System.currentTimeMillis();
        long end = Math.min(to, now);
        long[] totals = new long[8];
        long first = Long.MAX_VALUE;
        Calendar calendar = Calendar.getInstance();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.shufflepodQuery("SELECT " + PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS + ", "
                + PodDBAdapter.KEY_PLAYED_DURATION + " FROM " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                + " WHERE " + PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS + " >= ? AND "
                + PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS + " < ? AND "
                + PodDBAdapter.KEY_PLAYED_DURATION + " > 0",
                new String[] {String.valueOf(Math.max(from, 1)), String.valueOf(end)})) {
            while (cursor.moveToNext()) {
                long time = cursor.getLong(0);
                first = Math.min(first, time);
                calendar.setTimeInMillis(time);
                totals[calendar.get(Calendar.DAY_OF_WEEK)] += cursor.getLong(1);
            }
        } finally {
            adapter.close();
        }
        long[] averages = new long[8];
        if (first == Long.MAX_VALUE) {
            return averages;
        }
        int[] occurrences = new int[8];
        calendar.setTimeInMillis(Math.max(from, first));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        while (calendar.getTimeInMillis() < end) {
            occurrences[calendar.get(Calendar.DAY_OF_WEEK)]++;
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        for (int day = 1; day < 8; day++) {
            averages[day] = occurrences[day] > 0 ? totals[day] / occurrences[day] : 0;
        }
        return averages;
    }
}
