package de.danoeh.antennapod.shufflepod;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The fork's own database file. Kept separate from AntennaPod's database so upstream schema
 * migrations never collide with fork data.
 */
class ShufflepodDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "shufflepod.db";
    private static final int VERSION = 2;

    static final String TABLE_SHOW_SETTINGS = "show_settings";
    static final String KEY_FEED_URL = "feed_url";
    static final String KEY_QUEUE_POSITION = "queue_position";

    static final String TABLE_ARCHIVED_EPISODE = "archived_episode";
    static final String KEY_ITEM_ID = "item_id";
    static final String KEY_EPISODE_KEY = "episode_key";
    static final String KEY_ARCHIVED_AT = "archived_at";

    static final String TABLE_ENTERTAINMENT_POOL = "entertainment_pool";
    static final String KEY_FEED_ID = "feed_id";
    static final String KEY_ADDED_AT = "added_at";

    static final String TABLE_APP_SETTING = "app_setting";
    static final String KEY_NAME = "name";
    static final String KEY_VALUE = "value";

    ShufflepodDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SHOW_SETTINGS + " ("
                + KEY_FEED_URL + " TEXT PRIMARY KEY NOT NULL, "
                + KEY_QUEUE_POSITION + " TEXT NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_ARCHIVED_EPISODE + " ("
                + KEY_FEED_URL + " TEXT NOT NULL, "
                + KEY_EPISODE_KEY + " TEXT NOT NULL, "
                + KEY_ITEM_ID + " INTEGER NOT NULL, "
                + KEY_ARCHIVED_AT + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + KEY_FEED_URL + ", " + KEY_EPISODE_KEY + "))");
        db.execSQL("CREATE TABLE " + TABLE_APP_SETTING + " ("
                + KEY_NAME + " TEXT PRIMARY KEY NOT NULL, "
                + KEY_VALUE + " TEXT)");
        createEntertainmentPool(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createEntertainmentPool(db);
        }
    }

    private static void createEntertainmentPool(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ENTERTAINMENT_POOL + " ("
                + KEY_FEED_URL + " TEXT PRIMARY KEY NOT NULL, "
                + KEY_FEED_ID + " INTEGER NOT NULL, "
                + KEY_ADDED_AT + " INTEGER NOT NULL)");
    }
}
