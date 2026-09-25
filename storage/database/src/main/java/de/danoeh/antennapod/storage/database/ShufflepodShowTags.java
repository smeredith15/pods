package de.danoeh.antennapod.storage.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.shufflepod.ShowTags;

/**
 * SHUFFLEPOD: reads and changes membership of the News and Entertainment tags.
 */
public final class ShufflepodShowTags {

    private ShufflepodShowTags() {
    }

    /**
     * Subscribed shows carrying the tag, sorted by title. Must be called off the main thread.
     */
    public static List<Feed> getFeeds(String tag) {
        List<Feed> result = new ArrayList<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED && ShowTags.has(feed.getPreferences(), tag)) {
                result.add(feed);
            }
        }
        Collections.sort(result, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.getTitle() != null ? a.getTitle() : "", b.getTitle() != null ? b.getTitle() : ""));
        return result;
    }

    /**
     * The News shows, for modules that don't see the fork's tag constants.
     */
    public static List<Feed> getNewsFeeds() {
        return getFeeds(ShowTags.NEWS);
    }

    public static List<Long> getFeedIds(String tag) {
        List<Long> ids = new ArrayList<>();
        for (Feed feed : getFeeds(tag)) {
            ids.add(feed.getId());
        }
        return ids;
    }

    /**
     * Adds or removes the tag on each show and saves them.
     */
    public static void setTag(Collection<Feed> feeds, String tag, boolean enabled) {
        for (Feed feed : feeds) {
            setTag(feed, tag, enabled);
        }
    }

    public static Future<?> setTag(Feed feed, String tag, boolean enabled) {
        FeedPreferences preferences = feed.getPreferences();
        if (enabled) {
            preferences.getTags().add(tag);
        } else {
            preferences.getTags().remove(tag);
        }
        return DBWriter.setFeedPreferences(preferences);
    }
}
