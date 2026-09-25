package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Looks for new appearances of followed people every few hours.
 */
public class PeopleSyncWorker extends Worker {
    private static final String WORK_NAME = "shufflepod_people_sync";

    public PeopleSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(PeopleSyncWorker.class, 6, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        PeopleSync.syncAll(getApplicationContext());
        return Result.success();
    }
}
