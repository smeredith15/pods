package de.danoeh.antennapod.ui.shufflepod;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import de.danoeh.antennapod.R;

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

    /**
     * Pulling down at the top of the embedded queue goes back to the cover page instead of closing the
     * player. It takes over before the list would start its own drag (which the player sheet would turn
     * into closing), and leaves long presses (dragging to reorder) alone.
     */
    public static void pullDownToCover(@NonNull Fragment fragment, @NonNull RecyclerView recyclerView) {
        final int threshold = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
        final long longPress = ViewConfiguration.getLongPressTimeout();
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float startX;
            private float startY;
            private boolean atTop;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    atTop = !rv.canScrollVertically(-1);
                    return false;
                }
                if (event.getActionMasked() != MotionEvent.ACTION_MOVE || !atTop
                        || event.getEventTime() - event.getDownTime() > longPress) {
                    return false;
                }
                float dy = event.getY() - startY;
                if (dy > threshold && dy > Math.abs(event.getX() - startX)) {
                    atTop = false;
                    showCover(fragment);
                    return true;
                }
                return false;
            }
        });
    }

    private static void showCover(Fragment fragment) {
        Fragment player = fragment.getParentFragment();
        View playerView = player != null ? player.getView() : null;
        ViewPager2 pager = playerView != null ? playerView.findViewById(R.id.pager) : null;
        if (pager != null) {
            pager.setCurrentItem(0, true);
        }
    }
}
