package de.danoeh.antennapod.shufflepod;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point of the fork's data layer. Call {@link #init(Context)} once at app start.
 */
public final class Shufflepod {
    private static ShufflepodDatabase database;
    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShufflepodWriter");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private Shufflepod() {
    }

    public static synchronized void init(Context context) {
        if (database != null) {
            return;
        }
        database = new ShufflepodDatabase(context);
        SQLiteDatabase db = database.getReadableDatabase();
        ShowSettings.load(db);
        ArchiveStore.load(db);
    }

    static synchronized boolean isInitialized() {
        return database != null;
    }

    interface DbWrite {
        void run(SQLiteDatabase db);
    }

    static synchronized void write(DbWrite write) {
        if (database == null) {
            return;
        }
        final ShufflepodDatabase db = database;
        writer.submit(() -> write.run(db.getWritableDatabase()));
    }
}
