package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * Adding an episode to a show from an audio URL, and attaching audio to an episode that has none
 * (for example a history-only episode from the Pocket Casts import).
 */
public final class CustomEpisodeDialogs {
    private static final String TAG = "CustomEpisodeDialogs";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private CustomEpisodeDialogs() {
    }

    public static void showAddEpisode(Context context, Feed feed) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_custom_episode_dialog, null);
        EditText titleInput = view.findViewById(R.id.titleInput);
        EditText dateInput = view.findViewById(R.id.dateInput);
        EditText urlInput = view.findViewById(R.id.urlInput);
        dateInput.setText(new SimpleDateFormat(DATE_PATTERN, Locale.ROOT).format(new Date()));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_add_episode_from_url_label)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String url = urlInput.getText().toString().trim();
                    String title = titleInput.getText().toString().trim();
                    Date date = parseDate(dateInput.getText().toString());
                    if (!isValidUrl(url) || title.isEmpty() || date == null) {
                        showMessage(context, context.getString(R.string.shufflepod_custom_episode_invalid));
                        return;
                    }
                    FeedItem item = new FeedItem(0, title, "custom:" + UUID.randomUUID(), null, date,
                            FeedItem.UNPLAYED, feed);
                    item.setMedia(new FeedMedia(item, url, 0, guessMimeType(url)));
                    save(context, item);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void showAttachAudio(Context context, FeedItem item) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_custom_episode_dialog, null);
        view.findViewById(R.id.titleInput).setVisibility(View.GONE);
        view.findViewById(R.id.dateInput).setVisibility(View.GONE);
        EditText urlInput = view.findViewById(R.id.urlInput);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_attach_audio_label)
                .setMessage(item.getTitle())
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String url = urlInput.getText().toString().trim();
                    if (!isValidUrl(url) || item.hasMedia()) {
                        showMessage(context, context.getString(R.string.shufflepod_custom_episode_invalid));
                        return;
                    }
                    item.setMedia(new FeedMedia(item, url, 0, guessMimeType(url)));
                    save(context, item);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void save(Context context, FeedItem item) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                DBWriter.setFeedItem(item, false).get();
                showMessage(appContext, appContext.getString(R.string.shufflepod_custom_episode_saved));
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
                showMessage(appContext, String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private static void showMessage(Context context, String message) {
        EventBus.getDefault().post(new MessageEvent(message));
    }

    static boolean isValidUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://") || lower.startsWith("http://")) && url.length() > 10
                && !url.contains(" ");
    }

    static Date parseDate(String text) {
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.ROOT);
        format.setLenient(false);
        try {
            return format.parse(text.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    static String guessMimeType(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.endsWith(".m4a") || path.endsWith(".mp4") || path.endsWith(".aac")) {
            return "audio/mp4";
        } else if (path.endsWith(".ogg") || path.endsWith(".oga")) {
            return "audio/ogg";
        } else if (path.endsWith(".opus")) {
            return "audio/opus";
        } else if (path.endsWith(".wav")) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }
}
