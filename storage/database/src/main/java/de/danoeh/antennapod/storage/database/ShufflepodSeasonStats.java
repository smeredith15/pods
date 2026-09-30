package de.danoeh.antennapod.storage.database;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.shufflepod.WeeklyReleases;

/**
 * SHUFFLEPOD: audio released per week over the last year, per show, for the seasonality chart.
 * Includes subscribed shows and shows only previewed (not subscribed), which are kept in the database.
 */
public final class ShufflepodSeasonStats {
    public static final int WEEKS = 52;

    private ShufflepodSeasonStats() {
    }

    public static class Show {
        private final Feed feed;
        private final float speed;
        private final boolean news;
        private final long[] weeklyAdjustedMs = new long[WEEKS];
        private long firstRelease;

        Show(Feed feed, float speed, boolean news) {
            this.feed = feed;
            this.speed = speed;
            this.news = news;
        }

        public Feed getFeed() {
            return feed;
        }

        public float getSpeed() {
            return speed;
        }

        public boolean isNews() {
            return news;
        }

        public boolean isSubscribed() {
            return feed.getState() == Feed.STATE_SUBSCRIBED;
        }

        public long[] getWeeklyAdjustedMs() {
            return weeklyAdjustedMs.clone();
        }

        /**
         * Publish time of the oldest episode in the database. Many feeds only list their recent episodes.
         */
        public long getFirstRelease() {
            return firstRelease;
        }

        public long getTotalAdjustedMs() {
            long sum = 0;
            for (long value : weeklyAdjustedMs) {
                sum += value;
            }
            return sum;
        }
    }

    public static class Result {
        private final long from;
        private final List<Show> shows = new ArrayList<>();

        Result(long from) {
            this.from = from;
        }

        /**
         * Start of the first week.
         */
        public long getFrom() {
            return from;
        }

        public long getWeekStart(int week) {
            return from + week * WeeklyReleases.WEEK;
        }

        /**
         * News shows first, then the others, each by audio released.
         */
        public List<Show> getShows() {
            return Collections.unmodifiableList(shows);
        }
    }

    /**
     * The last {@link #WEEKS} whole weeks ending at the start of today. Must be called off the main thread.
     */
    public static Result compute() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long to = calendar.getTimeInMillis();
        Result result = new Result(to - WEEKS * WeeklyReleases.WEEK);
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED && feed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                continue;
            }
            boolean news = feed.getState() == Feed.STATE_SUBSCRIBED
                    && ShowTags.has(feed.getPreferences(), ShowTags.NEWS);
            Show show = new Show(feed, ShufflepodReleaseStats.effectiveSpeed(feed), news);
            if (load(show, result.from, to)) {
                result.shows.add(show);
            }
        }
        Collections.sort(result.shows, (a, b) -> a.news != b.news ? (a.news ? -1 : 1)
                : Long.compare(b.getTotalAdjustedMs(), a.getTotalAdjustedMs()));
        return result;
    }

    /**
     * One small indexed query per show, so other screens can use the database in between.
     *
     * @return whether the show released anything with a known length in the period
     */
    private static boolean load(Show show, long from, long to) {
        String items = PodDBAdapter.TABLE_NAME_FEED_ITEMS;
        String media = PodDBAdapter.TABLE_NAME_FEED_MEDIA;
        String feedId = String.valueOf(show.feed.getId());
        boolean any = false;
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            try (Cursor cursor = adapter.shufflepodQuery("SELECT " + items + "." + PodDBAdapter.KEY_PUBDATE + ", "
                    + media + "." + PodDBAdapter.KEY_DURATION + " FROM " + items + " INNER JOIN " + media
                    + " ON " + media + "." + PodDBAdapter.KEY_FEEDITEM + " = " + items + "." + PodDBAdapter.KEY_ID
                    + " WHERE " + items + "." + PodDBAdapter.KEY_FEED + " = ?"
                    + " AND " + items + "." + PodDBAdapter.KEY_PUBDATE + " >= ?"
                    + " AND " + items + "." + PodDBAdapter.KEY_PUBDATE + " < ?",
                    new String[] {feedId, String.valueOf(from), String.valueOf(to)})) {
                while (cursor.moveToNext()) {
                    int week = WeeklyReleases.weekIndex(cursor.getLong(0), from, WEEKS);
                    long duration = cursor.getLong(1);
                    if (week >= 0 && duration > 0) {
                        show.weeklyAdjustedMs[week] += (long) (duration / show.speed);
                        any = true;
                    }
                }
            }
            if (any) {
                try (Cursor cursor = adapter.shufflepodQuery("SELECT MIN(" + PodDBAdapter.KEY_PUBDATE + ") FROM "
                        + items + " WHERE " + PodDBAdapter.KEY_FEED + " = ? AND " + PodDBAdapter.KEY_PUBDATE + " > 0",
                        new String[] {feedId})) {
                    show.firstRelease = cursor.moveToFirst() ? cursor.getLong(0) : 0;
                }
            }
        } finally {
            adapter.close();
        }
        return any;
    }
}
