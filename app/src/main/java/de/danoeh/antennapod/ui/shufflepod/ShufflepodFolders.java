package de.danoeh.antennapod.ui.shufflepod;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.shufflepod.ReleasePattern;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import de.danoeh.antennapod.storage.database.ShufflepodSmartFolders;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The folders at the top of the Podcasts page, above all the shows: News and Entertainment first, then the
 * other tags (a show can be in several), then the smart folders and "New folder". Folders and shows scroll
 * as one list, with a thin line between them.
 */
public class ShufflepodFolders {
    private static final String TAG = "ShufflepodFolders";

    private final Fragment fragment;
    private final RowAdapter rowAdapter = new RowAdapter();
    private final SeparatorAdapter separatorAdapter = new SeparatorAdapter();
    private final List<Row> tagRows = new ArrayList<>();
    private final List<Row> smartRows = new ArrayList<>();
    private boolean enabled = false;
    private Disposable smartDisposable;

    public ShufflepodFolders(Fragment fragment) {
        this.fragment = fragment;
    }

    public RecyclerView.Adapter<?> wrap(RecyclerView.Adapter<?> feeds, int feedState) {
        enabled = feedState == Feed.STATE_SUBSCRIBED;
        if (!enabled) {
            return feeds;
        }
        return new ConcatAdapter(rowAdapter, separatorAdapter, feeds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Folder rows and the separator take the full width in the grid layouts.
     */
    public void applySpans(RecyclerView.LayoutManager layoutManager, int feedState) {
        if (feedState != Feed.STATE_SUBSCRIBED || !(layoutManager instanceof GridLayoutManager)) {
            return;
        }
        GridLayoutManager grid = (GridLayoutManager) layoutManager;
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position < rowAdapter.getItemCount() + separatorAdapter.getItemCount()
                        ? grid.getSpanCount() : 1;
            }
        });
    }

    public void setTags(List<NavDrawerData.TagItem> tags) {
        if (!enabled) {
            return;
        }
        int newsCount = 0;
        int entertainmentCount = 0;
        List<Row> others = new ArrayList<>();
        for (NavDrawerData.TagItem tag : tags) {
            String title = tag.getTitle();
            if (ShowTags.NEWS.equals(title)) {
                newsCount = tag.getFeeds().size();
            } else if (ShowTags.ENTERTAINMENT.equals(title)) {
                entertainmentCount = tag.getFeeds().size();
            } else if (!FeedPreferences.TAG_ROOT.equals(title) && !FeedPreferences.TAG_UNTAGGED.equals(title)) {
                others.add(Row.tag(title, R.drawable.ic_folder, tag.getFeeds().size()));
            }
        }
        tagRows.clear();
        tagRows.add(Row.tag(ShowTags.NEWS, R.drawable.ic_shufflepod_news, newsCount));
        tagRows.add(Row.tag(ShowTags.ENTERTAINMENT, R.drawable.ic_shuffle, entertainmentCount));
        tagRows.addAll(others);
        notifyChanged();
        loadSmartFolders();
    }

    public void dispose() {
        if (smartDisposable != null) {
            smartDisposable.dispose();
        }
    }

    private void loadSmartFolders() {
        if (smartDisposable != null && !smartDisposable.isDisposed()) {
            return;
        }
        smartDisposable = Observable.fromCallable(() -> new int[] {
            ShufflepodSmartFolders.count(ReleasePattern.DORMANT),
            ShufflepodSmartFolders.count(ReleasePattern.SEASONAL)})
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(counts -> {
                    smartRows.clear();
                    smartRows.add(Row.smart(ReleasePattern.DORMANT, R.string.shufflepod_folder_dormant,
                            R.drawable.ic_sleep, counts[0]));
                    smartRows.add(Row.smart(ReleasePattern.SEASONAL, R.string.shufflepod_folder_seasonal,
                            R.drawable.ic_refresh, counts[1]));
                    notifyChanged();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void notifyChanged() {
        rowAdapter.notifyDataSetChanged();
        separatorAdapter.notifyDataSetChanged();
    }

    private void open(Row row) {
        MainActivity activity = (MainActivity) fragment.requireActivity();
        if (row.pattern != 0) {
            activity.loadChildFragment(FolderFragment.forPattern(row.pattern));
        } else if (ShowTags.NEWS.equals(row.tag)) {
            activity.loadFragment(NewsFragment.TAG, null);
        } else if (ShowTags.ENTERTAINMENT.equals(row.tag)) {
            activity.loadFragment(EntertainmentFragment.TAG, null);
        } else if (row.tag != null) {
            activity.loadChildFragment(FolderFragment.forTag(row.tag));
        } else {
            createFolder();
        }
    }

    private void createFolder() {
        View view = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.edit_text_dialog, null);
        EditText input = view.findViewById(R.id.textInput);
        TextInputLayout inputLayout = view.findViewById(R.id.textInputLayout);
        inputLayout.setHint(fragment.getString(R.string.shufflepod_folder_name_hint));
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.shufflepod_new_folder)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        ShowPicker.show(fragment.requireContext(),
                                fragment.getString(R.string.shufflepod_folder_shows_title, name), name, false, null);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static class Row {
        final String tag;
        final int pattern;
        final int titleRes;
        @DrawableRes final int icon;
        final int count;

        Row(String tag, int pattern, int titleRes, int icon, int count) {
            this.tag = tag;
            this.pattern = pattern;
            this.titleRes = titleRes;
            this.icon = icon;
            this.count = count;
        }

        static Row tag(String tag, int icon, int count) {
            return new Row(tag, 0, 0, icon, count);
        }

        static Row smart(int pattern, int titleRes, int icon, int count) {
            return new Row(null, pattern, titleRes, icon, count);
        }
    }

    private static class RowHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;

        RowHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }

    private class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        private Row get(int position) {
            if (position < tagRows.size()) {
                return tagRows.get(position);
            }
            position -= tagRows.size();
            if (position < smartRows.size()) {
                return smartRows.get(position);
            }
            return new Row(null, 0, R.string.shufflepod_new_folder, R.drawable.ic_add, -1);
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shufflepod_folder_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            Row row = get(position);
            holder.icon.setImageResource(row.icon);
            if (row.tag != null) {
                holder.title.setText(row.tag);
            } else {
                holder.title.setText(row.titleRes);
            }
            if (row.count >= 0) {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(holder.itemView.getResources().getQuantityString(
                        R.plurals.shufflepod_folder_show_count, row.count, row.count));
            } else {
                holder.subtitle.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> open(row));
        }

        @Override
        public int getItemCount() {
            return tagRows.isEmpty() ? 0 : tagRows.size() + smartRows.size() + 1;
        }
    }

    private class SeparatorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.shufflepod_folder_separator, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return rowAdapter.getItemCount() > 0 ? 1 : 0;
        }
    }
}
