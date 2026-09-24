package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class QueuePlacementTest {
    private static final long SHOW = 7;

    @Test
    public void emptyQueueGoesFirst() {
        assertEquals(0, QueuePlacement.topInsertPosition(Collections.emptyList(), -1, SHOW));
    }

    @Test
    public void nothingPlayingGoesToTop() {
        List<Long> queue = Arrays.asList(1L, 2L, 3L);
        assertEquals(0, QueuePlacement.topInsertPosition(queue, -1, SHOW));
    }

    @Test
    public void neverAboveCurrentlyPlaying() {
        List<Long> queue = Arrays.asList(1L, 2L, 3L);
        assertEquals(1, QueuePlacement.topInsertPosition(queue, 0, SHOW));
        assertEquals(2, QueuePlacement.topInsertPosition(queue, 1, SHOW));
    }

    @Test
    public void neverAboveSameShow() {
        List<Long> queue = Arrays.asList(1L, SHOW, 2L, SHOW, 3L);
        assertEquals(4, QueuePlacement.topInsertPosition(queue, 0, SHOW));
        assertEquals(4, QueuePlacement.topInsertPosition(queue, -1, SHOW));
    }

    @Test
    public void sameShowAboveCurrentlyPlayingIsIgnored() {
        List<Long> queue = Arrays.asList(SHOW, 1L, 2L);
        assertEquals(2, QueuePlacement.topInsertPosition(queue, 1, SHOW));
    }

    @Test
    public void currentlyPlayingSameShow() {
        List<Long> queue = Arrays.asList(SHOW, 1L);
        assertEquals(1, QueuePlacement.topInsertPosition(queue, 0, SHOW));
    }

    @Test
    public void currentlyPlayingLastInQueue() {
        List<Long> queue = Arrays.asList(1L, 2L);
        assertEquals(2, QueuePlacement.topInsertPosition(queue, 1, SHOW));
    }
}
