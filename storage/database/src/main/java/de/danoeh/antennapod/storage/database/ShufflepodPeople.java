package de.danoeh.antennapod.storage.database;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.shufflepod.ArchiveStore;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.PersonMatcher;

/**
 * SHUFFLEPOD: database side of following people. Finds a person's episodes in the subscribed shows,
 * stores episodes found elsewhere as a not-subscribed show, and provides their folder's contents.
 * All methods must be called off the main thread.
 */
public final class ShufflepodPeople {

    private ShufflepodPeople() {
    }

    /**
     * An episode found by an online search, not necessarily from a subscribed show.
     */
    public static final class RemoteEpisode {
        public String feedUrl;
        public String feedTitle;
        public String feedImage;
        public String guid;
        public String title;
        public String description;
        public String link;
        public String image;
        public String enclosureUrl;
        public String enclosureType;
        public long enclosureLength;
        public long publishedAtMillis;
        public int durationSeconds;
    }

    /**
     * Searches the subscribed shows for episodes that mention the person by any of their names.
     *
     * @return number of episodes newly added to their folder
     */
    public static int scanSubscriptions(Person person) {
        int added = 0;
        Set<Long> seen = new HashSet<>();
        List<String> names = person.getNames();
        for (String name : names) {
            for (FeedItem item : DBReader.searchFeedItems(0, name, FeedItemFilter.unfiltered())) {
                if (!seen.add(item.getId()) || item.getPubDate() == null
                        || !person.includesPublishDate(item.getPubDate().getTime())) {
                    continue;
                }
                if (PersonMatcher.matchesAny(names, item.getTitle(), item.getDescription())
                        && People.addEpisode(person.getId(), item, People.SOURCE_LOCAL)) {
                    added++;
                }
            }
        }
        return added;
    }

    /**
     * Adds an episode found online to the person's folder. If AntennaPod doesn't know the episode yet, it is
     * stored under its show, which is kept as a not-subscribed show if you don't subscribe to it.
     *
     * @return true if it was newly added to the folder
     */
    public static boolean addRemoteEpisode(Context context, Person person, RemoteEpisode remote) {
        if (remote.enclosureUrl == null || remote.enclosureUrl.isEmpty() || remote.feedUrl == null
                || remote.feedUrl.isEmpty() || !person.includesPublishDate(remote.publishedAtMillis)) {
            return false;
        }
        if (People.isShowMuted(person.getId(), remote.feedUrl)) {
            return false;
        }
        String guid = remote.guid != null && !remote.guid.isEmpty() ? remote.guid : null;
        FeedItem existing = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, remote.enclosureUrl);
        if (existing == null) {
            Feed feed = new Feed(remote.feedUrl, null, remote.feedTitle);
            feed.setState(Feed.STATE_NOT_SUBSCRIBED);
            feed.setImageUrl(remote.feedImage);
            feed.setLastRefreshAttempt(System.currentTimeMillis());
            FeedItem item = new FeedItem(0, remote.title, guid, remote.link,
                    new Date(remote.publishedAtMillis), FeedItem.UNPLAYED, feed);
            item.setDescriptionIfLonger(remote.description);
            item.setImageUrl(remote.image);
            FeedMedia media = new FeedMedia(item, remote.enclosureUrl, remote.enclosureLength,
                    remote.enclosureType);
            media.setDuration(remote.durationSeconds * 1000);
            item.setMedia(media);
            List<FeedItem> items = new ArrayList<>();
            items.add(item);
            feed.setItems(items);
            FeedDatabaseWriter.updateFeed(context, feed, false);
            existing = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, remote.enclosureUrl);
            if (existing == null) {
                return false;
            }
        }
        FeedItem full = DBReader.getFeedItem(existing.getId());
        return full != null && People.addEpisode(person.getId(), full, People.SOURCE_PODCAST_INDEX);
    }

    /**
     * The episodes in a person's folder, newest first. Episodes AntennaPod no longer has are dropped.
     */
    public static List<FeedItem> getEpisodes(long personId) {
        List<FeedItem> result = new ArrayList<>();
        for (long itemId : People.getItemIds(personId)) {
            FeedItem item = DBReader.getFeedItem(itemId);
            if (item == null) {
                People.forgetItem(personId, itemId);
            } else {
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> {
            long ta = a.getPubDate() != null ? a.getPubDate().getTime() : 0;
            long tb = b.getPubDate() != null ? b.getPubDate().getTime() : 0;
            return Long.compare(tb, ta);
        });
        return result;
    }

    /**
     * The oldest episode in a person's folder the Entertainment pipeline could play: unplayed, not archived,
     * with media and not queued.
     */
    @Nullable
    public static FeedItem oldestEligible(long personId, long excludeItemId) {
        FeedItem oldest = null;
        for (FeedItem item : getEpisodes(personId)) {
            if (item.getId() == excludeItemId || !isEligible(item)) {
                continue;
            }
            if (oldest == null || pubTime(item) < pubTime(oldest)) {
                oldest = item;
            }
        }
        return oldest;
    }

    public static int eligibleCount(long personId) {
        int count = 0;
        for (FeedItem item : getEpisodes(personId)) {
            if (isEligible(item)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isEligible(FeedItem item) {
        return item.hasMedia() && !item.isPlayed() && !ArchiveStore.isArchived(item)
                && !item.isTagged(FeedItem.TAG_QUEUE);
    }

    private static long pubTime(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : Long.MAX_VALUE;
    }
}
