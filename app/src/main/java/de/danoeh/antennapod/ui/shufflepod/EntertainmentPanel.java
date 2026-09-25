package de.danoeh.antennapod.ui.shufflepod;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.shufflepod.EntertainmentPool;
import de.danoeh.antennapod.shufflepod.People;
import de.danoeh.antennapod.shufflepod.Person;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.ShufflepodEntertainment;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The strip under the queue that says what plays from Entertainment once the queue runs out.
 * With one show in the pool it shows that show's next episode; with several it only lists the shows.
 */
public class EntertainmentPanel {
    private static final String TAG = "EntertainmentPanel";

    private final View panel;
    private final TextView text;
    private Disposable disposable;

    public EntertainmentPanel(View root, MainActivity activity) {
        panel = root.findViewById(R.id.shufflepodEntertainmentPanel);
        text = root.findViewById(R.id.shufflepodEntertainmentPanelText);
        if (panel != null) {
            panel.setOnClickListener(v -> activity.loadChildFragment(new EntertainmentFragment()));
        }
    }

    public void refresh() {
        if (panel == null) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(this::describe)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(text::setText, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public void dispose() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private String describe() {
        List<FeedItem> forced = ShufflepodEntertainment.forcedEpisodes();
        if (forced.isEmpty()) {
            return describePool();
        }
        List<String> titles = new ArrayList<>();
        for (FeedItem item : forced) {
            titles.add(item.getTitle());
        }
        String forcedLine = text.getContext().getString(R.string.shufflepod_panel_forced,
                TextUtils.join(", ", titles));
        if (EntertainmentPool.isEmpty() && People.getPooledPeople().isEmpty()) {
            return forcedLine;
        }
        return forcedLine + "\n" + describePool();
    }

    private String describePool() {
        List<Long> feedIds = EntertainmentPool.getFeedIds();
        List<Person> people = People.getPooledPeople();
        if (feedIds.isEmpty() && people.isEmpty()) {
            return text.getContext().getString(R.string.shufflepod_panel_empty);
        }
        if (feedIds.isEmpty() && people.size() == 1) {
            FeedItem next = ShufflepodPeople.oldestEligible(people.get(0).getId(), -1);
            if (next == null) {
                return text.getContext().getString(R.string.shufflepod_panel_exhausted);
            }
            return text.getContext().getString(R.string.shufflepod_panel_single,
                    next.getTitle(), next.getFeed() != null ? next.getFeed().getTitle() : "");
        }
        if (feedIds.size() == 1 && people.isEmpty()) {
            FeedItem next = ShufflepodEntertainment.oldestEligible(feedIds.get(0), -1);
            if (next == null) {
                return text.getContext().getString(R.string.shufflepod_panel_exhausted);
            }
            Feed feed = DBReader.getFeed(feedIds.get(0), false, 0, 0);
            return text.getContext().getString(R.string.shufflepod_panel_single,
                    next.getTitle(), feed != null ? feed.getTitle() : "");
        }
        List<String> titles = new ArrayList<>();
        for (long feedId : feedIds) {
            Feed feed = DBReader.getFeed(feedId, false, 0, 0);
            if (feed != null && feed.getState() == Feed.STATE_SUBSCRIBED) {
                titles.add(feed.getTitle());
            }
        }
        for (Person person : people) {
            titles.add(person.getName());
        }
        if (titles.isEmpty()) {
            return text.getContext().getString(R.string.shufflepod_panel_exhausted);
        }
        return text.getContext().getResources().getQuantityString(R.plurals.shufflepod_panel_shuffle,
                titles.size(), titles.size(), TextUtils.join(", ", titles));
    }
}
