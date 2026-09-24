package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;

/**
 * Episodes marked "archived": done without being heard. Separate from AntennaPod's played state.
 */
public final class ArchiveStore {
    /** Extra {@link FeedItemFilter} property that makes episode queries skip archived episodes. */
    public static final String FILTER_HIDE_ARCHIVED = "shufflepod_hide_archived";
    private static final String SETTING_SHOW_ARCHIVED = "show_archived";

    private static final Set<Long> archivedIds = new HashSet<>();
    private static boolean showArchived = false;

    private ArchiveStore() {
    }

    static synchronized void load(SQLiteDatabase db) {
        archivedIds.clear();
        try (Cursor cursor = db.query(ShufflepodDatabase.TABLE_ARCHIVED_EPISODE,
                new String[]{ShufflepodDatabase.KEY_ITEM_ID}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                archivedIds.add(cursor.getLong(0));
            }
        }
        try (Cursor cursor = db.query(ShufflepodDatabase.TABLE_APP_SETTING,
                new String[]{ShufflepodDatabase.KEY_VALUE}, ShufflepodDatabase.KEY_NAME + "=?",
                new String[]{SETTING_SHOW_ARCHIVED}, null, null, null)) {
            showArchived = cursor.moveToNext() && "true".equals(cursor.getString(0));
        }
    }

    public static synchronized boolean isArchived(FeedItem item) {
        return item != null && archivedIds.contains(item.getId());
    }

    public static synchronized boolean isArchived(long itemId) {
        return archivedIds.contains(itemId);
    }

    public static synchronized void archive(List<FeedItem> items) {
        final List<ContentValues> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (FeedItem item : items) {
            if (archivedIds.add(item.getId())) {
                ContentValues values = new ContentValues();
                values.put(ShufflepodDatabase.KEY_FEED_URL, EpisodeKeys.feedKey(item));
                values.put(ShufflepodDatabase.KEY_EPISODE_KEY, EpisodeKeys.episodeKey(item));
                values.put(ShufflepodDatabase.KEY_ITEM_ID, item.getId());
                values.put(ShufflepodDatabase.KEY_ARCHIVED_AT, now);
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
                    db.insertWithOnConflict(ShufflepodDatabase.TABLE_ARCHIVED_EPISODE, null, values,
                            SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    public static synchronized void unarchive(List<FeedItem> items) {
        final List<String[]> keys = new ArrayList<>();
        for (FeedItem item : items) {
            if (archivedIds.remove(item.getId())) {
                keys.add(new String[]{String.valueOf(item.getId()),
                        EpisodeKeys.feedKey(item), EpisodeKeys.episodeKey(item)});
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        Shufflepod.write(db -> {
            db.beginTransaction();
            try {
                for (String[] key : keys) {
                    db.delete(ShufflepodDatabase.TABLE_ARCHIVED_EPISODE,
                            ShufflepodDatabase.KEY_ITEM_ID + "=? OR (" + ShufflepodDatabase.KEY_FEED_URL + "=? AND "
                                    + ShufflepodDatabase.KEY_EPISODE_KEY + "=?)", key);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    public static synchronized boolean isShowArchived() {
        return showArchived;
    }

    public static synchronized void setShowArchived(boolean show) {
        showArchived = show;
        Shufflepod.write(db -> {
            ContentValues values = new ContentValues();
            values.put(ShufflepodDatabase.KEY_NAME, SETTING_SHOW_ARCHIVED);
            values.put(ShufflepodDatabase.KEY_VALUE, show ? "true" : "false");
            db.insertWithOnConflict(ShufflepodDatabase.TABLE_APP_SETTING, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    /**
     * Adds {@link #FILTER_HIDE_ARCHIVED} to a filter unless the user chose to show archived episodes.
     */
    public static FeedItemFilter hideArchivedUnlessShown(FeedItemFilter filter) {
        if (isShowArchived()) {
            return filter;
        }
        return new FeedItemFilter(filter != null ? filter : FeedItemFilter.unfiltered(), FILTER_HIDE_ARCHIVED);
    }

    /**
     * SQL condition that excludes archived episodes, or an empty string if the filter doesn't ask for it.
     *
     * @param itemIdColumn fully qualified column holding the episode ID, e.g. "FeedItems.id"
     */
    public static synchronized String sqlExclusion(FeedItemFilter filter, String itemIdColumn) {
        if (filter == null || archivedIds.isEmpty()
                || !Arrays.asList(filter.getValues()).contains(FILTER_HIDE_ARCHIVED)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" ").append(itemIdColumn).append(" NOT IN (");
        boolean first = true;
        for (long id : archivedIds) {
            if (!first) {
                sb.append(',');
            }
            sb.append(id);
            first = false;
        }
        return sb.append(") ").toString();
    }
}
