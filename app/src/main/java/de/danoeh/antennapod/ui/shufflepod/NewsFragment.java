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
import de.danoeh.antennapod.shufflepod.ShowSettings;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.ShufflepodShowTags;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The News tab: the shows whose new episodes go to the queue.
 */
public class NewsFragment extends Fragment {
    public static final String TAG = "NewsFragment";

    private final NewsAdapter adapter = new NewsAdapter();
    private TextView summary;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_entertainment_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.shufflepod_news_label);
        toolbar.inflateMenu(R.menu.shufflepod_tag_list);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.shufflepod_add_shows_item) {
                ShowPicker.show(requireContext(), R.string.shufflepod_add_to_news_title, ShowTags.NEWS, false,
                        this::load);
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
        disposable = Observable.fromCallable(() -> ShufflepodShowTags.getFeeds(ShowTags.NEWS))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feeds -> {
                    adapter.setFeeds(feeds);
                    summary.setText(feeds.isEmpty() ? getString(R.string.shufflepod_news_empty)
                            : getString(R.string.shufflepod_news_intro));
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
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
            checkBox.setClickable(true);
            checkBox.setFocusable(true);
            itemView.findViewById(R.id.playNextButton).setVisibility(View.GONE);
        }
    }

    private class NewsAdapter extends RecyclerView.Adapter<Holder> {
        private final List<Feed> feeds = new ArrayList<>();

        void setFeeds(List<Feed> newFeeds) {
            feeds.clear();
            feeds.addAll(newFeeds);
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
            Feed feed = feeds.get(position);
            holder.title.setText(feed.getTitle());
            holder.subtitle.setText(ShowSettings.getQueuePosition(feed) == ShowSettings.QueuePosition.TOP
                    ? R.string.shufflepod_queue_position_top : R.string.shufflepod_queue_position_bottom);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(ShowTags.isNews(feed.getPreferences()));
            holder.checkBox.setOnCheckedChangeListener((button, checked) ->
                    ShufflepodShowTags.setTag(feed, ShowTags.NEWS, checked));
            holder.itemView.setOnClickListener(v -> ((MainActivity) requireActivity())
                    .loadChildFragment(FeedItemlistFragment.newInstance(feed.getId())));
            Glide.with(holder.itemView)
                    .load(feed.getImageUrl())
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .fitCenter()
                            .dontAnimate())
                    .into(holder.cover);
        }

        @Override
        public int getItemCount() {
            return feeds.size();
        }
    }
}
