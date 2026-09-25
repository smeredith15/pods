package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.model.feed.FeedItem;

/**
 * Followed people, the episodes found for each of them ("their folder"), and the shows and episodes
 * muted per person. Everything is cached in memory; writes are persisted in the background.
 */
public final class People {
    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_PODCAST_INDEX = "podcastindex";
    private static final String SETTING_PI_KEY = "podcastindex_key";
    private static final String SETTING_PI_SECRET = "podcastindex_secret";

    private static final Map<Long, Person> persons = new LinkedHashMap<>();
    // personId -> (feedKey + "\n" + episodeKey) -> item ID
    private static final Map<Long, Map<String, Long>> episodes = new HashMap<>();
    // personId -> feedKey -> show title
    private static final Map<Long, Map<String, String>> mutedShows = new HashMap<>();
    // personId -> feedKey + "\n" + episodeKey
    private static final Map<Long, Set<String>> mutedEpisodes = new HashMap<>();
    private static final Map<String, String> settings = new HashMap<>();
    private static long nextId = 1;

    private People() {
    }

    static synchronized void load(SQLiteDatabase db) {
        persons.clear();
        episodes.clear();
        mutedShows.clear();
        mutedEpisodes.clear();
        settings.clear();
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_PERSON, new String[]{ShufflepodDatabase.KEY_ID,
                ShufflepodDatabase.KEY_NAME, ShufflepodDatabase.KEY_ALIASES, ShufflepodDatabase.KEY_IN_POOL,
                ShufflepodDatabase.KEY_SINCE}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                Person p = new Person(c.getLong(0), c.getString(1), PersonMatcher.parseAliases(c.getString(2)),
                        c.getInt(3) != 0, c.getLong(4));
                persons.put(p.getId(), p);
                nextId = Math.max(nextId, p.getId() + 1);
            }
        }
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_PERSON_EPISODE, new String[]{
                ShufflepodDatabase.KEY_PERSON_ID, ShufflepodDatabase.KEY_FEED_URL, ShufflepodDatabase.KEY_EPISODE_KEY,
                ShufflepodDatabase.KEY_ITEM_ID}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                episodesOf(c.getLong(0)).put(key(c.getString(1), c.getString(2)), c.getLong(3));
            }
        }
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_PERSON_MUTED_SHOW, new String[]{
                ShufflepodDatabase.KEY_PERSON_ID, ShufflepodDatabase.KEY_FEED_URL, ShufflepodDatabase.KEY_TITLE},
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                mutedShowsOf(c.getLong(0)).put(c.getString(1), c.getString(2));
            }
        }
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_PERSON_MUTED_EPISODE, new String[]{
                ShufflepodDatabase.KEY_PERSON_ID, ShufflepodDatabase.KEY_FEED_URL, ShufflepodDatabase.KEY_EPISODE_KEY},
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                mutedEpisodesOf(c.getLong(0)).add(key(c.getString(1), c.getString(2)));
            }
        }
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_APP_SETTING, new String[]{ShufflepodDatabase.KEY_NAME,
                ShufflepodDatabase.KEY_VALUE}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                settings.put(c.getString(0), c.getString(1));
            }
        }
    }

    private static String key(String feedKey, String episodeKey) {
        return feedKey + "\n" + episodeKey;
    }

    private static Map<String, Long> episodesOf(long personId) {
        Map<String, Long> map = episodes.get(personId);
        if (map == null) {
            map = new LinkedHashMap<>();
            episodes.put(personId, map);
        }
        return map;
    }

    private static Map<String, String> mutedShowsOf(long personId) {
        Map<String, String> map = mutedShows.get(personId);
        if (map == null) {
            map = new LinkedHashMap<>();
            mutedShows.put(personId, map);
        }
        return map;
    }

    private static Set<String> mutedEpisodesOf(long personId) {
        Set<String> set = mutedEpisodes.get(personId);
        if (set == null) {
            set = new HashSet<>();
            mutedEpisodes.put(personId, set);
        }
        return set;
    }

    // ---- people ----

    public static synchronized List<Person> getPeople() {
        List<Person> list = new ArrayList<>(persons.values());
        Collections.sort(list, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        return list;
    }

    @Nullable
    public static synchronized Person get(long personId) {
        return persons.get(personId);
    }

    public static synchronized List<Person> getPooledPeople() {
        List<Person> list = new ArrayList<>();
        for (Person p : persons.values()) {
            if (p.isInPool()) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Follows a new person.
     *
     * @param includePastAppearances collect episodes published before today too
     */
    public static synchronized Person add(String name, List<String> aliases, boolean includePastAppearances,
                                          boolean inPool) {
        long now = System.currentTimeMillis();
        Person person = new Person(nextId++, name, aliases, inPool, includePastAppearances ? 0 : now);
        persons.put(person.getId(), person);
        savePerson(person, now);
        return person;
    }

    public static synchronized void setInPool(long personId, boolean inPool) {
        Person person = persons.get(personId);
        if (person != null) {
            replace(person.withInPool(inPool));
        }
    }

    public static synchronized void includePastAppearances(long personId) {
        Person person = persons.get(personId);
        if (person != null) {
            replace(person.withSince(0));
        }
    }

    public static synchronized void rename(long personId, String name, List<String> aliases) {
        Person person = persons.get(personId);
        if (person != null) {
            replace(person.withNames(name, aliases));
        }
    }

    private static void replace(Person person) {
        persons.put(person.getId(), person);
        savePerson(person, System.currentTimeMillis());
    }

    private static void savePerson(Person person, long addedAt) {
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_ID, person.getId());
        values.put(ShufflepodDatabase.KEY_NAME, person.getName());
        values.put(ShufflepodDatabase.KEY_ALIASES, TextUtils.join("\n", person.getAliases()));
        values.put(ShufflepodDatabase.KEY_IN_POOL, person.isInPool() ? 1 : 0);
        values.put(ShufflepodDatabase.KEY_SINCE, person.getSince());
        values.put(ShufflepodDatabase.KEY_ADDED_AT, addedAt);
        Shufflepod.write(db -> db.insertWithOnConflict(ShufflepodDatabase.TABLE_PERSON, null, values,
                SQLiteDatabase.CONFLICT_REPLACE));
    }

    public static synchronized void remove(long personId) {
        persons.remove(personId);
        episodes.remove(personId);
        mutedShows.remove(personId);
        mutedEpisodes.remove(personId);
        final String[] args = {String.valueOf(personId)};
        Shufflepod.write(db -> {
            db.delete(ShufflepodDatabase.TABLE_PERSON, ShufflepodDatabase.KEY_ID + "=?", args);
            db.delete(ShufflepodDatabase.TABLE_PERSON_EPISODE, ShufflepodDatabase.KEY_PERSON_ID + "=?", args);
            db.delete(ShufflepodDatabase.TABLE_PERSON_MUTED_SHOW, ShufflepodDatabase.KEY_PERSON_ID + "=?", args);
            db.delete(ShufflepodDatabase.TABLE_PERSON_MUTED_EPISODE, ShufflepodDatabase.KEY_PERSON_ID + "=?", args);
        });
    }

    // ---- episodes ----

    /**
     * The item IDs in a person's folder (unordered).
     */
    public static synchronized List<Long> getItemIds(long personId) {
        Map<String, Long> map = episodes.get(personId);
        return map == null ? new ArrayList<>() : new ArrayList<>(map.values());
    }

    public static synchronized int getEpisodeCount(long personId) {
        Map<String, Long> map = episodes.get(personId);
        return map == null ? 0 : map.size();
    }

    /**
     * Adds an episode to a person's folder unless it or its show is muted for them, or it's already there.
     *
     * @return true if it was added
     */
    public static synchronized boolean addEpisode(long personId, FeedItem item, String source) {
        if (!persons.containsKey(personId)) {
            return false;
        }
        final String feedKey = EpisodeKeys.feedKey(item);
        final String episodeKey = EpisodeKeys.episodeKey(item);
        String key = key(feedKey, episodeKey);
        if (isShowMuted(personId, feedKey) || mutedEpisodesOf(personId).contains(key)
                || episodesOf(personId).containsKey(key)) {
            return false;
        }
        episodesOf(personId).put(key, item.getId());
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_PERSON_ID, personId);
        values.put(ShufflepodDatabase.KEY_ITEM_ID, item.getId());
        values.put(ShufflepodDatabase.KEY_FEED_URL, feedKey);
        values.put(ShufflepodDatabase.KEY_EPISODE_KEY, episodeKey);
        values.put(ShufflepodDatabase.KEY_SOURCE, source);
        values.put(ShufflepodDatabase.KEY_ADDED_AT, System.currentTimeMillis());
        Shufflepod.write(db -> db.insertWithOnConflict(ShufflepodDatabase.TABLE_PERSON_EPISODE, null, values,
                SQLiteDatabase.CONFLICT_IGNORE));
        return true;
    }

    /**
     * Drops an episode from a person's folder (e.g. when AntennaPod no longer has it) without muting it.
     */
    public static synchronized void forgetItem(long personId, long itemId) {
        Map<String, Long> map = episodes.get(personId);
        if (map == null) {
            return;
        }
        String found = null;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (entry.getValue() == itemId) {
                found = entry.getKey();
                break;
            }
        }
        if (found == null) {
            return;
        }
        map.remove(found);
        final String[] args = {String.valueOf(personId), String.valueOf(itemId)};
        Shufflepod.write(db -> db.delete(ShufflepodDatabase.TABLE_PERSON_EPISODE,
                ShufflepodDatabase.KEY_PERSON_ID + "=? AND " + ShufflepodDatabase.KEY_ITEM_ID + "=?", args));
    }

    /**
     * True if any followed person has an episode of this show in their folder, so it must be kept even
     * though it isn't subscribed.
     */
    public static synchronized boolean isFeedReferenced(String feedKey) {
        String prefix = feedKey + "\n";
        for (Map<String, Long> map : episodes.values()) {
            for (String key : map.keySet()) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- muting ----

    public static synchronized boolean isShowMuted(long personId, String feedKey) {
        Map<String, String> map = mutedShows.get(personId);
        return map != null && map.containsKey(feedKey);
    }

    /**
     * feedKey -> show title of the shows muted for a person.
     */
    public static synchronized Map<String, String> getMutedShows(long personId) {
        Map<String, String> map = mutedShows.get(personId);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    /**
     * Mutes a show for a person: its episodes leave their folder and are not collected again.
     */
    public static synchronized void muteShow(long personId, final String feedKey, final String title) {
        mutedShowsOf(personId).put(feedKey, title);
        Map<String, Long> map = episodes.get(personId);
        if (map != null) {
            Iterator<String> it = map.keySet().iterator();
            while (it.hasNext()) {
                if (it.next().startsWith(feedKey + "\n")) {
                    it.remove();
                }
            }
        }
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_PERSON_ID, personId);
        values.put(ShufflepodDatabase.KEY_FEED_URL, feedKey);
        values.put(ShufflepodDatabase.KEY_TITLE, title);
        final String[] args = {String.valueOf(personId), feedKey};
        Shufflepod.write(db -> {
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_PERSON_MUTED_SHOW, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            db.delete(ShufflepodDatabase.TABLE_PERSON_EPISODE, ShufflepodDatabase.KEY_PERSON_ID + "=? AND "
                    + ShufflepodDatabase.KEY_FEED_URL + "=?", args);
        });
    }

    public static synchronized void unmuteShow(long personId, String feedKey) {
        Map<String, String> map = mutedShows.get(personId);
        if (map == null || map.remove(feedKey) == null) {
            return;
        }
        final String[] args = {String.valueOf(personId), feedKey};
        Shufflepod.write(db -> db.delete(ShufflepodDatabase.TABLE_PERSON_MUTED_SHOW,
                ShufflepodDatabase.KEY_PERSON_ID + "=? AND " + ShufflepodDatabase.KEY_FEED_URL + "=?", args));
    }

    /**
     * Removes one episode from a person's folder for good.
     */
    public static synchronized void muteEpisode(long personId, FeedItem item) {
        final String feedKey = EpisodeKeys.feedKey(item);
        final String episodeKey = EpisodeKeys.episodeKey(item);
        String key = key(feedKey, episodeKey);
        mutedEpisodesOf(personId).add(key);
        Map<String, Long> map = episodes.get(personId);
        if (map != null) {
            map.remove(key);
        }
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_PERSON_ID, personId);
        values.put(ShufflepodDatabase.KEY_FEED_URL, feedKey);
        values.put(ShufflepodDatabase.KEY_EPISODE_KEY, episodeKey);
        final String[] args = {String.valueOf(personId), feedKey, episodeKey};
        Shufflepod.write(db -> {
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_PERSON_MUTED_EPISODE, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
            db.delete(ShufflepodDatabase.TABLE_PERSON_EPISODE, ShufflepodDatabase.KEY_PERSON_ID + "=? AND "
                    + ShufflepodDatabase.KEY_FEED_URL + "=? AND " + ShufflepodDatabase.KEY_EPISODE_KEY + "=?", args);
        });
    }

    // ---- Podcast Index credentials ----

    public static synchronized String getPodcastIndexKey() {
        String value = settings.get(SETTING_PI_KEY);
        return value != null ? value : "";
    }

    public static synchronized String getPodcastIndexSecret() {
        String value = settings.get(SETTING_PI_SECRET);
        return value != null ? value : "";
    }

    public static synchronized boolean hasPodcastIndexCredentials() {
        return !getPodcastIndexKey().isEmpty() && !getPodcastIndexSecret().isEmpty();
    }

    public static synchronized void setPodcastIndexCredentials(String key, String secret) {
        putSetting(SETTING_PI_KEY, key.trim());
        putSetting(SETTING_PI_SECRET, secret.trim());
    }

    private static void putSetting(String name, String value) {
        settings.put(name, value);
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_NAME, name);
        values.put(ShufflepodDatabase.KEY_VALUE, value);
        Shufflepod.write(db -> db.insertWithOnConflict(ShufflepodDatabase.TABLE_APP_SETTING, null, values,
                SQLiteDatabase.CONFLICT_REPLACE));
    }
}
