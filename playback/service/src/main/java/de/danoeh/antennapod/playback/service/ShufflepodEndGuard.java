package de.danoeh.antennapod.playback.service;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * SHUFFLEPOD: keeps a finished episode from starting over while the next one loads.
 * Between the end of an episode and the next one being set, the finished item stays in the player. A play
 * command in that gap (notification, headset, car) made Media3 seek it back to the start. And when a
 * stream fails in its last seconds, the episode never reaches its end, so playing again repeats it.
 */
public final class ShufflepodEndGuard {
    private static final long MIN_NEAR_END_MS = 15000;

    private ShufflepodEndGuard() {
    }

    /**
     * True while the finished episode is still in the player and the next one is being loaded.
     */
    public static boolean isLoadingNext(Player player, @Nullable FeedMedia current, @Nullable Disposable loader) {
        return current == null && player.getPlaybackState() == Player.STATE_ENDED
                && loader != null && !loader.isDisposed();
    }

    /**
     * True if the position is close enough to the end to treat a playback error as the episode finishing.
     */
    public static boolean isNearEnd(@Nullable FeedMedia media, long position) {
        if (media == null || media.getDuration() <= 0) {
            return false;
        }
        long margin = Math.max(MIN_NEAR_END_MS, UserPreferences.getSmartMarkAsPlayedSecs() * 1000L);
        return position >= media.getDuration() - margin;
    }
}
