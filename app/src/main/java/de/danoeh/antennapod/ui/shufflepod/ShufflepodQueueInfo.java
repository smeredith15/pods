package de.danoeh.antennapod.ui.shufflepod;

import android.content.res.Resources;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;

/**
 * The queue's summary line: the real time left, and in brackets the time at each episode's playback speed.
 */
public final class ShufflepodQueueInfo {
    private static final long MIN_DIFFERENCE_MS = 60_000;

    private ShufflepodQueueInfo() {
    }

    public static String summary(Resources resources, List<FeedItem> queue) {
        long realLeft = 0;
        long adjustedLeft = 0;
        for (FeedItem item : queue) {
            if (item.getMedia() == null) {
                continue;
            }
            long itemLeft = Math.max(0, item.getMedia().getDuration() - item.getMedia().getPosition());
            float speed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
            realLeft += itemLeft;
            adjustedLeft += speed > 0 ? (long) (itemLeft / speed) : itemLeft;
        }
        String episodes = resources.getQuantityString(R.plurals.num_episodes, queue.size(), queue.size());
        String real = Converter.getDurationStringLocalized(resources, realLeft, false);
        if (Math.abs(realLeft - adjustedLeft) < MIN_DIFFERENCE_MS) {
            return resources.getString(R.string.queue_time_left_label, episodes, real);
        }
        String adjusted = Converter.getDurationStringLocalized(resources, adjustedLeft, false);
        return resources.getString(R.string.shufflepod_queue_time_left_with_speed, episodes, real, adjusted);
    }
}
