package de.danoeh.antennapod.ui.shufflepod;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import de.danoeh.antennapod.R;

/**
 * Tabs above the Now Playing pages: Now playing (cover), Up next (queue) and Show notes. Swiping up from the
 * cover still opens the queue; on the queue and show notes the page swipe is off, so the list scrolls and
 * pulling down at the top closes the player.
 */
public final class ShufflepodPlayerTabs {
    private static final int POS_COVER = 0;
    private static final int[] LABELS = {
        R.string.shufflepod_player_tab_now_playing,
        R.string.shufflepod_player_tab_up_next,
        R.string.shufflepod_player_tab_show_notes,
    };

    private ShufflepodPlayerTabs() {
    }

    public static void attach(@NonNull TabLayout tabs, @NonNull ViewPager2 pager) {
        new TabLayoutMediator(tabs, pager, true, true, (tab, position) -> {
            if (position < LABELS.length) {
                tab.setText(LABELS[position]);
            }
        }).attach();
        pager.setUserInputEnabled(pager.getCurrentItem() == POS_COVER);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                pager.setUserInputEnabled(position == POS_COVER);
            }
        });
    }
}
