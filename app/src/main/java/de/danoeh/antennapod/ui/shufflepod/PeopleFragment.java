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

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The People tab: followed guests and hosts. Each opens their folder of episodes.
 */
public class PeopleFragment extends Fragment {
    public static final String TAG = "PeopleFragment";

    private final PeopleAdapter adapter = new PeopleAdapter();
    private TextView summary;
    private Disposable disposable;
    private Disposable scanDisposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_entertainment_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.shufflepod_people_label);
        toolbar.inflateMenu(R.menu.shufflepod_people);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.shufflepod_add_person_item) {
                PersonDialogs.showAdd(requireContext(), this::load);
                return true;
            } else if (item.getItemId() == R.id.shufflepod_people_refresh_item) {
                refreshAll();
                return true;
            } else if (item.getItemId() == R.id.shufflepod_podcast_index_item) {
                PersonDialogs.showPodcastIndexKey(requireContext());
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
        if (scanDisposable != null) {
            scanDisposable.dispose();
        }
    }

    private void refreshAll() {
        for (Person person : People.getPeople()) {
            PersonDialogs.runSync(requireContext(), person.getId(), this::load);
        }
    }

    private void load() {
        if (!isAdded()) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> {
            List<Row> rows = new ArrayList<>();
            for (Person person : People.getPeople()) {
                rows.add(new Row(person, People.getEpisodeCount(person.getId()),
                        ShufflepodPeople.eligibleCount(person.getId())));
            }
            return rows;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rows -> {
                    adapter.setRows(rows);
                    summary.setText(rows.isEmpty()
                            ? getString(R.string.shufflepod_people_empty_title) + "\n"
                                    + getString(R.string.shufflepod_people_empty_message)
                            : getString(R.string.shufflepod_people_empty_message));
                    scanNewEpisodes();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    /**
     * Looks for mentions in the episodes added since the last check, in the background,
     * and reloads the list if any were found.
     */
    private void scanNewEpisodes() {
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            return;
        }
        scanDisposable = Observable.fromCallable(ShufflepodPeople::scanNewEpisodes)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(added -> {
                    if (added > 0) {
                        load();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static class Row {
        final Person person;
        final int episodes;
        final int unplayed;

        Row(Person person, int episodes, int unplayed) {
            this.person = person;
            this.episodes = episodes;
            this.unplayed = unplayed;
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;

        Holder(View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            itemView.findViewById(R.id.playNextButton).setVisibility(View.GONE);
            CheckBox checkBox = itemView.findViewById(R.id.checkbox);
            checkBox.setVisibility(View.GONE);
        }
    }

    private class PeopleAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
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
            Row row = rows.get(position);
            holder.title.setText(row.person.getName());
            String counts = holder.itemView.getResources().getQuantityString(
                    R.plurals.shufflepod_person_episode_count, row.episodes, row.episodes)
                    + " · " + holder.itemView.getResources().getQuantityString(
                    R.plurals.shufflepod_entertainment_episodes_left, row.unplayed, row.unplayed);
            if (row.person.isInPool()) {
                counts += " · " + holder.itemView.getContext().getString(R.string.shufflepod_entertainment_label);
            }
            holder.subtitle.setText(counts);
            holder.cover.setImageResource(R.drawable.ic_shufflepod_person);
            holder.itemView.setOnClickListener(v -> ((MainActivity) requireActivity())
                    .loadChildFragment(PersonFragment.newInstance(row.person.getId())));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
