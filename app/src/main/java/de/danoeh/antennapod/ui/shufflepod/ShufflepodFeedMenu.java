package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Collections;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;

/**
 * The fork's items in a show's menu: Folders (News, Entertainment and other folders) and Add episode.
 */
public final class ShufflepodFeedMenu {

    private ShufflepodFeedMenu() {
    }

    public static void prepare(Menu menu, Feed feed) {
        boolean subscribed = feed.getState() == Feed.STATE_SUBSCRIBED;
        MenuItem folders = menu.findItem(R.id.shufflepod_folders_item);
        if (folders != null) {
            folders.setVisible(subscribed);
        }
        MenuItem addEpisode = menu.findItem(R.id.shufflepod_add_episode_item);
        if (addEpisode != null) {
            addEpisode.setVisible(subscribed && !feed.isLocalFeed());
        }
    }

    /**
     * Returns true if the item was one of the fork's.
     */
    public static boolean onMenuItemClick(Context context, Menu menu, MenuItem item, Feed feed) {
        if (item.getItemId() == R.id.shufflepod_add_episode_item) {
            CustomEpisodeDialogs.showAddEpisode(context, feed);
            return true;
        } else if (item.getItemId() == R.id.shufflepod_folders_item) {
            ShufflepodFolderPicker.showForFeeds(context, Collections.singletonList(feed));
            return true;
        }
        return false;
    }
}
