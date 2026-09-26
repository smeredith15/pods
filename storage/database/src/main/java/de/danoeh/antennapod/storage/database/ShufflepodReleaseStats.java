package de.danoeh.antennapod.storage.database;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * SHUFFLEPOD: how much audio the News shows release, averaged per day of the week.
 * Playback-adjusted times use each show's current speed, or the current global speed for shows that follow it.
 */
public final class ShufflepodReleaseStats {

    private ShufflepodReleaseStats() {
    }

    public static class ShowTotal {
        private final Feed feed;
        private final float speed;
        private long weeklyMs;
        private long weeklyAdjustedMs;
        private long weeklyListenedMs;

        ShowTotal(Feed feed, float speed) {
            this.feed = feed;
            this.speed = speed;
        }

        public Feed getFeed() {
            return feed;
        }

        public float getSpeed() {
            return speed;
        }

        public long getWeeklyMs() {
            return weeklyMs;
        }

        public long getWeeklyAdjustedMs() {
            return weeklyAdjustedMs;
        }

        /**
         * Share of the released audio listened to, in percent.
         */
        public int getListenedPercent() {
            return weeklyMs > 0 ? (int) Math.round(100.0 * weeklyListenedMs / weeklyMs) : 0;
        }
    }

    public static class Result {
        private final int weeks;
        private final int showCount;
        private final long[] averageMs = new long[8];
        private final long[] averageAdjustedMs = new long[8];
        private final List<ShowTotal> shows = new ArrayList<>();
        private int episodesWithoutDuration;
        private int episodes;
        private int episodesPlayed;
        private long weeklyListenedMs;
        private long weeklyListenedAdjustedMs;

        Result(int weeks, int showCount) {
            this.weeks = weeks;
            this.showCount = showCount;
        }

        public int getWeeks() {
            return weeks;
        }

        public int getShowCount() {
            return showCount;
        }

        /**
         * @param day a {@link Calendar#DAY_OF_WEEK} value
         */
        public long getAverageMs(int day) {
            return averageMs[day];
        }

        public long getAverageAdjustedMs(int day) {
            return averageAdjustedMs[day];
        }

        public List<ShowTotal> getShows() {
            return Collections.unmodifiableList(shows);
        }

        public int getEpisodesWithoutDuration() {
            return episodesWithoutDuration;
        }

        public int getEpisodes() {
            return episodes;
        }

        public int getEpisodesPlayed() {
            return episodesPlayed;
        }

        /**
         * Audio of these episodes actually played, averaged per week. Each episode counts at most its length.
         */
        public long getWeeklyListenedMs() {
            return weeklyListenedMs;
        }

        public long getWeeklyListenedAdjustedMs() {
            return weeklyListenedAdjustedMs;
        }

        public int getListenedPercent() {
            long released = weeklyMs();
            return released > 0 ? (int) Math.round(100.0 * weeklyListenedMs / released) : 0;
        }

        public long weeklyMs() {
            long sum = 0;
            for (long value : averageMs) {
                sum += value;
            }
            return sum;
        }

        public long weeklyAdjustedMs() {
            long sum = 0;
            for (long value : averageAdjustedMs) {
                sum += value;
            }
            return sum;
        }
    }

    public static float effectiveSpeed(Feed feed) {
        FeedPreferences prefs = feed.getPreferences();
        float speed = prefs != null ? prefs.getFeedPlaybackSpeed() : FeedPreferences.SPEED_USE_GLOBAL;
        if (speed == FeedPreferences.SPEED_USE_GLOBAL || speed <= 0) {
            speed = UserPreferences.getPlaybackSpeed();
        }
        return speed > 0 ? speed : 1.0f;
    }

    /**
     * Averages over the given number of whole weeks ending at the start of today, so every weekday is counted
     * the same number of times. Must be called off the main thread.
     */
    public static Result compute(int weeks) {
        List<Feed> feeds = ShufflepodShowTags.getNewsFeeds();
        Result result = new Result(weeks, feeds.size());
        if (feeds.isEmpty()) {
            return result;
        }
        Map<Long, ShowTotal> byFeed = new HashMap<>();
        StringBuilder ids = new StringBuilder();
        for (Feed feed : feeds) {
            byFeed.put(feed.getId(), new ShowTotal(feed, effectiveSpeed(feed)));
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(feed.getId());
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long to = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -7 * weeks);
        long from = calendar.getTimeInMillis();

        String items = PodDBAdapter.TABLE_NAME_FEED_ITEMS;
        String media = PodDBAdapter.TABLE_NAME_FEED_MEDIA;
        String query = "SELECT " + items + "." + PodDBAdapter.KEY_FEED + ", " + items + "." + PodDBAdapter.KEY_PUBDATE
                + ", " + media + "." + PodDBAdapter.KEY_DURATION
                + ", " + media + "." + PodDBAdapter.KEY_PLAYED_DURATION + ", " + items + "." + PodDBAdapter.KEY_READ
                + " FROM " + items + " INNER JOIN " + media
                + " ON " + media + "." + PodDBAdapter.KEY_FEEDITEM + " = " + items + "." + PodDBAdapter.KEY_ID
                + " WHERE " + items + "." + PodDBAdapter.KEY_FEED + " IN (" + ids + ")"
                + " AND " + items + "." + PodDBAdapter.KEY_PUBDATE + " >= ?"
                + " AND " + items + "." + PodDBAdapter.KEY_PUBDATE + " < ?";

        double[] real = new double[8];
        double[] adjusted = new double[8];
        double listened = 0;
        double listenedAdjusted = 0;
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.shufflepodQuery(query,
                new String[] {String.valueOf(from), String.valueOf(to)})) {
            while (cursor.moveToNext()) {
                ShowTotal show = byFeed.get(cursor.getLong(0));
                if (show == null) {
                    continue;
                }
                long duration = cursor.getLong(2);
                result.episodes++;
                if (cursor.getInt(4) == FeedItem.PLAYED) {
                    result.episodesPlayed++;
                }
                if (duration <= 0) {
                    result.episodesWithoutDuration++;
                    continue;
                }
                calendar.setTimeInMillis(cursor.getLong(1));
                int day = calendar.get(Calendar.DAY_OF_WEEK);
                real[day] += duration;
                adjusted[day] += duration / show.speed;
                show.weeklyMs += duration;
                show.weeklyAdjustedMs += (long) (duration / show.speed);
                long played = Math.min(Math.max(cursor.getLong(3), 0), duration);
                listened += played;
                listenedAdjusted += played / show.speed;
                show.weeklyListenedMs += played;
            }
        } finally {
            adapter.close();
        }

        for (int day = 1; day < 8; day++) {
            result.averageMs[day] = Math.round(real[day] / weeks);
            result.averageAdjustedMs[day] = Math.round(adjusted[day] / weeks);
        }
        result.weeklyListenedMs = Math.round(listened / weeks);
        result.weeklyListenedAdjustedMs = Math.round(listenedAdjusted / weeks);
        for (ShowTotal show : byFeed.values()) {
            show.weeklyMs /= weeks;
            show.weeklyAdjustedMs /= weeks;
            show.weeklyListenedMs /= weeks;
            result.shows.add(show);
        }
        Collections.sort(result.shows, (a, b) -> Long.compare(b.weeklyMs, a.weeklyMs));
        return result;
    }
}
