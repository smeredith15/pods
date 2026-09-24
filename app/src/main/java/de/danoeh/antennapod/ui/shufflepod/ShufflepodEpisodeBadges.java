package de.danoeh.antennapod.ui.shufflepod;

import android.view.View;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.ArchiveStore;

/**
 * Fork status icons on episode list rows.
 */
public final class ShufflepodEpisodeBadges {
    private ShufflepodEpisodeBadges() {
    }

    public static void bind(View itemView, View container, FeedItem item) {
        boolean archived = ArchiveStore.isArchived(item);
        View badge = itemView.findViewById(R.id.shufflepodArchived);
        if (badge != null) {
            badge.setVisibility(archived ? View.VISIBLE : View.GONE);
        }
        if (archived) {
            container.setAlpha(0.5f);
        }
    }

    public static void hide(View itemView) {
        View badge = itemView.findViewById(R.id.shufflepodArchived);
        if (badge != null) {
            badge.setVisibility(View.GONE);
        }
    }

    public static boolean isShown(View itemView) {
        View badge = itemView.findViewById(R.id.shufflepodArchived);
        return badge != null && badge.getVisibility() == View.VISIBLE;
    }
}
