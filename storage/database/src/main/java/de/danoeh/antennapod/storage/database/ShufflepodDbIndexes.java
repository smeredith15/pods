package de.danoeh.antennapod.storage.database;

import android.database.sqlite.SQLiteDatabase;

/**
 * SHUFFLEPOD: extra indexes on AntennaPod's episode table. With hundreds of shows, the Subscriptions
 * screen's per-show counters and "latest episode" sort otherwise read every episode row, descriptions
 * included. These let SQLite answer both from small covering indexes. No tables or columns are changed.
 */
final class ShufflepodDbIndexes {

    private ShufflepodDbIndexes() {
    }

    static void ensure(SQLiteDatabase db) {
        if (db.isReadOnly()) {
            return;
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS shufflepod_FeedItems_feed_read ON "
                + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " (" + PodDBAdapter.KEY_FEED + ", "
                + PodDBAdapter.KEY_READ + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS shufflepod_FeedItems_feed_pubdate ON "
                + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " (" + PodDBAdapter.KEY_FEED + ", "
                + PodDBAdapter.KEY_PUBDATE + ")");
    }
}
