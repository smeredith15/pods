package de.danoeh.antennapod.shufflepod;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy store of the Entertainment pool. The pool is now the set of shows tagged
 * {@link ShowTags#ENTERTAINMENT}; this is only read once to migrate old selections.
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

    /**
     * Feed IDs that were in the pool before it moved to the Entertainment tag.
     */
    public static synchronized List<Long> getFeedIds() {
        return new ArrayList<>(feedIdsByKey.values());
    }

    public static synchronized void clear() {
        if (feedIdsByKey.isEmpty()) {
            return;
        }
        feedIdsByKey.clear();
        Shufflepod.write(db -> db.delete(ShufflepodDatabase.TABLE_ENTERTAINMENT_POOL, null, null));
    }
}
