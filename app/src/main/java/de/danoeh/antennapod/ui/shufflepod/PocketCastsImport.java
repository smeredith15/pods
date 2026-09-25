package de.danoeh.antennapod.ui.shufflepod;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.shufflepod.ArchiveStore;
import de.danoeh.antennapod.shufflepod.PocketCastsMatcher;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

/**
 * One-time import of played and archived state from Pocket Casts into the matching shows here.
 * Only ever marks episodes played or archived; never un-plays anything. Episodes Pocket Casts knows but
 * the show's feed no longer has are counted, not created. Must run off the main thread.
 */
public final class PocketCastsImport {

    private PocketCastsImport() {
    }

    public interface Progress {
        void onShow(int done, int total, String title);
    }

    public static final class Result {
        public int showsMatched;
        public int showsUnmatched;
        public int markedPlayed;
        public int archived;
        public int alreadyDone;
        public int notInFeed;
        public int showsFailed;
        public final List<String> unmatchedTitles = new ArrayList<>();
    }

    public static Result run(Context context, String email, String password, Progress progress) throws Exception {
        PocketCastsClient client = PocketCastsClient.login(email, password);
        List<PocketCastsClient.Podcast> podcasts = client.getSubscriptions();

        Map<String, Feed> byTitle = new HashMap<>();
        Map<String, Feed> byLink = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            byTitle.put(PocketCastsMatcher.normalizeTitle(feed.getTitle()), feed);
            String link = PocketCastsMatcher.normalizeUrl(feed.getLink());
            if (!link.isEmpty()) {
                byLink.put(link, feed);
            }
        }

        Result result = new Result();
        for (int i = 0; i < podcasts.size(); i++) {
            PocketCastsClient.Podcast podcast = podcasts.get(i);
            progress.onShow(i, podcasts.size(), podcast.title);
            Feed feed = byTitle.get(PocketCastsMatcher.normalizeTitle(podcast.title));
            if (feed == null) {
                feed = byLink.get(PocketCastsMatcher.normalizeUrl(podcast.url));
            }
            if (feed == null) {
                result.showsUnmatched++;
                result.unmatchedTitles.add(podcast.title);
                continue;
            }
            result.showsMatched++;
            try {
                importShow(context, client, podcast, feed, result);
            } catch (IOException e) {
                result.showsFailed++;
            }
        }
        progress.onShow(podcasts.size(), podcasts.size(), "");
        return result;
    }

    private static void importShow(Context context, PocketCastsClient client, PocketCastsClient.Podcast podcast,
                                   Feed feed, Result result) throws Exception {
        List<PocketCastsClient.EpisodeStatus> wanted = new ArrayList<>();
        for (PocketCastsClient.EpisodeStatus status : client.getEpisodeStatuses(podcast.uuid)) {
            if (status.playingStatus == PocketCastsClient.STATUS_PLAYED || status.archived) {
                wanted.add(status);
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        Map<String, PocketCastsClient.CatalogEpisode> catalog = new HashMap<>();
        for (PocketCastsClient.CatalogEpisode episode : client.getCatalog(podcast.uuid)) {
            catalog.put(episode.uuid, episode);
        }

        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        Map<Long, FeedItem> itemsById = new HashMap<>();
        List<PocketCastsMatcher.Episode> local = new ArrayList<>();
        for (FeedItem item : items) {
            itemsById.put(item.getId(), item);
            local.add(new PocketCastsMatcher.Episode(item.getId(),
                    item.getMedia() != null ? item.getMedia().getDownloadUrl() : null, item.getTitle(),
                    item.getPubDate() != null ? item.getPubDate().getTime() : 0));
        }
        PocketCastsMatcher matcher = new PocketCastsMatcher(local);

        List<FeedItem> toPlay = new ArrayList<>();
        List<FeedItem> toArchive = new ArrayList<>();
        for (PocketCastsClient.EpisodeStatus status : wanted) {
            PocketCastsClient.CatalogEpisode info = catalog.get(status.uuid);
            if (info == null) {
                result.notInFeed++;
                continue;
            }
            long id = matcher.match(new PocketCastsMatcher.Episode(0, info.url, info.title, info.publishedMs));
            FeedItem item = id >= 0 ? itemsById.get(id) : null;
            if (item == null) {
                result.notInFeed++;
            } else if (item.isPlayed() || ArchiveStore.isArchived(item)) {
                result.alreadyDone++;
            } else if (status.playingStatus == PocketCastsClient.STATUS_PLAYED) {
                toPlay.add(item);
            } else {
                toArchive.add(item);
            }
        }
        if (!toPlay.isEmpty()) {
            DBWriter.markItemsPlayed(FeedItem.PLAYED, false, toPlay).get();
            result.markedPlayed += toPlay.size();
        }
        if (!toArchive.isEmpty()) {
            ArchiveStore.archive(toArchive);
            result.archived += toArchive.size();
            EventBus.getDefault().post(new FeedItemEvent(toArchive, false));
        }
        List<Long> queued = new ArrayList<>();
        for (FeedItem item : toPlay) {
            if (item.isTagged(FeedItem.TAG_QUEUE)) {
                queued.add(item.getId());
            }
        }
        for (FeedItem item : toArchive) {
            if (item.isTagged(FeedItem.TAG_QUEUE)) {
                queued.add(item.getId());
            }
        }
        if (!queued.isEmpty()) {
            long[] ids = new long[queued.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = queued.get(i);
            }
            DBWriter.removeQueueItem(context, false, ids).get();
        }
    }
}
