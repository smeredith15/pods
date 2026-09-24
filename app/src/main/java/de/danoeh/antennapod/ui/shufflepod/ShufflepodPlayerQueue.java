package de.danoeh.antennapod.ui.shufflepod;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.ui.screen.queue.QueueFragment;

/**
 * The queue shown inside the Now Playing screen, one swipe up from the cover.
 */
public final class ShufflepodPlayerQueue {
    private static final String ARG_EMBEDDED_IN_PLAYER = "shufflepod_embedded_in_player";

    private ShufflepodPlayerQueue() {
    }

    public static Fragment newEmbeddedQueue() {
        QueueFragment fragment = new QueueFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_EMBEDDED_IN_PLAYER, true);
        fragment.setArguments(args);
        return fragment;
    }

    public static boolean isEmbedded(@NonNull Fragment fragment) {
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean(ARG_EMBEDDED_IN_PLAYER, false);
    }
}
