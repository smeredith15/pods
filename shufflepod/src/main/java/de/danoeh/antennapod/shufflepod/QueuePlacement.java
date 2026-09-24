package de.danoeh.antennapod.shufflepod;

import java.util.List;

/**
 * Pure logic for where automatically added episodes go in the queue.
 */
public final class QueuePlacement {
    private QueuePlacement() {
    }

    /**
     * Insert position for a new episode of a "Top" show: directly below the currently playing
     * episode (so playback is never interrupted), but never above an episode of the same show
     * that is already queued (it goes right after the last one of them).
     *
     * @param queueFeedIds          feed ID of each queue entry, in queue order
     * @param currentlyPlayingIndex index of the currently playing episode in the queue, or -1
     * @param feedId                feed ID of the episode being added
     */
    public static int topInsertPosition(List<Long> queueFeedIds, int currentlyPlayingIndex, long feedId) {
        int position = Math.max(0, currentlyPlayingIndex + 1);
        position = Math.min(position, queueFeedIds.size());
        for (int i = queueFeedIds.size() - 1; i >= position; i--) {
            Long id = queueFeedIds.get(i);
            if (id != null && id == feedId) {
                return i + 1;
            }
        }
        return position;
    }
}
