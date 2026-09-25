package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.ShufflepodShowTags;

/**
 * News and Entertainment toggles in a show's menu, and the matching batch actions on the Podcasts screen.
 */
public final class ShufflepodFeedMenu {

    private ShufflepodFeedMenu() {
    }

    public static void prepare(Menu menu, Feed feed) {
        boolean subscribed = feed.getState() == Feed.STATE_SUBSCRIBED;
        setChecked(menu.findItem(R.id.shufflepod_in_news_item), subscribed,
                ShowTags.isNews(feed.getPreferences()));
        setChecked(menu.findItem(R.id.shufflepod_in_entertainment_item), subscribed,
                ShowTags.has(feed.getPreferences(), ShowTags.ENTERTAINMENT));
    }

    /**
     * Returns true if the item was one of the fork's.
     */
    public static boolean onMenuItemClick(Menu menu, MenuItem item, Feed feed) {
        String tag;
        if (item.getItemId() == R.id.shufflepod_in_news_item) {
            tag = ShowTags.NEWS;
        } else if (item.getItemId() == R.id.shufflepod_in_entertainment_item) {
            tag = ShowTags.ENTERTAINMENT;
        } else {
            return false;
        }
        ShufflepodShowTags.setTag(feed, tag, !ShowTags.has(feed.getPreferences(), tag));
        prepare(menu, feed);
        return true;
    }

    /**
     * Batch action from the Podcasts screen's selection menu. Returns true if the item was one of the fork's.
     */
    public static boolean onBulkAction(Context context, int itemId, List<Feed> feeds) {
        final String tag;
        final int label;
        if (itemId == R.id.shufflepod_bulk_news_item) {
            tag = ShowTags.NEWS;
            label = R.string.shufflepod_news_label;
        } else if (itemId == R.id.shufflepod_bulk_entertainment_item) {
            tag = ShowTags.ENTERTAINMENT;
            label = R.string.shufflepod_entertainment_label;
        } else {
            return false;
        }
        final List<Feed> selected = new ArrayList<>(feeds);
        new MaterialAlertDialogBuilder(context)
                .setMessage(context.getResources().getQuantityString(R.plurals.shufflepod_bulk_tag_message,
                        selected.size(), selected.size(), context.getString(label)))
                .setPositiveButton(R.string.shufflepod_bulk_add, (dialog, which) -> apply(context, selected, tag, true))
                .setNeutralButton(R.string.shufflepod_bulk_remove, (dialog, which) ->
                        apply(context, selected, tag, false))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
        return true;
    }

    private static void apply(Context context, List<Feed> feeds, String tag, boolean enabled) {
        ShufflepodShowTags.setTag(feeds, tag, enabled);
        EventBus.getDefault().post(new MessageEvent(context.getResources().getQuantityString(
                R.plurals.updated_feeds_batch_label, feeds.size(), feeds.size())));
    }

    private static void setChecked(MenuItem item, boolean visible, boolean checked) {
        if (item != null) {
            item.setVisible(visible);
            item.setChecked(checked);
        }
    }
}
