package de.danoeh.antennapod.shufflepod;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

/**
 * Stable identifiers that survive AntennaPod database IDs changing (e.g. after resubscribing).
 */
public final class EpisodeKeys {
    private EpisodeKeys() {
    }

    public static String feedKey(Feed feed) {
        if (feed == null) {
            return "";
        }
        return feed.getDownloadUrl() != null ? feed.getDownloadUrl() : "feed:" + feed.getId();
    }

    public static String feedKey(FeedItem item) {
        return item.getFeed() != null ? feedKey(item.getFeed()) : "feed:" + item.getFeedId();
    }

    public static String episodeKey(FeedItem item) {
        String value = item.getIdentifyingValue();
        return value != null ? value : "item:" + item.getId();
    }
}
