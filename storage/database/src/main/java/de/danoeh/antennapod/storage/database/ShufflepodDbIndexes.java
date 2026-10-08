package de.danoeh.antennapod.storage.database;

import android.database.sqlite.SQLiteDatabase;

/**
 * SHUFFLEPOD: extra indexes on AntennaPod's episode table. With hundreds of shows, the Subscriptions
 * screen's per-show counters and "latest episode" sort otherwise read every episode row, descriptions
 * included. These let SQLite answer both from small covering indexes. No tables or columns are changed.
 * The lookup indexes (episode GUID and audio URL) are built off the main thread, the first time they're needed.
 */
final class ShufflepodDbIndexes {
    private static volatile boolean lookupIndexesReady = false;

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

    /**
     * Indexes for finding an episode by GUID or audio URL ({@link DBReader#getFeedItemByGuidOrEpisodeUrl}), which
     * otherwise reads the whole episode table. Takes a few seconds the first time. Must be called off the main thread.
     */
    static void ensureLookupIndexes() {
        if (lookupIndexesReady) {
            return;
        }
        synchronized (ShufflepodDbIndexes.class) {
            if (lookupIndexesReady) {
                return;
            }
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            try {
                adapter.shufflepodExec("CREATE INDEX IF NOT EXISTS shufflepod_FeedItems_item_identifier ON "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " (" + PodDBAdapter.KEY_ITEM_IDENTIFIER + ")");
                adapter.shufflepodExec("CREATE INDEX IF NOT EXISTS shufflepod_FeedMedia_download_url ON "
                        + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " (" + PodDBAdapter.KEY_DOWNLOAD_URL + ")");
            } finally {
                adapter.close();
            }
            lookupIndexesReady = true;
        }
    }
}
