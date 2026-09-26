package de.danoeh.antennapod.playback.service.internal;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.session.MediaSession;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;

/**
 * SHUFFLEPOD: applies the episode's playback speed as soon as it is resolved, before the player starts.
 * Otherwise the service only sets it after loading the episode again, so playback started at 1x and then
 * sped up.
 */
public final class ShufflepodEarlySpeed {

    private ShufflepodEarlySpeed() {
    }

    public static void apply(MediaSession session, FeedMedia media) {
        if (media == null) {
            return;
        }
        float speed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(media);
        new Handler(Looper.getMainLooper()).post(() -> session.getPlayer().setPlaybackSpeed(speed));
    }
}
