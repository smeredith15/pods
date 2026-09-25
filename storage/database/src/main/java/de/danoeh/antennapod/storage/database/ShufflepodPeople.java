package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.shufflepod.ArchiveStore;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.PersonMatcher;
import de.danoeh.antennapod.storage.database.mapper.FeedItemCursor;

/**
 * SHUFFLEPOD: database side of following people. Finds a person's episodes in the subscribed shows,
 * stores episodes found elsewhere as a not-subscribed show, and provides their folder's contents.
 * All methods must be called off the main thread.
 */
public final class ShufflepodPeople {

    private static final int ID_CHUNK = 500;
    private static final long SCAN_CHUNK = 10000;

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
     * Searches every episode of the subscribed shows for mentions of the person by any of their names.
     *
     * @return number of episodes newly added to their folder
     */
    public static int scanSubscriptions(Person person) {
        List<Person> people = new ArrayList<>();
        people.add(person);
        return scan(people, 0, maxItemId());
    }

    /**
     * Searches only the episodes added since the last call for mentions of anyone followed. The first call
     * just records where to start from.
     *
     * @return number of episodes newly added to folders
     */
    public static int scanNewEpisodes() {
        long max = maxItemId();
        long mark = People.getLocalScanMark();
        int added = 0;
        if (mark > 0 && max > mark) {
            added = scan(People.getPeople(), mark, max);
        }
        if (max != mark) {
            People.setLocalScanMark(max);
        }
        return added;
    }

    /**
     * Searches the episodes with IDs in (afterId, upToId] in small chunks, without holding DBReader's lock,
     * so the rest of the app can keep reading the database in between.
     */
    private static int scan(List<Person> people, long afterId, long upToId) {
        Set<String> names = new LinkedHashSet<>();
        for (Person person : people) {
            names.addAll(person.getNames());
        }
        if (names.isEmpty() || upToId <= afterId) {
            return 0;
        }
        Map<Long, Feed> subscribed = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                subscribed.put(feed.getId(), feed);
            }
        }
        String items = PodDBAdapter.TABLE_NAME_FEED_ITEMS + ".";
        StringBuilder where = new StringBuilder(items + PodDBAdapter.KEY_ID + " > ? AND "
                + items + PodDBAdapter.KEY_ID + " <= ? AND (");
        List<String> patterns = new ArrayList<>();
        for (String name : names) {
            if (!patterns.isEmpty()) {
                where.append(" OR ");
            }
            where.append(items).append(PodDBAdapter.KEY_TITLE).append(" LIKE ? OR ")
                    .append(items).append(PodDBAdapter.KEY_DESCRIPTION).append(" LIKE ?");
            patterns.add("%" + name + "%");
            patterns.add("%" + name + "%");
        }
        where.append(")");
        String[] args = new String[patterns.size() + 2];
        for (int i = 0; i < patterns.size(); i++) {
            args[i + 2] = patterns.get(i);
        }

        int added = 0;
        for (long low = afterId; low < upToId; low += SCAN_CHUNK) {
            args[0] = String.valueOf(low);
            args[1] = String.valueOf(Math.min(upToId, low + SCAN_CHUNK));
            List<FeedItem> found = new ArrayList<>();
            for (FeedItem item : queryWithDescriptions(where.toString(), args)) {
                Feed feed = subscribed.get(item.getFeedId());
                if (feed != null) {
                    item.setFeed(feed);
                    found.add(item);
                }
            }
            for (FeedItem item : found) {
                for (Person person : people) {
                    if (item.getPubDate() != null && person.includesPublishDate(item.getPubDate().getTime())
                            && PersonMatcher.matchesAny(person.getNames(), item.getTitle(), item.getDescription())
                            && People.addEpisode(person.getId(), item, People.SOURCE_LOCAL)) {
                        added++;
                    }
                }
            }
        }
        return added;
    }

    private static List<FeedItem> queryWithDescriptions(String where, String[] args) {
        List<FeedItem> result = new ArrayList<>();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (FeedItemCursor cursor = new FeedItemCursor(adapter.shufflepodItemsWithDescription(where, args))) {
            int descriptionIndex = cursor.getColumnIndex(PodDBAdapter.KEY_DESCRIPTION);
            while (cursor.moveToNext()) {
                FeedItem item = cursor.getFeedItem();
                if (descriptionIndex >= 0) {
                    item.setDescriptionIfLonger(cursor.getString(descriptionIndex));
                }
                result.add(item);
            }
        } finally {
            adapter.close();
        }
        return result;
    }

    private static long maxItemId() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try (Cursor cursor = adapter.shufflepodQuery("SELECT MAX(" + PodDBAdapter.KEY_ID + ") FROM "
                + PodDBAdapter.TABLE_NAME_FEED_ITEMS, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            adapter.close();
        }
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
        if (!PersonMatcher.matchesAny(person.getNames(), remote.title, remote.description, remote.feedTitle)) {
            return false; // Podcast Index also returns loose matches, e.g. on the first name only
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
        if (People.hasItem(person.getId(), existing.getId())) {
            return false;
        }
        FeedItem full = DBReader.getFeedItem(existing.getId());
        return full != null && People.addEpisode(person.getId(), full, People.SOURCE_PODCAST_INDEX);
    }

    /**
     * Removes episodes from the person's folder whose title, show notes and show title don't contain any of
     * their names as a whole phrase (left over from looser matching).
     *
     * @return number of episodes removed
     */
    public static int pruneNonMatching(Person person) {
        int removed = 0;
        List<FeedItem> items = loadItems(People.getItemIds(person.getId()), true, true);
        for (FeedItem item : items) {
            long itemId = item.getId();
            String feedTitle = item.getFeed() != null ? item.getFeed().getTitle() : null;
            if (!PersonMatcher.matchesAny(person.getNames(), item.getTitle(), item.getDescription(), feedTitle)) {
                People.forgetItem(person.getId(), itemId);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The episodes in a person's folder, newest first. Episodes AntennaPod no longer has are dropped.
     */
    public static List<FeedItem> getEpisodes(long personId) {
        return getEpisodes(personId, true);
    }

    private static List<FeedItem> getEpisodes(long personId, boolean withFeeds) {
        List<Long> ids = People.getItemIds(personId);
        List<FeedItem> result = loadItems(ids, withFeeds, false);
        if (result.size() < ids.size()) {
            Set<Long> found = new HashSet<>();
            for (FeedItem item : result) {
                found.add(item.getId());
            }
            for (long itemId : ids) {
                if (!found.contains(itemId)) {
                    People.forgetItem(personId, itemId);
                }
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
        for (FeedItem item : getEpisodes(personId, false)) {
            if (isEligible(item)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Loads the episodes in a few queries instead of one lookup per episode.
     */
    private static List<FeedItem> loadItems(List<Long> ids, boolean withFeeds, boolean withDescriptions) {
        List<FeedItem> result = new ArrayList<>(ids.size());
        if (ids.isEmpty()) {
            return result;
        }
        for (int start = 0; start < ids.size(); start += ID_CHUNK) {
            List<Long> chunk = ids.subList(start, Math.min(ids.size(), start + ID_CHUNK));
            String[] args = new String[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                args[i] = String.valueOf(chunk.get(i));
            }
            if (withDescriptions) {
                result.addAll(queryWithDescriptions(PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_ID
                        + " IN (" + TextUtils.join(",", args) + ")", null));
                continue;
            }
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try (FeedItemCursor cursor = new FeedItemCursor(adapter.getFeedItemCursor(args))) {
                while (cursor.moveToNext()) {
                    result.add(cursor.getFeedItem());
                }
            } finally {
                adapter.close();
            }
        }
        if (withFeeds) {
            DBReader.loadFeedDataOfFeedItemList(result);
        }
        return result;
    }

    private static boolean isEligible(FeedItem item) {
        return item.hasMedia() && !item.isPlayed() && !ArchiveStore.isArchived(item)
                && !item.isTagged(FeedItem.TAG_QUEUE);
    }

    private static long pubTime(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : Long.MAX_VALUE;
    }
}
