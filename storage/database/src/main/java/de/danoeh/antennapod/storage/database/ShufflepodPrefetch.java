package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;

/**
 * SHUFFLEPOD: downloads what plays next so a patchy connection doesn't interrupt listening: the next few
 * News episodes in the queue and, once the queue is nearly done, the Entertainment episode that will follow
 * it (without adding it to the queue). Downloads run one at a time, next episode first, so they don't compete
 * with each other or with the episode being streamed: an episode start queues the first missing one (after a
 * short delay, so the stream can buffer), and each finished download queues the next.
 * Played episodes are deleted again by the "Delete played episodes" setting.
 */
public final class ShufflepodPrefetch {
    private static final String TAG = "ShufflepodPrefetch";
    static final int NEWS_AHEAD = 3;
    private static final long START_DELAY_SECONDS = 30;
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private ShufflepodPrefetch() {
    }

    public static void onEpisodeStarted(Context context, @Nullable FeedItem current) {
        final Context appContext = context.getApplicationContext();
        final long currentId = current != null ? current.getId() : -1;
        executor.schedule(() -> {
            try {
                run(appContext, currentId);
                ShufflepodQueueHistory.record();
            } catch (RuntimeException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }, START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Called by the download worker after an episode finished downloading: queues the next one.
     */
    public static void onDownloadFinished(Context context) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
                FeedMedia playing = mediaId != PlaybackPreferences.NO_MEDIA_PLAYING
                        ? DBReader.getFeedMedia(mediaId) : null;
                run(appContext, playing != null ? playing.getItemId() : -1);
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
            if (download(context, upcoming.get(i))) {
                return;
            }
        }
        if (upcoming.size() <= NEWS_AHEAD) {
            FeedItem next = ShufflepodEntertainment.peekNext(currentId);
            if (next != null) {
                download(context, next);
            }
        }
    }

    /**
     * Queues the download unless the episode is already downloaded. Queueing an episode that is already
     * downloading does nothing (the download work is unique per episode).
     *
     * @return true if the episode still needs downloading, so nothing further should be queued for now
     */
    private static boolean download(Context context, FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || media.isDownloaded() || media.getDownloadUrl() == null) {
            return false;
        }
        DownloadServiceInterface.get().downloadNow(context, item, true);
        return true;
    }
}
