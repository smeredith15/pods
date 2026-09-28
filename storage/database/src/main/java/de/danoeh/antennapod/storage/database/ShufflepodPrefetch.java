package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;

/**
 * SHUFFLEPOD: downloads what plays next so a patchy connection doesn't interrupt listening. Each time an
 * episode starts, the next few News episodes in the queue are downloaded; once the queue is nearly done,
 * the Entertainment episode that will follow it is downloaded too (without adding it to the queue).
 * Played episodes are deleted again by the "Delete played episodes" setting.
 */
public final class ShufflepodPrefetch {
    private static final String TAG = "ShufflepodPrefetch";
    static final int NEWS_AHEAD = 3;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ShufflepodPrefetch() {
    }

    public static void onEpisodeStarted(Context context, @Nullable FeedItem current) {
        final Context appContext = context.getApplicationContext();
        final long currentId = current != null ? current.getId() : -1;
        executor.execute(() -> {
            try {
                run(appContext, currentId);
                ShufflepodQueueHistory.record();
            } catch (RuntimeException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        });
    }

    private static void run(Context context, long currentId) {
        List<FeedItem> queue = DBReader.getQueue();
        int start = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == currentId) {
                start = i + 1;
                break;
            }
        }
        List<FeedItem> upcoming = new ArrayList<>();
        for (int i = start; i < queue.size(); i++) {
            FeedItem item = queue.get(i);
            if (item.getId() != currentId && item.hasMedia()) {
                upcoming.add(item);
            }
        }
        for (int i = 0; i < Math.min(NEWS_AHEAD, upcoming.size()); i++) {
            download(context, upcoming.get(i));
        }
        if (upcoming.size() <= NEWS_AHEAD) {
            FeedItem next = ShufflepodEntertainment.peekNext(currentId);
            if (next != null) {
                download(context, next);
            }
        }
    }

    private static void download(Context context, FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || media.isDownloaded() || media.getDownloadUrl() == null
                || DownloadServiceInterface.get().isDownloadingEpisode(media.getDownloadUrl())) {
            return;
        }
        DownloadServiceInterface.get().downloadNow(context, item, true);
    }
}
