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
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.shufflepod.EntertainmentPool;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.ShufflepodEntertainment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The Entertainment tab: choose which shows are in the Entertainment pool.
 */
public class EntertainmentFragment extends Fragment {
    public static final String TAG = "EntertainmentFragment";

    private final PoolAdapter adapter = new PoolAdapter();
    private TextView summary;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_entertainment_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
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
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> {
            List<Row> rows = new ArrayList<>();
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() == Feed.STATE_SUBSCRIBED && !feed.isLocalFeed()) {
                    rows.add(new Row(feed, ShufflepodEntertainment.eligibleCount(feed.getId())));
                }
            }
            Collections.sort(rows, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a.feed.getTitle() != null ? a.feed.getTitle() : "",
                    b.feed.getTitle() != null ? b.feed.getTitle() : ""));
            return rows;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rows -> {
                    adapter.setRows(rows);
                    updateSummary();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void updateSummary() {
        int size = EntertainmentPool.getFeedIds().size();
        if (size == 0) {
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
        }
    }

    private class PoolAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shufflepod_entertainment_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            holder.title.setText(row.feed.getTitle());
            holder.subtitle.setText(row.episodesLeft > 0
                    ? holder.itemView.getResources().getQuantityString(
                            R.plurals.shufflepod_entertainment_episodes_left, row.episodesLeft, row.episodesLeft)
                    : holder.itemView.getContext().getString(R.string.shufflepod_entertainment_none_left));
            holder.checkBox.setChecked(EntertainmentPool.contains(row.feed));
            holder.itemView.setOnClickListener(v -> {
                if (EntertainmentPool.contains(row.feed)) {
                    EntertainmentPool.remove(row.feed);
                } else {
                    EntertainmentPool.add(row.feed);
                }
                holder.checkBox.setChecked(EntertainmentPool.contains(row.feed));
                updateSummary();
            });
            Glide.with(holder.itemView)
                    .load(row.feed.getImageUrl())
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .fitCenter()
                            .dontAnimate())
                    .into(holder.cover);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
