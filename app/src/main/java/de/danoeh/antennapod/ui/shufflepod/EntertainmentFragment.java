package de.danoeh.antennapod.ui.shufflepod;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.ForcedEpisodes;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.ShufflepodEntertainment;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import de.danoeh.antennapod.storage.database.ShufflepodShowTags;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The Entertainment tab: the shows (tagged Entertainment) and people in the pool, plus forced episodes.
 */
public class EntertainmentFragment extends Fragment {
    public static final String TAG = "EntertainmentFragment";
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FORCED = 1;
    private static final int TYPE_SHOW = 2;

    private final PoolAdapter adapter = new PoolAdapter();
    private TextView summary;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_entertainment_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.shufflepod_tag_list);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.shufflepod_add_shows_item) {
                ShowPicker.show(requireContext(), R.string.shufflepod_add_to_entertainment_title,
                        ShowTags.ENTERTAINMENT, true, this::load);
                return true;
            }
            return false;
        });
        boolean displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        summary = root.findViewById(R.id.summary);
        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void load() {
        if (!isAdded()) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        final String forcedHeader = getString(R.string.shufflepod_forced_section);
        final String showsHeader = getString(R.string.shufflepod_shows_section);
        final String peopleHeader = getString(R.string.shufflepod_people_section);
        disposable = Observable.fromCallable(() -> {
            List<Row> rows = new ArrayList<>();
            for (Feed feed : ShufflepodShowTags.getFeeds(ShowTags.ENTERTAINMENT)) {
                rows.add(new Row(feed, ShufflepodEntertainment.eligibleCount(feed.getId())));
            }
            List<Object> entries = new ArrayList<>();
            List<FeedItem> forced = ShufflepodEntertainment.forcedEpisodes();
            if (!forced.isEmpty()) {
                entries.add(forcedHeader);
                entries.addAll(forced);
            }
            List<Person> people = People.getPooledPeople();
            if (!people.isEmpty()) {
                entries.add(peopleHeader);
                for (Person person : people) {
                    entries.add(new PersonRow(person.getId(), person.getName(),
                            ShufflepodPeople.eligibleCount(person.getId())));
                }
            }
            if (!rows.isEmpty()) {
                entries.add(showsHeader);
                entries.addAll(rows);
            }
            return entries;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> {
                    adapter.setEntries(entries);
                    updateSummary(entries.isEmpty());
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void updateSummary(boolean empty) {
        int size = adapter.countRows();
        if (empty) {
            summary.setText(R.string.shufflepod_entertainment_empty);
        } else if (size == 0) {
            summary.setText(R.string.shufflepod_entertainment_intro);
        } else {
            summary.setText(getString(R.string.shufflepod_entertainment_intro) + "\n"
                    + getResources().getQuantityString(R.plurals.shufflepod_entertainment_pool_size, size, size));
        }
    }

    private static class Row {
        final Feed feed;
        final int episodesLeft;

        Row(Feed feed, int episodesLeft) {
            this.feed = feed;
            this.episodesLeft = episodesLeft;
        }
    }

    private static class PersonRow {
        final long personId;
        final String name;
        final int episodesLeft;

        PersonRow(long personId, String name, int episodesLeft) {
            this.personId = personId;
            this.name = name;
            this.episodesLeft = episodesLeft;
        }
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView header;

        HeaderHolder(View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.header);
        }
    }

    private static class ForcedHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final View removeButton;

        ForcedHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
    }

    private static class ShowHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;
        final View playNextButton;
        final CheckBox checkBox;

        ShowHolder(View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            playNextButton = itemView.findViewById(R.id.playNextButton);
            checkBox = itemView.findViewById(R.id.checkbox);
            checkBox.setClickable(true);
            checkBox.setFocusable(true);
        }
    }

    private class PoolAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Object> entries = new ArrayList<>();

        int countRows() {
            int count = 0;
            for (Object entry : entries) {
                if (entry instanceof Row || entry instanceof PersonRow) {
                    count++;
                }
            }
            return count;
        }

        void setEntries(List<Object> newEntries) {
            entries.clear();
            entries.addAll(newEntries);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object entry = entries.get(position);
            if (entry instanceof String) {
                return TYPE_HEADER;
            } else if (entry instanceof FeedItem) {
                return TYPE_FORCED;
            }
            return TYPE_SHOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.shufflepod_entertainment_header, parent, false));
            } else if (viewType == TYPE_FORCED) {
                return new ForcedHolder(inflater.inflate(R.layout.shufflepod_forced_item, parent, false));
            }
            return new ShowHolder(inflater.inflate(R.layout.shufflepod_entertainment_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object entry = entries.get(position);
            if (holder instanceof HeaderHolder && entry instanceof String) {
                ((HeaderHolder) holder).header.setText((String) entry);
            } else if (holder instanceof ForcedHolder && entry instanceof FeedItem) {
                bindForced((ForcedHolder) holder, (FeedItem) entry);
            } else if (holder instanceof ShowHolder && entry instanceof Row) {
                bindShow((ShowHolder) holder, (Row) entry);
            } else if (holder instanceof ShowHolder && entry instanceof PersonRow) {
                bindPerson((ShowHolder) holder, (PersonRow) entry);
            }
        }

        private void bindForced(ForcedHolder holder, FeedItem item) {
            holder.title.setText(item.getTitle());
            holder.subtitle.setText(item.getFeed() != null ? item.getFeed().getTitle() : "");
            holder.removeButton.setOnClickListener(v -> {
                ForcedEpisodes.remove(item.getId());
                load();
            });
        }

        private void bindShow(ShowHolder holder, Row row) {
            holder.title.setText(row.feed.getTitle());
            holder.playNextButton.setVisibility(View.VISIBLE);
            holder.subtitle.setText(row.episodesLeft > 0
                    ? holder.itemView.getResources().getQuantityString(
                            R.plurals.shufflepod_entertainment_episodes_left, row.episodesLeft, row.episodesLeft)
                    : holder.itemView.getContext().getString(R.string.shufflepod_entertainment_none_left));
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(ShowTags.has(row.feed.getPreferences(), ShowTags.ENTERTAINMENT));
            holder.checkBox.setOnCheckedChangeListener((button, checked) ->
                    ShufflepodShowTags.setTag(row.feed, ShowTags.ENTERTAINMENT, checked));
            holder.itemView.setOnClickListener(v -> ((MainActivity) requireActivity())
                    .loadChildFragment(FeedItemlistFragment.newInstance(row.feed.getId())));
            holder.playNextButton.setOnClickListener(v -> ForceNextDialog.show(requireContext(),
                    row.feed.getId(), row.feed.getTitle(), -1, EntertainmentFragment.this::load));
            Glide.with(holder.itemView)
                    .load(row.feed.getImageUrl())
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .fitCenter()
                            .dontAnimate())
                    .into(holder.cover);
        }

        private void bindPerson(ShowHolder holder, PersonRow row) {
            holder.title.setText(row.name);
            holder.subtitle.setText(row.episodesLeft > 0
                    ? holder.itemView.getResources().getQuantityString(
                            R.plurals.shufflepod_entertainment_episodes_left, row.episodesLeft, row.episodesLeft)
                    : holder.itemView.getContext().getString(R.string.shufflepod_entertainment_none_left));
            holder.playNextButton.setVisibility(View.GONE);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(isPersonInPool(row.personId));
            holder.checkBox.setOnCheckedChangeListener((button, checked) -> People.setInPool(row.personId, checked));
            holder.itemView.setOnClickListener(v -> ((MainActivity) requireActivity())
                    .loadChildFragment(PersonFragment.newInstance(row.personId)));
            Glide.with(holder.itemView).clear(holder.cover);
            holder.cover.setImageResource(R.drawable.ic_shufflepod_person);
        }

        private boolean isPersonInPool(long personId) {
            Person person = People.get(personId);
            return person != null && person.isInPool();
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }
}
