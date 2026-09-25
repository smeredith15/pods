package de.danoeh.antennapod.shufflepod;

import de.danoeh.antennapod.model.feed.FeedPreferences;

/**
 * The two tags that get special treatment. They are ordinary AntennaPod tags, so they show up as folders
 * on the Podcasts screen and can be edited like any other tag.
 * News: new episodes go to the queue. Entertainment: the show is in the Entertainment pool.
 * Shows with neither tag just collect episodes (there is no inbox).
 */
public final class ShowTags {
    public static final String NEWS = "News";
    public static final String ENTERTAINMENT = "Entertainment";

    private ShowTags() {
    }

    public static boolean has(FeedPreferences preferences, String tag) {
        return preferences != null && preferences.getTags().contains(tag);
    }

    public static boolean isNews(FeedPreferences preferences) {
        return has(preferences, NEWS);
    }
}
