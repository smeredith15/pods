package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.FeedItem;

/**
 * Episodes the user forced to play next from Entertainment, in play order. They play after the News
 * queue and before the shuffle, and can be removed at any time.
 */
public final class ForcedEpisodes {
    private static final Set<Long> itemIds = new LinkedHashSet<>();

    private ForcedEpisodes() {
    }

    static synchronized void load(SQLiteDatabase db) {
        itemIds.clear();
        try (Cursor cursor = db.query(ShufflepodDatabase.TABLE_FORCED_EPISODE,
                new String[]{ShufflepodDatabase.KEY_ITEM_ID}, null, null, null, null, ShufflepodDatabase.KEY_ID)) {
            while (cursor.moveToNext()) {
                itemIds.add(cursor.getLong(0));
            }
        }
    }

    public static synchronized List<Long> getItemIds() {
        return new ArrayList<>(itemIds);
    }

    public static synchronized boolean contains(long itemId) {
        return itemIds.contains(itemId);
    }

    public static synchronized void add(List<FeedItem> items) {
        final List<ContentValues> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (FeedItem item : items) {
            if (itemIds.add(item.getId())) {
                ContentValues values = new ContentValues();
                values.put(ShufflepodDatabase.KEY_ITEM_ID, item.getId());
                values.put(ShufflepodDatabase.KEY_FEED_URL, EpisodeKeys.feedKey(item));
                values.put(ShufflepodDatabase.KEY_EPISODE_KEY, EpisodeKeys.episodeKey(item));
                values.put(ShufflepodDatabase.KEY_ADDED_AT, now);
                rows.add(values);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        Shufflepod.write(db -> {
            db.beginTransaction();
            try {
                for (ContentValues values : rows) {
                    db.insertWithOnConflict(ShufflepodDatabase.TABLE_FORCED_EPISODE, null, values,
                            SQLiteDatabase.CONFLICT_IGNORE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    public static synchronized void remove(long itemId) {
        if (!itemIds.remove(itemId)) {
            return;
        }
        final String id = String.valueOf(itemId);
        Shufflepod.write(db -> db.delete(ShufflepodDatabase.TABLE_FORCED_EPISODE,
                ShufflepodDatabase.KEY_ITEM_ID + "=?", new String[]{id}));
    }
}
