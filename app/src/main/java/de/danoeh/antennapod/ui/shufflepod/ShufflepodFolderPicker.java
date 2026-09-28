package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * "Folders" for one or more shows, replacing AntennaPod's tag editor: a checklist of News, Entertainment and
 * the other folders, plus "New folder". With several shows, a folder is ticked only if all of them are in it;
 * only folders whose tick is changed are added to or removed from all of them.
 */
public final class ShufflepodFolderPicker {
    private static final String TAG = "ShufflepodFolderPicker";

    private ShufflepodFolderPicker() {
    }

    public static void show(Context context, List<FeedPreferences> preferences) {
        if (preferences.isEmpty()) {
            return;
        }
        final List<FeedPreferences> selected = new ArrayList<>(preferences);
        Observable.fromCallable(ShufflepodFolderPicker::folderNames)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(names -> showDialog(context, selected, names),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public static void showForFeeds(Context context, List<Feed> feeds) {
        List<FeedPreferences> preferences = new ArrayList<>();
        for (Feed feed : feeds) {
            if (feed.getPreferences() != null) {
                preferences.add(feed.getPreferences());
            }
        }
        show(context, preferences);
    }

    private static List<String> folderNames() {
        TreeSet<String> others = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getPreferences() == null) {
                continue;
            }
            for (String tag : feed.getPreferences().getTags()) {
                if (!FeedPreferences.TAG_ROOT.equals(tag) && !ShowTags.NEWS.equals(tag)
                        && !ShowTags.ENTERTAINMENT.equals(tag)) {
                    others.add(tag);
                }
            }
        }
        List<String> names = new ArrayList<>();
        names.add(ShowTags.NEWS);
        names.add(ShowTags.ENTERTAINMENT);
        names.addAll(others);
        return names;
    }

    private static void showDialog(Context context, List<FeedPreferences> selected, List<String> names) {
        final String[] items = names.toArray(new String[0]);
        final boolean[] initial = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            initial[i] = true;
            for (FeedPreferences preferences : selected) {
                if (!preferences.getTags().contains(items[i])) {
                    initial[i] = false;
                    break;
                }
            }
        }
        final boolean[] checked = initial.clone();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_folders_label)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i] != initial[i]) {
                            setFolder(selected, items[i], checked[i]);
                        }
                    }
                    save(context, selected);
                })
                .setNeutralButton(R.string.shufflepod_new_folder, (dialog, which) -> newFolder(context, selected))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void newFolder(Context context, List<FeedPreferences> selected) {
        View view = LayoutInflater.from(context).inflate(R.layout.edit_text_dialog, null);
        EditText input = view.findViewById(R.id.textInput);
        TextInputLayout inputLayout = view.findViewById(R.id.textInputLayout);
        inputLayout.setHint(context.getString(R.string.shufflepod_folder_name_hint));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_new_folder)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty() && !FeedPreferences.TAG_ROOT.equals(name)) {
                        setFolder(selected, name, true);
                        save(context, selected);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void setFolder(List<FeedPreferences> selected, String folder, boolean in) {
        for (FeedPreferences preferences : selected) {
            if (in) {
                preferences.getTags().add(folder);
            } else {
                preferences.getTags().remove(folder);
            }
        }
    }

    private static void save(Context context, List<FeedPreferences> selected) {
        for (FeedPreferences preferences : selected) {
            DBWriter.setFeedPreferences(preferences);
        }
        if (selected.size() > 1) {
            EventBus.getDefault().post(new MessageEvent(context.getResources().getQuantityString(
                    R.plurals.updated_feeds_batch_label, selected.size(), selected.size())));
        }
    }
}
