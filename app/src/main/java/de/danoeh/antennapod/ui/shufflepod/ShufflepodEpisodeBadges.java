package de.danoeh.antennapod.ui.shufflepod;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.ArchiveStore;

/**
 * Fork status icons on episode list rows, and the show name shown in bold on queue rows.
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

    public static void showFeedTitle(TextView size, FeedItem item) {
        size.setText(item != null && item.getFeed() != null ? item.getFeed().getTitle() : "");
        size.setTypeface(Typeface.create(size.getTypeface(), Typeface.BOLD));
        size.setMaxLines(1);
        size.setEllipsize(TextUtils.TruncateAt.END);
    }

    public static void resetSize(TextView size) {
        size.setTypeface(Typeface.create(size.getTypeface(), Typeface.NORMAL));
    }
}
