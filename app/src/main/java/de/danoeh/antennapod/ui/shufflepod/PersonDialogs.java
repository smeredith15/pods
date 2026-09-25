package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.EpisodeKeys;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.PersonMatcher;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Dialogs for following people: add/edit, Podcast Index key, muting shows, and running a search.
 */
public final class PersonDialogs {
    private static final String TAG = "PersonDialogs";

    private PersonDialogs() {
    }

    public static void showAdd(Context context, @Nullable Runnable onDone) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_person_dialog, null);
        EditText nameInput = view.findViewById(R.id.nameInput);
        EditText aliasesInput = view.findViewById(R.id.aliasesInput);
        CheckBox pastCheckbox = view.findViewById(R.id.pastCheckbox);
        CheckBox poolCheckbox = view.findViewById(R.id.poolCheckbox);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_add_person_label)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim().replaceAll("\\s+", " ");
                    if (name.isEmpty()) {
                        return;
                    }
                    Person person = People.add(name, PersonMatcher.parseAliases(aliasesInput.getText().toString()),
                            pastCheckbox.isChecked(), poolCheckbox.isChecked());
                    PeopleSyncWorker.schedule(context);
                    if (onDone != null) {
                        onDone.run();
                    }
                    runSync(context, person.getId(), onDone);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void showEdit(Context context, Person person, @Nullable Runnable onDone) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_person_dialog, null);
        EditText nameInput = view.findViewById(R.id.nameInput);
        final EditText aliasesInput = view.findViewById(R.id.aliasesInput);
        view.findViewById(R.id.pastCheckbox).setVisibility(View.GONE);
        view.findViewById(R.id.poolCheckbox).setVisibility(View.GONE);
        nameInput.setText(person.getName());
        aliasesInput.setText(TextUtils.join(", ", person.getAliases()));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_edit_person_label)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim().replaceAll("\\s+", " ");
                    if (name.isEmpty()) {
                        return;
                    }
                    People.rename(person.getId(), name, PersonMatcher.parseAliases(aliasesInput.getText().toString()));
                    if (onDone != null) {
                        onDone.run();
                    }
                    runSync(context, person.getId(), onDone);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void showPodcastIndexKey(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_podcast_index_dialog, null);
        EditText keyInput = view.findViewById(R.id.keyInput);
        EditText secretInput = view.findViewById(R.id.secretInput);
        keyInput.setText(People.getPodcastIndexKey());
        secretInput.setText(People.getPodcastIndexSecret());
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.shufflepod_podcast_index_label)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> People.setPodcastIndexCredentials(
                        keyInput.getText().toString(), secretInput.getText().toString()))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    /**
     * Lists the shows in a person's folder plus the muted ones; unticking a show mutes it for them.
     */
    public static void showShows(Context context, Person person, @Nullable Runnable onDone) {
        Observable.fromCallable(() -> {
            Map<String, String> shows = new LinkedHashMap<>();
            for (FeedItem item : ShufflepodPeople.getEpisodes(person.getId())) {
                String feedKey = EpisodeKeys.feedKey(item);
                if (!shows.containsKey(feedKey)) {
                    shows.put(feedKey, item.getFeed() != null ? item.getFeed().getTitle() : feedKey);
                }
            }
            for (Map.Entry<String, String> muted : People.getMutedShows(person.getId()).entrySet()) {
                if (!shows.containsKey(muted.getKey())) {
                    shows.put(muted.getKey(), muted.getValue() != null ? muted.getValue() : muted.getKey());
                }
            }
            return shows;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(shows -> {
                    if (shows.isEmpty()) {
                        EventBus.getDefault().post(new MessageEvent(
                                context.getString(R.string.shufflepod_person_shows_empty)));
                        return;
                    }
                    final List<String> keys = new ArrayList<>(shows.keySet());
                    final String[] titles = new String[keys.size()];
                    final boolean[] included = new boolean[keys.size()];
                    for (int i = 0; i < keys.size(); i++) {
                        titles[i] = shows.get(keys.get(i));
                        included[i] = !People.isShowMuted(person.getId(), keys.get(i));
                    }
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(context.getString(R.string.shufflepod_person_shows_title, person.getName()))
                            .setMultiChoiceItems(titles, included, (dialog, which, isChecked) ->
                                    included[which] = isChecked)
                            .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                                boolean unmutedAny = false;
                                for (int i = 0; i < keys.size(); i++) {
                                    boolean wasMuted = People.isShowMuted(person.getId(), keys.get(i));
                                    if (!included[i] && !wasMuted) {
                                        People.muteShow(person.getId(), keys.get(i), titles[i]);
                                    } else if (included[i] && wasMuted) {
                                        People.unmuteShow(person.getId(), keys.get(i));
                                        unmutedAny = true;
                                    }
                                }
                                if (onDone != null) {
                                    onDone.run();
                                }
                                if (unmutedAny) {
                                    runSync(context, person.getId(), onDone);
                                }
                            })
                            .setNegativeButton(R.string.cancel_label, null)
                            .show();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    /**
     * Searches for the person's appearances in the background and reports how many were found.
     */
    public static void runSync(Context context, long personId, @Nullable Runnable onDone) {
        final Context appContext = context.getApplicationContext();
        EventBus.getDefault().post(new MessageEvent(appContext.getString(R.string.shufflepod_person_searching)));
        Observable.fromCallable(() -> PeopleSync.syncPerson(appContext, personId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    String message = result.podcastIndexError != null
                            ? appContext.getString(R.string.shufflepod_person_search_failed, result.podcastIndexError)
                            : appContext.getResources().getQuantityString(R.plurals.shufflepod_person_found,
                                    result.total(), result.total());
                    EventBus.getDefault().post(new MessageEvent(message));
                    if (onDone != null) {
                        onDone.run();
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    EventBus.getDefault().post(new MessageEvent(appContext.getString(
                            R.string.shufflepod_person_search_failed, String.valueOf(error.getMessage()))));
                    if (onDone != null) {
                        onDone.run();
                    }
                });
    }
}
