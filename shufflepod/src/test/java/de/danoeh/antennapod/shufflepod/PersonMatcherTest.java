package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonMatcherTest {
    private static final List<String> MARA = Collections.singletonList("Mara Wilson");

    @Test
    public void matchesWholeName() {
        assertTrue(PersonMatcher.matchesAny(MARA, "Episode 12 with Mara Wilson"));
        assertTrue(PersonMatcher.matchesAny(MARA, "Mara Wilson!"));
    }

    @Test
    public void caseAndAccentInsensitive() {
        assertTrue(PersonMatcher.matchesAny(MARA, "MARA WILSON returns"));
        assertTrue(PersonMatcher.matchesAny(Collections.singletonList("Beyoncé"), "talking about beyonce"));
        assertTrue(PersonMatcher.matchesAny(Collections.singletonList("Beyonce"), "Beyoncé speaks"));
    }

    @Test
    public void onlyWholeWords() {
        assertFalse(PersonMatcher.matchesAny(Collections.singletonList("Mara"), "Marathon training"));
        assertFalse(PersonMatcher.matchesAny(MARA, "Mara Wilsonville"));
        assertTrue(PersonMatcher.matchesAny(Collections.singletonList("Mara"), "Mara's new book"));
    }

    @Test
    public void ignoresHtmlAndExtraSpaces() {
        assertTrue(PersonMatcher.matchesAny(MARA, "<p>Guest: <b>Mara</b>  Wilson</p>"));
    }

    @Test
    public void curlyApostrophes() {
        assertTrue(PersonMatcher.matchesAny(Collections.singletonList("Conan O'Brien"),
                "Conan O’Brien Needs a Friend"));
    }

    @Test
    public void anyAliasAnyText() {
        List<String> names = Arrays.asList("Marc Maron", "Maron");
        assertTrue(PersonMatcher.matchesAny(names, "nothing here", "an interview with Maron"));
        assertFalse(PersonMatcher.matchesAny(names, null, ""));
    }

    @Test
    public void parsesAliases() {
        assertEquals(Arrays.asList("Marc Maron", "Maron"),
                PersonMatcher.parseAliases(" Marc  Maron , Maron,\nMaron, "));
        assertTrue(PersonMatcher.parseAliases(null).isEmpty());
    }
}
