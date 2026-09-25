package de.danoeh.antennapod.shufflepod;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point of the fork's data layer. Call {@link #init(Context)} once at app start.
 */
public final class Shufflepod {
    private static volatile ShufflepodDatabase database;
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
        ShufflepodDatabase created = new ShufflepodDatabase(context);
        SQLiteDatabase db = created.getReadableDatabase();
        ShowSettings.load(db);
        ArchiveStore.load(db);
        EntertainmentPool.load(db);
        ForcedEpisodes.load(db);
        People.load(db);
        CompletedShows.load(db);
        database = created;
    }

    interface DbWrite {
        void run(SQLiteDatabase db);
    }

    /**
     * Persists a change on the background writer thread. Deliberately not synchronized, so callers
     * holding their own store lock never wait on this class's lock (init takes them in the other order).
     */
    static void write(DbWrite write) {
        final ShufflepodDatabase db = database;
        if (db == null) {
            return;
        }
        writer.submit(() -> write.run(db.getWritableDatabase()));
    }
}
