package de.danoeh.antennapod.ui.shufflepod;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.google.android.material.button.MaterialButtonToggleGroup;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * "All shows / This show" switch in the playback speed dialog. Speed changes are saved as the global default
 * or as the current show's own speed. It starts on "This show" when the show already has its own speed.
 */
public class ShufflepodSpeedScope {
    private MaterialButtonToggleGroup group;
    private Consumer<Float> display;
    private Feed feed;
    private boolean showScope = false;

    public void bind(View root, Consumer<Float> display) {
        this.group = root.findViewById(R.id.shufflepodSpeedScope);
        this.display = display;
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            showScope = checkedId == R.id.shufflepodSpeedScopeShow;
            float own = ownSpeed();
            display.accept(showScope && own != FeedPreferences.SPEED_USE_GLOBAL ? own
                    : UserPreferences.getPlaybackSpeed());
        });
    }

    public void setMedia(@Nullable Playable media) {
        feed = null;
        if (media instanceof FeedMedia && ((FeedMedia) media).getItem() != null) {
            Feed candidate = ((FeedMedia) media).getItem().getFeed();
            if (candidate != null && candidate.getPreferences() != null) {
                feed = candidate;
            }
        }
        if (group == null) {
            return;
        }
        group.setVisibility(feed != null ? View.VISIBLE : View.GONE);
        showScope = ownSpeed() != FeedPreferences.SPEED_USE_GLOBAL;
        group.check(showScope ? R.id.shufflepodSpeedScopeShow : R.id.shufflepodSpeedScopeAll);
    }

    /**
     * Saves the chosen speed where the switch points. Replaces the dialog's UserPreferences.setPlaybackSpeed.
     */
    public void save(float speed) {
        Feed current = feed;
        if (showScope && current != null) {
            FeedPreferences preferences = current.getPreferences();
            preferences.setFeedPlaybackSpeed(speed);
            DBWriter.setFeedPreferences(preferences);
        } else {
            UserPreferences.setPlaybackSpeed(speed);
        }
    }

    /**
     * The show's own speed, or {@link FeedPreferences#SPEED_USE_GLOBAL}.
     */
    private float ownSpeed() {
        Feed current = feed;
        return current != null ? current.getPreferences().getFeedPlaybackSpeed() : FeedPreferences.SPEED_USE_GLOBAL;
    }
}
