package de.danoeh.antennapod.shufflepod;

import java.util.List;
import java.util.Random;

/**
 * Pure logic for choosing the next Entertainment episode.
 */
public final class EntertainmentPicker {
    private EntertainmentPicker() {
    }

    /**
     * Picks one of the candidates (one per show that still has an eligible episode) uniformly at random.
     *
     * @return the chosen candidate, or null if there are none
     */
    public static <T> T pick(List<T> candidates, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
