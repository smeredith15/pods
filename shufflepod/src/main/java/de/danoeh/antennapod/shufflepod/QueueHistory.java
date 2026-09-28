package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshots of the queue's length over time, for the statistics chart. Kept for 90 days.
 */
public final class QueueHistory {
    private static final long KEEP_MS = 90L * 24 * 60 * 60 * 1000;

    private QueueHistory() {
    }

    public static final class Point {
        public final long time;
        public final int count;
        public final long durationMs;

        Point(long time, int count, long durationMs) {
            this.time = time;
            this.count = count;
            this.durationMs = durationMs;
        }
    }

    public static void record(long time, int count, long durationMs) {
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_TIME, time);
        values.put(ShufflepodDatabase.KEY_COUNT, count);
        values.put(ShufflepodDatabase.KEY_DURATION, durationMs);
        Shufflepod.write(db -> {
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_QUEUE_HISTORY, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            db.delete(ShufflepodDatabase.TABLE_QUEUE_HISTORY, ShufflepodDatabase.KEY_TIME + " < ?",
                    new String[] {String.valueOf(time - KEEP_MS)});
        });
    }

    /**
     * Snapshots since the given time, oldest first. Must be called off the main thread.
     */
    public static List<Point> load(long since) {
        return Shufflepod.read(db -> {
            List<Point> points = new ArrayList<>();
            try (Cursor c = db.query(ShufflepodDatabase.TABLE_QUEUE_HISTORY, new String[] {
                    ShufflepodDatabase.KEY_TIME, ShufflepodDatabase.KEY_COUNT, ShufflepodDatabase.KEY_DURATION},
                    ShufflepodDatabase.KEY_TIME + " >= ?", new String[] {String.valueOf(since)},
                    null, null, ShufflepodDatabase.KEY_TIME)) {
                while (c.moveToNext()) {
                    points.add(new Point(c.getLong(0), c.getInt(1), c.getLong(2)));
                }
            }
            return points;
        }, Collections.emptyList());
    }
}
