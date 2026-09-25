package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.shufflepod.EntertainmentPool;
import de.danoeh.antennapod.shufflepod.ShowTags;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One-time changes for existing installs. Runs at app start.
 */
public final class ShufflepodMigrations {
    private static final String TAG = "ShufflepodMigrations";
    private static final String KEY_TABS = "shufflepod_migrated_tabs_v1";
    private static final String KEY_TAGS = "shufflepod_migrated_tags_v1";

    private ShufflepodMigrations() {
    }

    public static void run(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(KEY_TABS, false)) {
            migrateTabs(prefs);
        }
        if (!prefs.getBoolean(KEY_TAGS, false)) {
            Completable.fromAction(() -> {
                migrateTags();
                prefs.edit().putBoolean(KEY_TAGS, true).apply();
            })
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> { }, error -> Log.e(TAG, Log.getStackTraceString(error)));
        }
    }

    /**
     * Resets the tab order so the bar becomes Playing, Podcasts, News, Entertainment, People, and stops
     * opening on Home or Inbox, which are now hidden.
     */
    private static void migrateTabs(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit()
                .remove(UserPreferences.PREF_HIDDEN_DRAWER_ITEMS)
                .remove(UserPreferences.PREF_DRAWER_ITEM_ORDER);
        String defaultPage = prefs.getString(UserPreferences.PREF_DEFAULT_PAGE, "");
        if ("HomeFragment".equals(defaultPage) || "NewEpisodesFragment".equals(defaultPage)) {
            editor.remove(UserPreferences.PREF_DEFAULT_PAGE);
        }
        editor.putBoolean(KEY_TABS, true).apply();
    }

    /**
     * Shows that added new episodes to the queue get the News tag, shows in the old Entertainment pool get
     * the Entertainment tag, and the inbox is emptied.
     */
    private static void migrateTags() throws Exception {
        List<Long> legacyPool = EntertainmentPool.getFeedIds();
        FeedPreferences.NewEpisodesAction globalAction = UserPreferences.getNewEpisodesAction();
        for (Feed feed : DBReader.getFeedList()) {
            FeedPreferences preferences = feed.getPreferences();
            if (feed.getState() != Feed.STATE_SUBSCRIBED || preferences == null) {
                continue;
            }
            boolean changed = false;
            if (legacyPool.contains(feed.getId()) && preferences.getTags().add(ShowTags.ENTERTAINMENT)) {
                changed = true;
            }
            FeedPreferences.NewEpisodesAction action = preferences.getNewEpisodesAction();
            if (action == FeedPreferences.NewEpisodesAction.GLOBAL) {
                action = globalAction;
            }
            if (action == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE
                    && preferences.getTags().add(ShowTags.NEWS)) {
                changed = true;
            }
            if (changed) {
                DBWriter.setFeedPreferences(preferences).get();
            }
        }
        EntertainmentPool.clear();
        DBWriter.removeAllNewFlags().get();
    }
}
