package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.database.ShufflepodEntertainment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Asks how many of a show's next episodes to force, then adds them ahead of the Entertainment shuffle.
 */
public final class ForceNextDialog {
    private static final String TAG = "ForceNextDialog";
    private static final int[] COUNTS = {1, 2, 3, 5, 10};

    private ForceNextDialog() {
    }

    public static void show(Context context, long feedId, String feedTitle, long excludeItemId,
                            @Nullable Runnable onDone) {
        String[] labels = new String[COUNTS.length];
        for (int i = 0; i < COUNTS.length; i++) {
            labels[i] = context.getResources().getQuantityString(
                    R.plurals.shufflepod_play_next_count, COUNTS[i], COUNTS[i]);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.shufflepod_play_next_title, feedTitle))
                .setItems(labels, (dialog, which) -> force(context, feedId, feedTitle, COUNTS[which],
                        excludeItemId, onDone))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void force(Context context, long feedId, String feedTitle, int count, long excludeItemId,
                              @Nullable Runnable onDone) {
        Context appContext = context.getApplicationContext();
        Observable.fromCallable(() -> ShufflepodEntertainment.forceNext(feedId, count, excludeItemId).size())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(added -> {
                    String message = added > 0
                            ? appContext.getResources().getQuantityString(
                                    R.plurals.shufflepod_forced_added, added, added, feedTitle)
                            : appContext.getString(R.string.shufflepod_forced_none, feedTitle);
                    EventBus.getDefault().post(new MessageEvent(message));
                    if (onDone != null) {
                        onDone.run();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
