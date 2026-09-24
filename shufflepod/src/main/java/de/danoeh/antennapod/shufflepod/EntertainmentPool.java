package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;

/**
 * The shows currently in the Entertainment pool. When the News queue runs out, the next episode
 * comes from one of these shows.
 */
public final class EntertainmentPool {
    private static final Map<String, Long> feedIdsByKey = new LinkedHashMap<>();

    private EntertainmentPool() {
    }

    static synchronized void load(SQLiteDatabase db) {
        feedIdsByKey.clear();
        try (Cursor cursor = db.query(ShufflepodDatabase.TABLE_ENTERTAINMENT_POOL,
                new String[]{ShufflepodDatabase.KEY_FEED_URL, ShufflepodDatabase.KEY_FEED_ID},
                null, null, null, null, ShufflepodDatabase.KEY_ADDED_AT)) {
            while (cursor.moveToNext()) {
                feedIdsByKey.put(cursor.getString(0), cursor.getLong(1));
            }
        }
    }

    public static synchronized boolean contains(Feed feed) {
        return feed != null && feedIdsByKey.containsKey(EpisodeKeys.feedKey(feed));
    }

    public static synchronized List<Long> getFeedIds() {
        return new ArrayList<>(feedIdsByKey.values());
    }

    public static synchronized boolean isEmpty() {
        return feedIdsByKey.isEmpty();
    }

    public static synchronized void add(Feed feed) {
        final String key = EpisodeKeys.feedKey(feed);
        final long feedId = feed.getId();
        final long now = System.currentTimeMillis();
        feedIdsByKey.put(key, feedId);
        Shufflepod.write(db -> {
            ContentValues values = new ContentValues();
            values.put(ShufflepodDatabase.KEY_FEED_URL, key);
            values.put(ShufflepodDatabase.KEY_FEED_ID, feedId);
            values.put(ShufflepodDatabase.KEY_ADDED_AT, now);
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_ENTERTAINMENT_POOL, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    public static synchronized void remove(Feed feed) {
        final String key = EpisodeKeys.feedKey(feed);
        if (feedIdsByKey.remove(key) == null) {
            return;
        }
        Shufflepod.write(db -> db.delete(ShufflepodDatabase.TABLE_ENTERTAINMENT_POOL,
                ShufflepodDatabase.KEY_FEED_URL + "=?", new String[]{key}));
    }
}
