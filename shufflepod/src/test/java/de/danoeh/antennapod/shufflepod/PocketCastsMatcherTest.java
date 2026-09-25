package de.danoeh.antennapod.shufflepod;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class PocketCastsMatcherTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void matchesByUrlIgnoringSchemeAndQuery() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(1, "https://cdn.example.com/show/ep1.mp3?utm=x", "One", 0),
                new PocketCastsMatcher.Episode(2, "https://cdn.example.com/show/ep2.mp3", "Two", 0)));
        assertEquals(2, matcher.match(new PocketCastsMatcher.Episode(0, "http://CDN.example.com/show/ep2.mp3",
                "Other title", 0)));
    }

    @Test
    public void matchesByFileNameWhenTrackingPrefixChanged() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(7, "https://dts.podtrac.com/redirect.mp3/host.fm/abc123.mp3",
                        "Seven", 0)));
        assertEquals(7, matcher.match(new PocketCastsMatcher.Episode(0, "https://host.fm/abc123.mp3", "x", 0)));
    }

    @Test
    public void matchesByTitleAndTellsRerunsApartByDate() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(1, "https://a/1.mp3", "Best Of: Café Stories", 100 * DAY),
                new PocketCastsMatcher.Episode(2, "https://a/2.mp3", "Best of - Cafe stories", 400 * DAY)));
        assertEquals(2, matcher.match(new PocketCastsMatcher.Episode(0, "https://b/zz.mp3",
                "BEST OF: CAFE STORIES", 401 * DAY)));
        assertEquals(-1, matcher.match(new PocketCastsMatcher.Episode(0, "https://b/zz.mp3",
                "Best of Cafe Stories", 0)));
    }

    @Test
    public void singleTitleMatchRejectsFarAwayDate() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(5, "https://a/5.mp3", "Trailer", 10 * DAY)));
        assertEquals(5, matcher.match(new PocketCastsMatcher.Episode(0, null, "Trailer", 0)));
        assertEquals(-1, matcher.match(new PocketCastsMatcher.Episode(0, null, "Trailer", 90 * DAY)));
    }

    @Test
    public void ambiguousFileNamesAreNotUsed() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(1, "https://a/one/audio.mp3", "One", 0),
                new PocketCastsMatcher.Episode(2, "https://a/two/audio.mp3", "Two", 0)));
        assertEquals(-1, matcher.match(new PocketCastsMatcher.Episode(0, "https://b/audio.mp3", "Three", 0)));
    }

    @Test
    public void unknownEpisodeIsNotMatched() {
        PocketCastsMatcher matcher = new PocketCastsMatcher(Arrays.asList(
                new PocketCastsMatcher.Episode(1, "https://a/1.mp3", "One", 0)));
        assertEquals(-1, matcher.match(new PocketCastsMatcher.Episode(0, "https://z/9.mp3", "Nine", 0)));
    }
}
