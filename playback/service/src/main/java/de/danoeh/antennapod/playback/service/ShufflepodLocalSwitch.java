package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.internal.ExoPlayerUtils;
import de.danoeh.antennapod.storage.database.DBReader;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SHUFFLEPOD: prefers the downloaded file over the stream mid-episode. The player picks the file or the stream
 * when an episode is loaded, so an episode that finished downloading after it started kept streaming (and
 * buffering). When a streamed episode buffers or fails and a download of it now exists, playback continues
 * from the file at the same position.
 */
public final class ShufflepodLocalSwitch {
    private static final String TAG = "ShufflepodLocalSwitch";
    private static Disposable pending;

    private ShufflepodLocalSwitch() {
    }

    public static boolean isStreaming(Player player) {
        MediaItem item = player.getCurrentMediaItem();
        return item != null && item.localConfiguration != null
                && item.localConfiguration.uri.toString().startsWith("http");
    }

    /**
     * Must be called on the main thread.
     *
     * @param onNotSwitched run on the main thread if there is no download to switch to (may be null)
     */
    public static void trySwitch(Context context, Player player, long mediaId, @Nullable Runnable onNotSwitched) {
        if (!isStreaming(player) || (pending != null && !pending.isDisposed())) {
            if (onNotSwitched != null) {
                onNotSwitched.run();
            }
            return;
        }
        final Context appContext = context.getApplicationContext();
        pending = Maybe.fromCallable(() -> {
            FeedMedia media = DBReader.getFeedMedia(mediaId);
            if (media == null || !media.localFileAvailable()) {
                return null;
            }
            return MediaItemAdapter.fromPlayable(appContext, media, false);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(item -> {
                    MediaItem current = player.getCurrentMediaItem();
                    if (current != null && String.valueOf(mediaId).equals(current.mediaId) && isStreaming(player)) {
                        Log.d(TAG, "Continuing from the downloaded file");
                        player.setMediaItem(item, player.getCurrentPosition());
                        player.prepare();
                    } else if (onNotSwitched != null) {
                        onNotSwitched.run();
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    if (onNotSwitched != null) {
                        onNotSwitched.run();
                    }
                }, () -> {
                    if (onNotSwitched != null) {
                        onNotSwitched.run();
                    }
                });
    }

    /**
     * What the service does on a playback error it can't recover from.
     */
    public static void reportError(Context context, @NonNull PlaybackException error) {
        PlaybackService.isRunning = false;
        EventBus.getDefault().post(new PlayerErrorEvent(ExoPlayerUtils.translateErrorReason(error, context)));
        EventBus.getDefault().post(new PlayerStatusEvent());
    }
}
