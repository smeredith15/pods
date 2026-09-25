package de.danoeh.antennapod.ui.shufflepod;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.ShowSettings;

/**
 * Fork settings shown on a podcast's settings screen. "New episodes action" is hidden: the News tag
 * decides whether new episodes go to the queue, and there is no inbox.
 */
public final class ShufflepodFeedSettings {
    private static final String PREF_QUEUE_POSITION = "shufflepodQueuePosition";
    private static final String PREF_NEW_EPISODES_ACTION = "feedNewEpisodesAction";

    private ShufflepodFeedSettings() {
    }

    public static void setup(@NonNull PreferenceFragmentCompat fragment, @NonNull Feed feed) {
        Preference newEpisodesAction = fragment.findPreference(PREF_NEW_EPISODES_ACTION);
        if (newEpisodesAction != null) {
            newEpisodesAction.setVisible(false);
        }
        Preference preference = fragment.findPreference(PREF_QUEUE_POSITION);
        if (preference == null) {
            return;
        }
        if (feed.isLocalFeed()) {
            preference.setVisible(false);
            return;
        }
        updateSummary(preference, feed);
        preference.setOnPreferenceClickListener(pref -> {
            ShowSettings.QueuePosition[] positions = ShowSettings.QueuePosition.values();
            String[] labels = new String[positions.length];
            int checked = 0;
            ShowSettings.QueuePosition current = ShowSettings.getQueuePosition(feed);
            for (int i = 0; i < positions.length; i++) {
                labels[i] = fragment.getString(label(positions[i]));
                if (positions[i] == current) {
                    checked = i;
                }
            }
            new MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.shufflepod_queue_position_title)
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        ShowSettings.setQueuePosition(feed, positions[which]);
                        updateSummary(pref, feed);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel_label, null)
                    .show();
            return true;
        });
    }

    private static void updateSummary(Preference preference, Feed feed) {
        preference.setSummary(ShowSettings.getQueuePosition(feed) == ShowSettings.QueuePosition.TOP
                ? R.string.shufflepod_queue_position_top_summary
                : R.string.shufflepod_queue_position_bottom_summary);
    }

    private static int label(ShowSettings.QueuePosition position) {
        return position == ShowSettings.QueuePosition.TOP
                ? R.string.shufflepod_queue_position_top
                : R.string.shufflepod_queue_position_bottom;
    }
}
