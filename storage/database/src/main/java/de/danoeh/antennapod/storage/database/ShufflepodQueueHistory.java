package de.danoeh.antennapod.storage.database;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.shufflepod.QueueHistory;

/**
 * SHUFFLEPOD: records the queue's length (episodes and time left) for the News queue chart. Called after
 * feed refreshes (the News refresh runs every 30 minutes) and when an episode starts; at most one snapshot
 * per 10 minutes. Must be called off the main thread.
 */
public final class ShufflepodQueueHistory {
    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000;
    private static volatile long lastRecorded = 0;

    private ShufflepodQueueHistory() {
    }

    public static void record() {
        long now = System.currentTimeMillis();
        if (now - lastRecorded < MIN_INTERVAL_MS) {
            return;
        }
        lastRecorded = now;
        int count = 0;
        long durationMs = 0;
        for (FeedItem item : DBReader.getQueue()) {
            FeedMedia media = item.getMedia();
            if (media != null) {
                count++;
                durationMs += Math.max(0, media.getDuration() - media.getPosition());
            }
        }
        QueueHistory.record(now, count, durationMs);
    }

    /**
     * Snapshots since the given time, oldest first, as {time, episodes, milliseconds left}.
     */
    public static List<long[]> load(long since) {
        List<long[]> result = new ArrayList<>();
        for (QueueHistory.Point point : QueueHistory.load(since)) {
            result.add(new long[] {point.time, point.count, point.durationMs});
        }
        return result;
    }
}
