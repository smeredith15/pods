package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.danoeh.antennapod.R;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Asks for the Pocket Casts login, runs {@link PocketCastsImport} with a progress dialog, and shows a summary.
 */
public final class PocketCastsImportDialog {
    private static final String TAG = "PocketCastsImport";
    private static final int MAX_UNMATCHED_LISTED = 30;

    private PocketCastsImportDialog() {
    }

    public static void show(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_pocketcasts_dialog, null);
        EditText emailInput = view.findViewById(R.id.emailInput);
        EditText passwordInput = view.findViewById(R.id.passwordInput);
        EditText tokenInput = view.findViewById(R.id.tokenInput);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_pocketcasts_import_label)
                .setView(view)
                .setPositiveButton(R.string.shufflepod_pocketcasts_import_button, (dialog, which) -> {
                    String email = emailInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    String token = tokenInput.getText().toString();
                    if (!token.trim().isEmpty() || (!email.isEmpty() && !password.isEmpty())) {
                        runImport(context, email, password, token);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void runImport(Context context, String email, String password, String token) {
        final AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_pocketcasts_import_label)
                .setMessage(R.string.shufflepod_pocketcasts_signing_in)
                .setCancelable(false)
                .show();
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        final Handler main = new Handler(Looper.getMainLooper());
        final Context appContext = context.getApplicationContext();
        Observable.fromCallable(() -> PocketCastsImport.run(appContext, email, password, token,
                (done, total, title) -> main.post(() -> progressDialog.setMessage(context.getString(
                        R.string.shufflepod_pocketcasts_progress, Math.min(done + 1, total), total, title)))))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    progressDialog.dismiss();
                    showResult(context, result);
                }, error -> {
                    progressDialog.dismiss();
                    Log.e(TAG, Log.getStackTraceString(error));
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.shufflepod_pocketcasts_import_label)
                            .setMessage(context.getString(R.string.shufflepod_pocketcasts_failed,
                                    String.valueOf(error.getMessage())))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
    }

    private static void showResult(Context context, PocketCastsImport.Result result) {
        StringBuilder message = new StringBuilder(context.getString(R.string.shufflepod_pocketcasts_done_message,
                result.playedInPocketCasts, result.markedPlayed, result.archived, result.historyAdded,
                result.alreadyDone, result.notInFeed,
                result.showsMatched, result.showsUnmatched, result.showsFailed));
        if (!result.unmatchedTitles.isEmpty()) {
            List<String> listed = result.unmatchedTitles.subList(0,
                    Math.min(MAX_UNMATCHED_LISTED, result.unmatchedTitles.size()));
            message.append("\n\n").append(context.getString(R.string.shufflepod_pocketcasts_not_found_header))
                    .append('\n').append(TextUtils.join("\n", listed));
            if (result.unmatchedTitles.size() > listed.size()) {
                message.append("\n…");
            }
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_pocketcasts_done_title)
                .setMessage(message.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
