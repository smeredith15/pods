package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.ShufflepodShowTags;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * A searchable checklist of all podcasts (and optionally followed people) for adding to or removing
 * from News or Entertainment. Current members start ticked.
 */
public final class ShowPicker {
    private static final String TAG = "ShowPicker";

    private ShowPicker() {
    }

    public static void show(Context context, @StringRes int title, String tag, boolean includePeople,
                            @Nullable Runnable onDone) {
        Observable.fromCallable(() -> {
            List<Entry> entries = new ArrayList<>();
            if (includePeople) {
                for (Person person : People.getPeople()) {
                    entries.add(new Entry(null, person, person.getName(), person.isInPool()));
                }
            }
            List<Entry> shows = new ArrayList<>();
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                    shows.add(new Entry(feed, null, feed.getTitle() != null ? feed.getTitle() : "",
                            ShowTags.has(feed.getPreferences(), tag)));
                }
            }
            Collections.sort(shows, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label));
            entries.addAll(shows);
            return entries;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> showDialog(context, title, tag, entries, onDone),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static void showDialog(Context context, @StringRes int title, String tag, List<Entry> entries,
                                   @Nullable Runnable onDone) {
        View view = LayoutInflater.from(context).inflate(R.layout.shufflepod_picker_dialog, null);
        EditText searchInput = view.findViewById(R.id.searchInput);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        PickerAdapter adapter = new PickerAdapter(entries);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> apply(tag, entries, onDone))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void apply(String tag, List<Entry> entries, @Nullable Runnable onDone) {
        List<Future<?>> writes = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.checked == entry.initiallyChecked) {
                continue;
            }
            if (entry.person != null) {
                People.setInPool(entry.person.getId(), entry.checked);
            } else if (entry.feed != null) {
                writes.add(ShufflepodShowTags.setTag(entry.feed, tag, entry.checked));
            }
        }
        Completable.fromAction(() -> {
            for (Future<?> write : writes) {
                write.get();
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (onDone != null) {
                        onDone.run();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static class Entry {
        final Feed feed;
        final Person person;
        final String label;
        final boolean initiallyChecked;
        boolean checked;

        Entry(Feed feed, Person person, String label, boolean checked) {
            this.feed = feed;
            this.person = person;
            this.label = label;
            this.initiallyChecked = checked;
            this.checked = checked;
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;
        final CheckBox checkBox;

        Holder(View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            checkBox = itemView.findViewById(R.id.checkbox);
            itemView.findViewById(R.id.playNextButton).setVisibility(View.GONE);
            subtitle.setVisibility(View.GONE);
        }
    }

    private static class PickerAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Entry> all;
        private final List<Entry> shown = new ArrayList<>();

        PickerAdapter(List<Entry> all) {
            this.all = all;
            shown.addAll(all);
        }

        void filter(String query) {
            String needle = query.trim().toLowerCase(Locale.getDefault());
            shown.clear();
            for (Entry entry : all) {
                if (needle.isEmpty() || entry.label.toLowerCase(Locale.getDefault()).contains(needle)) {
                    shown.add(entry);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shufflepod_entertainment_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Entry entry = shown.get(position);
            holder.title.setText(entry.label);
            holder.checkBox.setChecked(entry.checked);
            holder.itemView.setOnClickListener(v -> {
                entry.checked = !entry.checked;
                holder.checkBox.setChecked(entry.checked);
            });
            if (entry.feed != null) {
                Glide.with(holder.itemView)
                        .load(entry.feed.getImageUrl())
                        .apply(new RequestOptions()
                                .placeholder(R.color.light_gray)
                                .fitCenter()
                                .dontAnimate())
                        .into(holder.cover);
            } else {
                Glide.with(holder.itemView).clear(holder.cover);
                holder.cover.setImageResource(R.drawable.ic_shufflepod_person);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
