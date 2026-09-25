package de.danoeh.antennapod.ui.shufflepod;

import android.os.SystemClock;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment;

/**
 * The "Now Playing" navigation entry. It isn't a screen of its own: it opens the full player (whose
 * next page, one swipe up, is the queue), or the Queue screen when nothing is loaded in the player.
 */
public final class NowPlayingTab {
    public static final String TAG = "NowPlayingFragment";
    private static final long OPEN_ON_START_WINDOW_MS = 10000;

    private static long openOnStartRequestedAt = 0;

    private NowPlayingTab() {
    }

    /**
     * The screen to load at app start. With Now Playing as the default, that's Podcasts underneath, and the
     * player opens once it has loaded the current episode.
     */
    public static String startPage(String defaultPage) {
        if (TAG.equals(defaultPage)) {
            openOnStartRequestedAt = SystemClock.elapsedRealtime();
            return SubscriptionFragment.TAG;
        }
        return defaultPage;
    }

    /**
     * The screen Back returns to before leaving the app.
     */
    public static String screenFor(String defaultPage) {
        return TAG.equals(defaultPage) ? SubscriptionFragment.TAG : defaultPage;
    }

    public static void onPlayerVisible(MainActivity activity, boolean visible) {
        if (!visible || openOnStartRequestedAt == 0) {
            return;
        }
        boolean recent = SystemClock.elapsedRealtime() - openOnStartRequestedAt < OPEN_ON_START_WINDOW_MS;
        openOnStartRequestedAt = 0;
        if (recent) {
            activity.getWindow().getDecorView().post(() ->
                    activity.getBottomSheet().setState(BottomSheetBehavior.STATE_EXPANDED));
        }
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
