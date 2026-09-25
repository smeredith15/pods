package de.danoeh.antennapod.ui.shufflepod;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.shufflepod.EpisodeKeys;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment;

/**
 * A followed person's folder: every episode found for them, newest first, with the usual episode actions.
 */
public class PersonFragment extends EpisodesListFragment {
    public static final String TAG = "PersonFragment";
    private static final String ARG_PERSON_ID = "person_id";

    private long personId;

    public static PersonFragment newInstance(long personId) {
        PersonFragment fragment = new PersonFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PERSON_ID, personId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        personId = requireArguments().getLong(ARG_PERSON_ID);
        final View root = super.onCreateView(inflater, container, savedInstanceState);
        toolbar.inflateMenu(R.menu.shufflepod_person);
        emptyView.setIcon(R.drawable.ic_shufflepod_person);
        emptyView.setTitle(R.string.shufflepod_people_label);
        emptyView.setMessage(R.string.shufflepod_person_shows_empty);
        swipeRefreshLayout.setOnRefreshListener(() -> PersonDialogs.runSync(requireContext(), personId, () -> {
            swipeRefreshLayout.setRefreshing(false);
            loadItems();
        }));
        updateToolbar();
        return root;
    }

    @Override
    protected void updateToolbar() {
        Person person = People.get(personId);
        if (person == null || toolbar == null) {
            return;
        }
        toolbar.setTitle(person.getName());
        MenuItem inPool = toolbar.getMenu().findItem(R.id.shufflepod_person_in_pool_item);
        if (inPool != null) {
            inPool.setChecked(person.isInPool());
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Person person = People.get(personId);
        if (person == null) {
            return super.onMenuItemClick(item);
        }
        int id = item.getItemId();
        if (id == R.id.shufflepod_person_in_pool_item) {
            People.setInPool(personId, !person.isInPool());
            updateToolbar();
            return true;
        } else if (id == R.id.shufflepod_person_shows_item) {
            PersonDialogs.showShows(requireContext(), person, this::loadItems);
            return true;
        } else if (id == R.id.shufflepod_person_refresh_item) {
            PersonDialogs.runSync(requireContext(), personId, this::loadItems);
            return true;
        } else if (id == R.id.shufflepod_person_find_past_item) {
            People.includePastAppearances(personId);
            PersonDialogs.runSync(requireContext(), personId, this::loadItems);
            return true;
        } else if (id == R.id.shufflepod_person_edit_item) {
            PersonDialogs.showEdit(requireContext(), person, () -> {
                updateToolbar();
                loadItems();
            });
            return true;
        } else if (id == R.id.shufflepod_person_unfollow_item) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.shufflepod_person_unfollow_confirm, person.getName()))
                    .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                        People.remove(personId);
                        getParentFragmentManager().popBackStack();
                    })
                    .setNegativeButton(R.string.cancel_label, null)
                    .show();
            return true;
        }
        return super.onMenuItemClick(item);
    }

    @Override
    protected void onPrepareContextMenu(ContextMenu menu) {
        MenuItem muteEpisode = menu.findItem(R.id.shufflepod_person_mute_episode_item);
        if (muteEpisode != null) {
            muteEpisode.setVisible(true);
        }
        MenuItem muteShow = menu.findItem(R.id.shufflepod_person_mute_show_item);
        if (muteShow != null) {
            muteShow.setVisible(true);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id != R.id.shufflepod_person_mute_episode_item && id != R.id.shufflepod_person_mute_show_item) {
            return super.onContextItemSelected(item);
        }
        FeedItem selected = listAdapter.getLongPressedItem();
        Person person = People.get(personId);
        if (selected == null || person == null) {
            return false;
        }
        if (id == R.id.shufflepod_person_mute_episode_item) {
            People.muteEpisode(personId, selected);
        } else {
            String title = selected.getFeed() != null ? selected.getFeed().getTitle() : "";
            People.muteShow(personId, EpisodeKeys.feedKey(selected), title);
            EventBus.getDefault().post(new MessageEvent(
                    getString(R.string.shufflepod_person_show_muted, title, person.getName())));
        }
        loadItems();
        return true;
    }

    @NonNull
    @Override
    protected List<FeedItem> loadData() {
        return ShufflepodPeople.getEpisodes(personId);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadMoreData(int page) {
        return new ArrayList<>();
    }

    @Override
    protected int loadTotalItemCount() {
        return People.getEpisodeCount(personId);
    }

    @Override
    protected FeedItemFilter getFilter() {
        return FeedItemFilter.unfiltered();
    }

    @Override
    protected String getFragmentTag() {
        return TAG;
    }
}
