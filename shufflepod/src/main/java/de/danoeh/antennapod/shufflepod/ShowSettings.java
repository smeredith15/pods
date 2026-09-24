package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;

/**
 * Per-show fork settings.
 */
public final class ShowSettings {

    /**
     * Where new episodes of a show go when they are added to the queue automatically.
     */
    public enum QueuePosition {
        BOTTOM, TOP
    }

    private static final Map<String, QueuePosition> queuePositions = new HashMap<>();

    private ShowSettings() {
    }

    static synchronized void load(SQLiteDatabase db) {
        queuePositions.clear();
        try (Cursor cursor = db.query(ShufflepodDatabase.TABLE_SHOW_SETTINGS,
                new String[]{ShufflepodDatabase.KEY_FEED_URL, ShufflepodDatabase.KEY_QUEUE_POSITION},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                queuePositions.put(cursor.getString(0), parse(cursor.getString(1)));
            }
        }
    }

    private static QueuePosition parse(String value) {
        try {
            return QueuePosition.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return QueuePosition.BOTTOM;
        }
    }

    public static synchronized QueuePosition getQueuePosition(Feed feed) {
        QueuePosition position = queuePositions.get(EpisodeKeys.feedKey(feed));
        return position != null ? position : QueuePosition.BOTTOM;
    }

    public static synchronized void setQueuePosition(Feed feed, QueuePosition position) {
        final String key = EpisodeKeys.feedKey(feed);
        queuePositions.put(key, position);
        Shufflepod.write(db -> {
            ContentValues values = new ContentValues();
            values.put(ShufflepodDatabase.KEY_FEED_URL, key);
            values.put(ShufflepodDatabase.KEY_QUEUE_POSITION, position.name());
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_SHOW_SETTINGS, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }
}
