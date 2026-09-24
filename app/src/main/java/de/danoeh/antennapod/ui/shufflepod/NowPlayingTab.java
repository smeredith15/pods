package de.danoeh.antennapod.ui.shufflepod;

import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;

/**
 * The "Now Playing" navigation entry. It isn't a screen of its own: it opens the full player (whose
 * next page, one swipe up, is the queue), or the Queue screen when nothing is loaded in the player.
 */
public final class NowPlayingTab {
    public static final String TAG = "NowPlayingFragment";

    private NowPlayingTab() {
    }

    public static void open(MainActivity activity) {
        View player = activity.findViewById(R.id.audioplayerFragment);
        if (player != null && player.getVisibility() == View.VISIBLE) {
            activity.getBottomSheet().setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            activity.loadFragment(QueueFragment.TAG, null);
        }
    }
}
