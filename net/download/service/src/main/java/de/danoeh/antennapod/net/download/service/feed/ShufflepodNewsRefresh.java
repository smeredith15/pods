package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * SHUFFLEPOD: refreshes only the News shows, often, so new episodes are already queued. The full refresh
 * of every show keeps its own (much slower) schedule. News feeds are small, so this also runs on mobile data.
 */
public final class ShufflepodNewsRefresh {
    public static final String EXTRA_NEWS_ONLY = "shufflepod_news_only";
    private static final String WORK_ID_PERIODIC = "shufflepod_news_refresh";
    private static final String WORK_ID_NOW = "shufflepod_news_refresh_now";
    private static final long INTERVAL_MINUTES = 30;

    private ShufflepodNewsRefresh() {
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FeedUpdateWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(connected())
                .setInputData(newsOnly())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_ID_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * Refreshes the News shows right away, showing the usual refresh spinner.
     */
    public static void runNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FeedUpdateWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE)
                .setConstraints(connected())
                .setInputData(newsOnly())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(WORK_ID_NOW, ExistingWorkPolicy.REPLACE, request);
    }

    private static Constraints connected() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    private static Data newsOnly() {
        return new Data.Builder()
                .putBoolean(EXTRA_NEWS_ONLY, true)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, true)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true)
                .build();
    }
}
