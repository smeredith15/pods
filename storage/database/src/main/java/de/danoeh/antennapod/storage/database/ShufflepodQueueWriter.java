package de.danoeh.antennapod.storage.database;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.shufflepod.QueuePlacement;
import de.danoeh.antennapod.shufflepod.ShowSettings;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.greenrobot.eventbus.EventBus;

/**
 * SHUFFLEPOD: adds newly published episodes to the queue according to the show's Top/Bottom setting.
 * Replaces DBWriter.addQueueItem() for the "new episodes: add to queue" path only.
 */
public final class ShufflepodQueueWriter {
    private ShufflepodQueueWriter() {
    }

    public static Future<?> addNewEpisodes(final Context context, @Nullable final Feed feed,
                                           final List<FeedItem> newItems) {
        final List<FeedItem> items = new ArrayList<>(newItems);
        return DBWriter.runOnDbThread(() -> {
            if (items.isEmpty()) {
                return;
            }
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            final List<FeedItem> queue = DBReader.getQueue();

            List<FeedItem> toAdd = new ArrayList<>();
            for (FeedItem item : items) {
                if (item.hasMedia() && !containsItem(queue, item.getId())) {
                    toAdd.add(item);
                }
            }
            if (toAdd.isEmpty()) {
                adapter.close();
                return;
            }
            Collections.sort(toAdd, Comparator.comparing(FeedItem::getPubDate,
                    Comparator.nullsLast(Comparator.<Date>naturalOrder())));

            int position;
            if (feed != null && ShowSettings.getQueuePosition(feed) == ShowSettings.QueuePosition.TOP) {
                List<Long> queueFeedIds = new ArrayList<>();
                int currentlyPlayingIndex = -1;
                long currentlyPlayingMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
                for (int i = 0; i < queue.size(); i++) {
                    FeedItem queued = queue.get(i);
                    queueFeedIds.add(queued.getFeedId());
                    if (queued.getMedia() != null && queued.getMedia().getId() == currentlyPlayingMediaId) {
                        currentlyPlayingIndex = i;
                    }
                }
                position = QueuePlacement.topInsertPosition(queueFeedIds, currentlyPlayingIndex, feed.getId());
            } else {
                position = queue.size();
            }

            List<QueueEvent> events = new ArrayList<>();
            List<FeedItem> markAsUnplayed = new ArrayList<>();
            for (FeedItem item : toAdd) {
                queue.add(position, item);
                events.add(QueueEvent.added(item, position));
                item.addTag(FeedItem.TAG_QUEUE);
                if (item.isNew()) {
                    markAsUnplayed.add(item);
                }
                position++;
            }
            adapter.setQueue(queue);
            for (QueueEvent event : events) {
                EventBus.getDefault().post(event);
            }
            EventBus.getDefault().post(new FeedItemEvent(toAdd, false));
            if (!markAsUnplayed.isEmpty()) {
                DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, markAsUnplayed);
            }
            adapter.close();
            AutoDownloadManager.getInstance().autodownloadUndownloadedItems(context);
        });
    }

    private static boolean containsItem(List<FeedItem> items, long itemId) {
        for (FeedItem item : items) {
            if (item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }
}
