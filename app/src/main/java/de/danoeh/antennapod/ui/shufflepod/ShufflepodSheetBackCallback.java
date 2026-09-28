package de.danoeh.antennapod.ui.shufflepod;

import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.view.BottomSheetBackPressedCallback;

/**
 * Back inside the full player: from the queue or show notes it returns to the cover; on the cover it closes
 * the player if there is a screen underneath to go back to, otherwise it leaves the app. Together with
 * {@link NowPlayingTab#expandOnBack}, Now Playing is the last stop before leaving.
 */
public class ShufflepodSheetBackCallback extends BottomSheetBackPressedCallback {
    private final MainActivity activity;

    public ShufflepodSheetBackCallback(MainActivity activity, BottomSheetBehavior<?> sheetBehavior, View view) {
        super(false, sheetBehavior, view);
        this.activity = activity;
    }

    @Override
    public void handleOnBackPressed() {
        View player = activity.findViewById(R.id.audioplayerFragment);
        ViewPager2 pager = player != null ? player.findViewById(R.id.pager) : null;
        if (pager != null && pager.getCurrentItem() != 0) {
            handleOnBackCancelled();
            pager.setCurrentItem(0, true);
        } else if (activity.getSupportFragmentManager().getBackStackEntryCount() == 0) {
            handleOnBackCancelled();
            activity.finish();
        } else {
            super.handleOnBackPressed();
        }
    }
}
