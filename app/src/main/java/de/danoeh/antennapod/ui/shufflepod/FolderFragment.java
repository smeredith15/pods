package de.danoeh.antennapod.ui.shufflepod;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateUtils;
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
import de.danoeh.antennapod.shufflepod.ReleasePattern;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import de.danoeh.antennapod.storage.database.ShufflepodShowTags;
import de.danoeh.antennapod.storage.database.ShufflepodSmartFolders;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import de.danoeh.antennapod.ui.screen.feed.RenameFeedDialog;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The shows in one folder of the Podcasts page: a tag (shows can be in several), or a smart folder.
 */
public class FolderFragment extends Fragment {
    private static final String TAG = "FolderFragment";
    private static final String ARG_TAG = "tag";
    private static final String ARG_PATTERN = "pattern";

    private final FolderAdapter adapter = new FolderAdapter();
    private TextView summary;
    private Disposable disposable;
    private String tag;
    private int pattern;
    private List<Feed> feeds = new ArrayList<>();

    public static FolderFragment forTag(String tag) {
        FolderFragment fragment = new FolderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    public static FolderFragment forPattern(int pattern) {
        FolderFragment fragment = new FolderFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PATTERN, pattern);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        tag = requireArguments().getString(ARG_TAG);
        pattern = requireArguments().getInt(ARG_PATTERN, 0);
        View root = inflater.inflate(R.layout.shufflepod_entertainment_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        if (tag != null) {
            toolbar.setTitle(tag);
            toolbar.inflateMenu(R.menu.shufflepod_folder);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.shufflepod_add_shows_item) {
                    ShowPicker.show(requireContext(), getString(R.string.shufflepod_folder_shows_title, tag),
                            tag, false, this::load);
                    return true;
                } else if (item.getItemId() == R.id.rename_folder_item) {
                    new RenameFeedDialog(requireActivity(), tagItem()).show();
                    getParentFragmentManager().popBackStack();
                    return true;
                } else if (item.getItemId() == R.id.delete_folder_item) {
                    confirmDelete();
                    return true;
                }
                return false;
            });
        } else {
            toolbar.setTitle(pattern == ReleasePattern.DORMANT
                    ? R.string.shufflepod_folder_dormant : R.string.shufflepod_folder_seasonal);
        }
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

    private NavDrawerData.TagItem tagItem() {
        NavDrawerData.TagItem item = new NavDrawerData.TagItem(tag);
        for (Feed feed : feeds) {
            item.addFeed(feed, 0);
        }
        return item;
    }

    private void confirmDelete() {
        new ConfirmationDialog(requireContext(), R.string.delete_tag_label,
                getString(R.string.delete_tag_confirmation, tag)) {
            @Override
            public void onConfirmButtonPressed(DialogInterface dialog) {
                ShufflepodShowTags.setTag(new ArrayList<>(feeds), tag, false);
                getParentFragmentManager().popBackStack();
            }
        }.createNewDialog().show();
    }

    private void load() {
        if (!isAdded()) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> tag != null
                        ? ShufflepodShowTags.getFeeds(tag) : ShufflepodSmartFolders.getFeeds(pattern))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    feeds = result;
                    adapter.notifyDataSetChanged();
                    summary.setVisibility(result.isEmpty() || tag == null ? View.VISIBLE : View.GONE);
                    if (tag == null) {
                        String description = getString(pattern == ReleasePattern.DORMANT
                                ? R.string.shufflepod_folder_dormant_sum : R.string.shufflepod_folder_seasonal_sum);
                        summary.setText(result.isEmpty()
                                ? getString(R.string.shufflepod_smart_folder_empty) : description);
                    } else {
                        summary.setText(R.string.shufflepod_folder_empty);
                    }
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

    private class FolderAdapter extends RecyclerView.Adapter<Holder> {
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
            long lastRelease = ShufflepodSmartFolders.getLastRelease(feed.getId());
            if (tag == null && lastRelease > 0) {
                holder.subtitle.setText(getString(R.string.shufflepod_folder_last_release,
                        DateUtils.getRelativeTimeSpanString(lastRelease, System.currentTimeMillis(),
                                DateUtils.DAY_IN_MILLIS)));
            } else {
                holder.subtitle.setText(feed.getAuthor());
            }
            holder.checkBox.setOnCheckedChangeListener(null);
            if (tag != null) {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.checkBox.setChecked(ShowTags.has(feed.getPreferences(), tag));
                holder.checkBox.setOnCheckedChangeListener((button, checked) ->
                        ShufflepodShowTags.setTag(feed, tag, checked));
            } else {
                holder.checkBox.setVisibility(View.GONE);
            }
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
