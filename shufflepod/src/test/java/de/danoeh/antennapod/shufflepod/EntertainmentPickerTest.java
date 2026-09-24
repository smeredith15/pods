package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EntertainmentPickerTest {

    @Test
    public void emptyPoolPicksNothing() {
        assertNull(EntertainmentPicker.pick(Collections.<String>emptyList(), new Random(1)));
        assertNull(EntertainmentPicker.pick(null, new Random(1)));
    }

    @Test
    public void singleShowAlwaysPicked() {
        for (int seed = 0; seed < 20; seed++) {
            assertEquals("a", EntertainmentPicker.pick(Collections.singletonList("a"), new Random(seed)));
        }
    }

    @Test
    public void deterministicWithSeed() {
        List<String> shows = Arrays.asList("a", "b", "c", "d");
        assertEquals(EntertainmentPicker.pick(shows, new Random(42)),
                EntertainmentPicker.pick(shows, new Random(42)));
    }

    @Test
    public void everyShowCanBePicked() {
        List<String> shows = Arrays.asList("a", "b", "c");
        Random random = new Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(EntertainmentPicker.pick(shows, random));
        }
        assertTrue(seen.containsAll(shows));
    }
}
