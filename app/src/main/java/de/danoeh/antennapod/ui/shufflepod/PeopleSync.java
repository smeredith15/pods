package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;

/**
 * Fills followed people's folders: from the subscribed shows, and from Podcast Index when a key is set.
 * Must be called off the main thread.
 */
public final class PeopleSync {
    private static final String TAG = "PeopleSync";

    private PeopleSync() {
    }

    public static final class Result {
        public int fromSubscriptions;
        public int fromPodcastIndex;
        public String podcastIndexError;

        public int total() {
            return fromSubscriptions + fromPodcastIndex;
        }
    }

    public static Result syncPerson(Context context, long personId) {
        Result result = new Result();
        Person person = People.get(personId);
        if (person == null) {
            return result;
        }
        ShufflepodPeople.pruneNonMatching(person);
        result.fromSubscriptions = ShufflepodPeople.scanSubscriptions(person);
        searchPodcastIndex(context, person, result);
        return result;
    }

    /**
     * The periodic check: only episodes added since the last check are searched locally.
     */
    public static void syncAll(Context context) {
        ShufflepodPeople.scanNewEpisodes();
        for (Person person : People.getPeople()) {
            searchPodcastIndex(context, person, new Result());
        }
    }

    private static void searchPodcastIndex(Context context, Person person, Result result) {
        if (!People.hasPodcastIndexCredentials()) {
            return;
        }
        try {
            for (String name : person.getNames()) {
                for (ShufflepodPeople.RemoteEpisode remote : PodcastIndexClient.searchByPerson(name,
                        People.getPodcastIndexKey(), People.getPodcastIndexSecret())) {
                    if (ShufflepodPeople.addRemoteEpisode(context, person, remote)) {
                        result.fromPodcastIndex++;
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Podcast Index search failed for " + person.getName(), e);
            result.podcastIndexError = e.getMessage();
        }
    }
}
