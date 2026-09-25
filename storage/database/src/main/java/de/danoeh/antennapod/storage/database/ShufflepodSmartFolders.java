package de.danoeh.antennapod.storage.database;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.ReleasePattern;

/**
 * SHUFFLEPOD: the smart folders on the Podcasts page, computed from each subscribed show's release dates.
 * Results are cached for an hour. Must be called off the main thread.
 */
public final class ShufflepodSmartFolders {
    private static final long MAX_AGE_MS = 60 * 60 * 1000;

    private static volatile Snapshot snapshot = null;

    private ShufflepodSmartFolders() {
    }

    /**
     * The subscribed shows matching the pattern ({@link ReleasePattern#DORMANT} or
     * {@link ReleasePattern#SEASONAL}), sorted by title.
     */
    public static List<Feed> getFeeds(int pattern) {
        Map<Long, Integer> current = getPatterns();
        List<Feed> result = new ArrayList<>();
        for (Feed feed : DBReader.getFeedList()) {
            Integer flags = current.get(feed.getId());
            if (feed.getState() == Feed.STATE_SUBSCRIBED && flags != null && (flags & pattern) != 0) {
                result.add(feed);
            }
        }
        Collections.sort(result, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.getTitle() != null ? a.getTitle() : "", b.getTitle() != null ? b.getTitle() : ""));
        return result;
    }

    public static int count(int pattern) {
        int count = 0;
        for (int flags : getPatterns().values()) {
            if ((flags & pattern) != 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Publish time of the show's newest episode, or 0 if unknown.
     */
    public static long getLastRelease(long feedId) {
        Snapshot current = snapshot;
        Long value = current != null ? current.lastReleases.get(feedId) : null;
        return value != null ? value : 0;
    }

    private static Map<Long, Integer> getPatterns() {
        Snapshot current = snapshot;
        if (current != null && System.currentTimeMillis() - current.computedAt < MAX_AGE_MS) {
            return current.patterns;
        }
        Map<Long, Integer> newPatterns = new HashMap<>();
        Map<Long, Long> newLastReleases = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            long[] dates = releaseDates(feed.getId());
            newPatterns.put(feed.getId(), ReleasePattern.classify(dates, now));
            if (dates.length > 0) {
                newLastReleases.put(feed.getId(), dates[dates.length - 1]);
            }
        }
        snapshot = new Snapshot(newPatterns, newLastReleases, now);
        return newPatterns;
    }

    private static final class Snapshot {
        final Map<Long, Integer> patterns;
        final Map<Long, Long> lastReleases;
        final long computedAt;

        Snapshot(Map<Long, Integer> patterns, Map<Long, Long> lastReleases, long computedAt) {
            this.patterns = patterns;
            this.lastReleases = lastReleases;
            this.computedAt = computedAt;
        }
    }

    /**
     * One small indexed query per show, so other screens can use the database in between.
     */
    private static long[] releaseDates(long feedId) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.shufflepodQuery("SELECT " + PodDBAdapter.KEY_PUBDATE + " FROM "
                + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " WHERE " + PodDBAdapter.KEY_FEED + " = ? AND "
                + PodDBAdapter.KEY_PUBDATE + " > 0 ORDER BY " + PodDBAdapter.KEY_PUBDATE,
                new String[] {String.valueOf(feedId)})) {
            long[] dates = new long[cursor.getCount()];
            int i = 0;
            while (cursor.moveToNext()) {
                dates[i++] = cursor.getLong(0);
            }
            return dates;
        } finally {
            adapter.close();
        }
    }
}
