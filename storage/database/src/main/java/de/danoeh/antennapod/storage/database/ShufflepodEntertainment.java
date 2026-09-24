package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.shufflepod.ArchiveStore;
import de.danoeh.antennapod.shufflepod.EntertainmentPicker;
import de.danoeh.antennapod.shufflepod.EntertainmentPool;

/**
 * SHUFFLEPOD: the Entertainment pipeline. When the News queue runs out, the next episode is the
 * oldest eligible (unplayed, not archived) episode of a random show from the Entertainment pool.
 */
public final class ShufflepodEntertainment {
    private static final String TAG = "ShufflepodEntertainment";
    private static final Random random = new Random();

    private ShufflepodEntertainment() {
    }

    /**
     * What should play after {@code current} ends: the next queued episode, otherwise any other queued
     * episode (News always comes first), otherwise a newly chosen Entertainment episode, which is added
     * to the end of the queue. Must be called off the main thread.
     */
    @Nullable
    public static FeedItem nextAfter(Context context, @Nullable FeedItem current) {
        long currentId = current != null ? current.getId() : -1;
        if (current != null) {
            FeedItem next = DBReader.getNextInQueue(current);
            if (next != null) {
                return next;
            }
        }
        for (FeedItem queued : DBReader.getQueue()) {
            if (queued.getId() != currentId && queued.hasMedia()) {
                return queued;
            }
        }
        FeedItem picked = pick(currentId);
        if (picked == null) {
            return null;
        }
        try {
            DBWriter.addQueueItemAt(context, picked.getId(), DBReader.getQueue().size()).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Could not add Entertainment episode to queue", e);
        }
        return DBReader.getFeedItem(picked.getId());
    }

    /**
     * Chooses the next Entertainment episode without changing anything.
     */
    @Nullable
    public static FeedItem pick(long excludeItemId) {
        List<FeedItem> candidates = new ArrayList<>();
        for (long feedId : EntertainmentPool.getFeedIds()) {
            FeedItem oldest = oldestEligible(feedId, excludeItemId);
            if (oldest != null) {
                candidates.add(oldest);
            }
        }
        return EntertainmentPicker.pick(candidates, random);
    }

    /**
     * The oldest episode of a show that is unplayed (partly played counts), not archived, has media and
     * is not already queued.
     */
    @Nullable
    public static FeedItem oldestEligible(long feedId, long excludeItemId) {
        Feed feed = DBReader.getFeed(feedId, false, 0, 0);
        if (feed == null || feed.getState() != Feed.STATE_SUBSCRIBED) {
            return null;
        }
        List<FeedItem> items = DBReader.getFeedItemList(feed, eligibleFilter(), SortOrder.DATE_OLD_NEW, 0, 2);
        for (FeedItem item : items) {
            if (item.getId() != excludeItemId) {
                return item;
            }
        }
        return null;
    }

    /**
     * Number of episodes of a show that the Entertainment pipeline could still play.
     */
    public static int eligibleCount(long feedId) {
        return DBReader.getFeedEpisodeCount(feedId, eligibleFilter());
    }

    private static FeedItemFilter eligibleFilter() {
        return new FeedItemFilter(FeedItemFilter.UNPLAYED, FeedItemFilter.HAS_MEDIA,
                FeedItemFilter.NOT_QUEUED, ArchiveStore.FILTER_HIDE_ARCHIVED);
    }
}
