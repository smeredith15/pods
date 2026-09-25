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
import de.danoeh.antennapod.shufflepod.ForcedEpisodes;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.ShowTags;

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
     * What should play after {@code current} ends: the episode at the top of the queue (so sorting or
     * reordering the queue decides what's next; News always comes first), otherwise the first forced
     * Entertainment episode, otherwise a newly shuffled Entertainment episode. Entertainment episodes are
     * added to the end of the queue.
     * Must be called off the main thread.
     */
    @Nullable
    public static FeedItem nextAfter(Context context, @Nullable FeedItem current) {
        long currentId = current != null ? current.getId() : -1;
        for (FeedItem queued : DBReader.getQueue()) {
            if (queued.getId() != currentId && queued.hasMedia()) {
                return queued;
            }
        }
        FeedItem picked = takeNextForced(currentId);
        if (picked == null) {
            picked = pick(currentId);
        }
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
        for (long feedId : ShufflepodShowTags.getFeedIds(ShowTags.ENTERTAINMENT)) {
            FeedItem oldest = oldestEligible(feedId, excludeItemId);
            if (oldest != null) {
                candidates.add(oldest);
            }
        }
        for (Person person : People.getPooledPeople()) {
            FeedItem oldest = ShufflepodPeople.oldestEligible(person.getId(), excludeItemId);
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

    /**
     * The forced episodes still waiting to play, in order. Ones that were played, archived, deleted or
     * queued in the meantime are dropped from the list.
     */
    public static List<FeedItem> forcedEpisodes() {
        List<FeedItem> result = new ArrayList<>();
        for (long itemId : ForcedEpisodes.getItemIds()) {
            FeedItem item = DBReader.getFeedItem(itemId);
            if (isForcedStillValid(item)) {
                result.add(item);
            } else {
                ForcedEpisodes.remove(itemId);
            }
        }
        return result;
    }

    /**
     * Forces the next {@code count} oldest eligible episodes of a show to play after the queue, ahead
     * of the shuffle. Episodes already forced, and {@code excludeItemId} (e.g. the one playing now),
     * are skipped, so repeating this continues further into the show.
     *
     * @return the episodes that were added
     */
    public static List<FeedItem> forceNext(long feedId, int count, long excludeItemId) {
        List<FeedItem> added = new ArrayList<>();
        Feed feed = DBReader.getFeed(feedId, false, 0, 0);
        if (feed == null || count <= 0) {
            return added;
        }
        int limit = count + ForcedEpisodes.getItemIds().size() + 1;
        for (FeedItem item : DBReader.getFeedItemList(feed, eligibleFilter(), SortOrder.DATE_OLD_NEW, 0, limit)) {
            if (added.size() >= count) {
                break;
            }
            if (item.getId() != excludeItemId && !ForcedEpisodes.contains(item.getId())) {
                added.add(item);
            }
        }
        ForcedEpisodes.add(added);
        return added;
    }

    @Nullable
    private static FeedItem takeNextForced(long currentId) {
        for (long itemId : ForcedEpisodes.getItemIds()) {
            ForcedEpisodes.remove(itemId);
            FeedItem item = DBReader.getFeedItem(itemId);
            if (itemId != currentId && isForcedStillValid(item)) {
                return item;
            }
        }
        return null;
    }

    private static boolean isForcedStillValid(@Nullable FeedItem item) {
        return item != null && item.hasMedia() && !item.isPlayed() && !ArchiveStore.isArchived(item)
                && !item.isTagged(FeedItem.TAG_QUEUE);
    }
}
