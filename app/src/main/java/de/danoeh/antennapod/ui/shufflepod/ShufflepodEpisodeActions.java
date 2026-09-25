package de.danoeh.antennapod.ui.shufflepod;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.ArchiveStore;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;

/**
 * Episode menu actions added by the fork: archive, unarchive, the "everything older" bulk actions, and
 * attaching audio to an episode without any.
 */
public final class ShufflepodEpisodeActions {
    private static final String TAG = "ShufflepodEpisodeAction";

    private ShufflepodEpisodeActions() {
    }

    public static void onPrepareMenu(Menu menu, List<FeedItem> items) {
        boolean canArchive = false;
        boolean canUnarchive = false;
        for (FeedItem item : items) {
            if (!isSubscribed(item)) {
                continue;
            }
            if (ArchiveStore.isArchived(item)) {
                canUnarchive = true;
            } else {
                canArchive = true;
            }
        }
        boolean canChangeOlder = items.size() == 1 && isSubscribed(items.get(0))
                && items.get(0).getPubDate() != null;
        setVisible(menu, R.id.shufflepod_archive_item, canArchive);
        setVisible(menu, R.id.shufflepod_unarchive_item, canUnarchive);
        setVisible(menu, R.id.shufflepod_archive_older_item, canChangeOlder);
        setVisible(menu, R.id.shufflepod_mark_older_played_item, canChangeOlder);
        setVisible(menu, R.id.shufflepod_play_next_from_show_item,
                items.size() == 1 && isSubscribed(items.get(0)));
        setVisible(menu, R.id.shufflepod_attach_audio_item, items.size() == 1 && !items.get(0).hasMedia());
    }

    public static boolean onMenuItemClicked(@NonNull Fragment fragment, int menuItemId, @NonNull FeedItem item) {
        Activity activity = fragment.getActivity();
        if (activity == null) {
            return false;
        }
        if (menuItemId == R.id.shufflepod_archive_older_item) {
            changeOlderEpisodes(activity, item, true);
            return true;
        } else if (menuItemId == R.id.shufflepod_mark_older_played_item) {
            changeOlderEpisodes(activity, item, false);
            return true;
        } else if (menuItemId == R.id.shufflepod_attach_audio_item) {
            CustomEpisodeDialogs.showAttachAudio(activity, item);
            return true;
        } else if (menuItemId == R.id.shufflepod_play_next_from_show_item) {
            ForceNextDialog.show(activity, item.getFeedId(),
                    item.getFeed() != null ? item.getFeed().getTitle() : "", item.getId(), null);
            return true;
        }
        return handleMultiSelect(activity, menuItemId, Collections.singletonList(item));
    }

    public static boolean handleMultiSelect(@NonNull Activity activity, int actionId, List<FeedItem> items) {
        if (actionId == R.id.shufflepod_archive_item) {
            archive(activity, items);
            return true;
        } else if (actionId == R.id.shufflepod_unarchive_item) {
            unarchive(activity, items);
            return true;
        }
        return false;
    }

    public static void archive(@NonNull Context context, List<FeedItem> items) {
        List<FeedItem> toArchive = new ArrayList<>();
        for (FeedItem item : items) {
            if (!ArchiveStore.isArchived(item)) {
                toArchive.add(item);
            }
        }
        ArchiveStore.archive(toArchive);

        List<Long> queued = new ArrayList<>();
        List<FeedItem> inInbox = new ArrayList<>();
        for (FeedItem item : toArchive) {
            if (item.isTagged(FeedItem.TAG_QUEUE)) {
                queued.add(item.getId());
            }
            if (item.isNew()) {
                inInbox.add(item);
            }
        }
        if (!queued.isEmpty()) {
            long[] ids = new long[queued.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = queued.get(i);
            }
            DBWriter.removeQueueItem(context, false, ids);
        }
        if (!inInbox.isEmpty()) {
            DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, inInbox);
        }
        notifyChanged(toArchive);
        EventBus.getDefault().post(new MessageEvent(
                context.getResources().getQuantityString(R.plurals.shufflepod_archived_message,
                        toArchive.size(), toArchive.size()),
                ctx -> {
                    ArchiveStore.unarchive(toArchive);
                    notifyChanged(toArchive);
                }, context.getString(R.string.undo)));
    }

    public static void unarchive(@NonNull Context context, List<FeedItem> items) {
        List<FeedItem> toUnarchive = new ArrayList<>();
        for (FeedItem item : items) {
            if (ArchiveStore.isArchived(item)) {
                toUnarchive.add(item);
            }
        }
        ArchiveStore.unarchive(toUnarchive);
        notifyChanged(toUnarchive);
        EventBus.getDefault().post(new MessageEvent(context.getResources().getQuantityString(
                R.plurals.shufflepod_unarchived_message, toUnarchive.size(), toUnarchive.size())));
    }

    private static void notifyChanged(List<FeedItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Set<Long> feedIds = new LinkedHashSet<>();
        for (FeedItem item : items) {
            feedIds.add(item.getFeedId());
        }
        for (long feedId : feedIds) {
            EventBus.getDefault().post(new FeedListUpdateEvent(feedId));
        }
        EventBus.getDefault().post(new FeedItemEvent(items, false));
    }

    private static void changeOlderEpisodes(@NonNull Activity activity, @NonNull FeedItem item, boolean archive) {
        Observable.fromCallable(() -> {
            Feed feed = DBReader.getFeed(item.getFeedId(), false, 0, Integer.MAX_VALUE);
            List<FeedItem> older = new ArrayList<>();
            if (feed == null || item.getPubDate() == null) {
                return older;
            }
            for (FeedItem other : feed.getItems()) {
                if (other.getId() == item.getId() || other.getPubDate() == null
                        || !other.getPubDate().before(item.getPubDate()) || other.isPlayed()) {
                    continue;
                }
                if (archive && ArchiveStore.isArchived(other)) {
                    continue;
                }
                older.add(other);
            }
            return older;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(older -> {
                    if (older.isEmpty()) {
                        EventBus.getDefault().post(
                                new MessageEvent(activity.getString(R.string.shufflepod_older_none)));
                        return;
                    }
                    String feedTitle = item.getFeed() != null ? item.getFeed().getTitle() : "";
                    int message = archive ? R.plurals.shufflepod_archive_older_confirm
                            : R.plurals.shufflepod_mark_older_played_confirm;
                    new MaterialAlertDialogBuilder(activity)
                            .setMessage(activity.getResources().getQuantityString(message,
                                    older.size(), older.size(), feedTitle))
                            .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                                if (archive) {
                                    archive(activity, older);
                                } else {
                                    new EpisodeMultiSelectActionHandler(activity, R.id.mark_read_item)
                                            .handleAction(older);
                                }
                            })
                            .setNegativeButton(R.string.cancel_label, null)
                            .show();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static boolean isSubscribed(FeedItem item) {
        return item != null && item.getFeed() != null && item.getFeed().getState() == Feed.STATE_SUBSCRIBED;
    }

    private static void setVisible(Menu menu, int id, boolean visible) {
        MenuItem item = menu.findItem(id);
        if (item != null) {
            item.setVisible(visible);
        }
    }
}
