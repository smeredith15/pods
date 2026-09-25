package de.danoeh.antennapod.shufflepod;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shows whose feed says the series is finished ({@code <itunes:complete>Yes</itunes:complete>}), for the
 * Finished smart folder. The feed parser reports the tag while parsing and commits when the parse ends, so
 * a show whose feed drops the tag leaves the folder on its next refresh.
 */
public final class CompletedShows {
    private static final String SETTING = "completed_shows";

    private static final Set<String> completed = new HashSet<>();
    private static final Map<String, Boolean> pending = new HashMap<>();

    private CompletedShows() {
    }

    static synchronized void load(SQLiteDatabase db) {
        completed.clear();
        try (Cursor c = db.query(ShufflepodDatabase.TABLE_APP_SETTING, new String[]{ShufflepodDatabase.KEY_VALUE},
                ShufflepodDatabase.KEY_NAME + "=?", new String[]{SETTING}, null, null, null)) {
            if (c.moveToFirst() && c.getString(0) != null) {
                for (String url : c.getString(0).split("\n")) {
                    if (!url.isEmpty()) {
                        completed.add(url);
                    }
                }
            }
        }
    }

    public static synchronized boolean isComplete(String feedUrl) {
        return feedUrl != null && completed.contains(feedUrl);
    }

    /**
     * Called by the parser when it reads the channel's {@code itunes:complete} tag.
     */
    public static synchronized void onTagParsed(String feedUrl, String value) {
        if (feedUrl != null) {
            pending.put(feedUrl, value != null && "yes".equalsIgnoreCase(value.trim()));
        }
    }

    /**
     * Called by the parser when a feed was parsed completely.
     */
    public static synchronized void onFeedParsed(String feedUrl) {
        if (feedUrl == null) {
            return;
        }
        boolean complete = Boolean.TRUE.equals(pending.remove(feedUrl));
        boolean changed = complete ? completed.add(feedUrl) : completed.remove(feedUrl);
        if (!changed) {
            return;
        }
        final ContentValues values = new ContentValues();
        values.put(ShufflepodDatabase.KEY_NAME, SETTING);
        values.put(ShufflepodDatabase.KEY_VALUE, TextUtils.join("\n", completed));
        Shufflepod.write(db -> db.insertWithOnConflict(ShufflepodDatabase.TABLE_APP_SETTING, null, values,
                SQLiteDatabase.CONFLICT_REPLACE));
    }
}
