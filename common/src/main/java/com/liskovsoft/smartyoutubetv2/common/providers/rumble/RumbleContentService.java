package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;
import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.LocalContentBase;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderAuth;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import io.reactivex.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Rumble implementation of the app's content sections. There is no login: subscriptions and history are local.
 */
public class RumbleContentService extends LocalContentBase {
    private static final long FEED_BUDGET_MS = 25000;
    private static final int MAX_FEED_CHANNELS = 4; // every followed channel is one rendered page

    private static class Row {
        final String title;
        final String path;

        Row(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    public RumbleContentService(ProviderStore store) {
        super(store);
    }

    // Rows (home and categories)

    @Override
    public Observable<List<MediaGroup>> getHomeObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_HOME, homeRows()));
    }

    @Override
    public Observable<List<MediaGroup>> getTrendingObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_TRENDING, trendingRows()));
    }

    @Override
    public Observable<List<MediaGroup>> getGamingObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_GAMING, categoryRows("gaming")));
    }

    @Override
    public Observable<List<MediaGroup>> getMusicObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_MUSIC, categoryRows("music")));
    }

    @Override
    public Observable<List<MediaGroup>> getNewsObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_NEWS, categoryRows("news")));
    }

    @Override
    public Observable<List<MediaGroup>> getSportsObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_SPORTS, categoryRows("sports")));
    }

    private List<Row> homeRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Editor picks", "/editor-picks"));
        rows.add(new Row("Top today", RumbleApi.browsePath("views", "today")));
        rows.add(new Row("News", RumbleApi.categoryPath("news")));
        rows.add(new Row("Gaming", RumbleApi.categoryPath("gaming")));
        return rows;
    }

    private List<Row> trendingRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Top today", RumbleApi.browsePath("views", "today")));
        rows.add(new Row("Top this week", RumbleApi.browsePath("views", "this-week")));
        return rows;
    }

    private List<Row> categoryRows(String category) {
        String path = RumbleApi.categoryPath(category);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Latest", path));
        rows.add(new Row("Top today", path + "?sort=views&date=today"));
        return rows;
    }

    private List<MediaGroup> fetchRows(int type, List<Row> rows) throws Exception {
        List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();

        for (Row row : rows) {
            tasks.add(() -> videoPage(type, row.title, row.path, 1));
        }

        return runParallel(tasks);
    }

    /**
     * One page of videos. The group knows how to load the next page.
     */
    private ProviderMediaGroup videoPage(int type, String title, String path, int page) throws IOException {
        List<RumbleParser.Entry> entries = RumbleApi.listing(path, page);
        ProviderMediaGroup group = new ProviderMediaGroup(type, title);

        for (RumbleParser.Entry entry : entries) {
            group.add(entry.toMediaItem());
        }

        if (entries.size() >= RumbleApi.MIN_FULL_PAGE) {
            group.setNext(() -> videoPage(type, title, path, page + 1));
        }

        return group;
    }

    // Subscriptions (local follows)

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() {
        return RxHelper.fromCallable(() -> {
            if (isSignedIn()) { // the account's own feed
                try {
                    ProviderMediaGroup feed = accountPage(1);

                    if (!feed.isEmpty()) {
                        return feed;
                    }
                } catch (IOException e) {
                    // Fall back to the local follow list
                }
            }

            return subscriptionsFeed();
        });
    }

    private boolean isSignedIn() {
        return ProviderAuth.isSignedIn(ProviderData.getAppContext(), ProviderData.RUMBLE);
    }

    private ProviderMediaGroup accountPage(int page) throws IOException {
        List<RumbleParser.Entry> entries = RumbleApi.listingSignedIn("/subscriptions", page);
        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions");

        for (RumbleParser.Entry entry : entries) {
            group.add(entry.toMediaItem());
        }

        if (entries.size() >= RumbleApi.MIN_FULL_PAGE) {
            group.setNext(() -> accountPage(page + 1));
        }

        return group;
    }

    private static long sLastAccountSync;
    private static boolean sAccountSyncing;

    @Override
    protected MediaGroup channelsGroup(boolean sortByName) {
        ProviderMediaGroup group = (ProviderMediaGroup) super.channelsGroup(sortByName);

        if (isSignedIn()) {
            if (group.isEmpty()) { // first time: nothing saved yet, so wait for the account's channels once
                syncAccountChannels();
                group = (ProviderMediaGroup) super.channelsGroup(sortByName);
            } else {
                syncAccountChannelsInBackground();
            }
        }

        fillKnownPictures(group);
        loadMissingPictures(group);
        return group;
    }

    /**
     * Saves the channels seen in the signed-in account's subscription feed as follows. Opening the Channels list never
     * waits for this once there are saved channels.
     */
    private void syncAccountChannels() {
        synchronized (RumbleContentService.class) {
            sAccountSyncing = true;
        }

        try {
            for (RumbleParser.Entry entry : RumbleApi.listingSignedIn("/subscriptions", 1)) {
                if (entry.channelId != null) {
                    mStore.follow(ProviderMediaItem.channel(entry.channelId, entry.channelName, entry.channelThumb));
                }
            }

            synchronized (RumbleContentService.class) {
                sLastAccountSync = System.currentTimeMillis();
            }
        } catch (IOException e) {
            // Try again next time
        } finally {
            synchronized (RumbleContentService.class) {
                sAccountSyncing = false;
            }
        }
    }

    private void syncAccountChannelsInBackground() {
        synchronized (RumbleContentService.class) {
            if (sAccountSyncing || System.currentTimeMillis() - sLastAccountSync < 10 * 60 * 1000) {
                return;
            }

            sAccountSyncing = true;
        }

        new Thread(() -> {
            BrowserFetcher.waitUntilIdle(60000); // screens that are loading come first
            syncAccountChannels();
        }).start();
    }

    /** Channel pictures seen on pages earlier. */
    private void fillKnownPictures(ProviderMediaGroup group) {
        for (MediaItem item : group.getMediaItems()) {
            ProviderMediaItem channel = (ProviderMediaItem) item;

            if (channel.cardImageUrl == null) {
                String thumb = RumbleApi.knownChannelThumb(channel.channelId);

                if (thumb != null) {
                    channel.cardImageUrl = thumb;
                    channel.backgroundImageUrl = thumb;
                }
            }
        }
    }

    private static boolean sLoadingPictures;

    /**
     * Fetches pictures of followed channels that still have none, in the background (a few per run).
     * They show up next time the list is opened, and are saved with the follow.
     */
    private void loadMissingPictures(ProviderMediaGroup group) {
        List<String> missing = new ArrayList<>();

        for (MediaItem item : group.getMediaItems()) {
            if (item.getCardImageUrl() == null && item.getChannelId() != null && missing.size() < 10) {
                missing.add(item.getChannelId());
            }
        }

        synchronized (RumbleContentService.class) {
            if (missing.isEmpty() || sLoadingPictures) {
                return;
            }

            sLoadingPictures = true;
        }

        new Thread(() -> {
            try {
                BrowserFetcher.waitUntilIdle(60000);

                for (String channelId : missing) {
                    try {
                        ProviderMediaItem info = RumbleApi.channelInfo(channelId);

                        if (info.cardImageUrl != null) {
                            mStore.updateChannel(info);
                        }
                    } catch (IOException e) {
                        // Next time
                    }
                }
            } finally {
                synchronized (RumbleContentService.class) {
                    sLoadingPictures = false;
                }
            }
        }).start();
    }

    /**
     * Latest videos of the followed channels merged by date. Channel pages that fail to load are skipped.
     */
    private MediaGroup subscriptionsFeed() throws Exception {
        List<ProviderMediaItem> channels = mStore.getChannels();
        ProviderMediaGroup feed = new ProviderMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions");

        if (channels.isEmpty()) {
            return feed;
        }

        // One channel page at a time (the hidden browser does one page at a time anyway). Stop early once there is
        // something to show and a time budget has passed, so the screen opens instead of waiting for every channel.
        List<ProviderMediaItem> merged = new ArrayList<>();
        long start = System.currentTimeMillis();
        IOException lastError = null;

        for (int i = 0; i < channels.size() && i < MAX_FEED_CHANNELS; i++) {
            if (!merged.isEmpty() && System.currentTimeMillis() - start > FEED_BUDGET_MS) {
                break;
            }

            try {
                for (MediaItem item : videoPage(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions", RumbleApi.channelPath(channels.get(i).channelId), 1).getMediaItems()) {
                    merged.add((ProviderMediaItem) item);
                }
            } catch (IOException e) {
                lastError = e; // skip this channel
            }
        }

        if (merged.isEmpty() && lastError != null) {
            throw lastError;
        }

        RumbleParser.sortNewestFirst(merged);

        for (ProviderMediaItem item : merged) {
            feed.add(item);
        }

        return feed;
    }

    // Channel pages

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(String channelId) {
        return RxHelper.fromCallable(() -> channelRows(channelId));
    }

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(MediaItem item) {
        return RxHelper.fromCallable(() -> channelRows(channelIdOf(item)));
    }

    private List<MediaGroup> channelRows(String channelId) throws Exception {
        List<MediaGroup> result = new ArrayList<>();

        if (channelId != null) {
            ProviderMediaGroup latest = videoPage(MediaGroup.TYPE_CHANNEL, "Latest videos", RumbleApi.channelPath(channelId), 1);
            latest.setChannelId(channelId);
            result.add(latest);
        }

        return result;
    }

    @Override
    public Observable<MediaGroup> getGroupObserve(MediaItem item) {
        return RxHelper.fromCallable(() -> channelUploads(channelIdOf(item)));
    }

    @Override
    public Observable<MediaGroup> getGroupObserve(String reloadPageKey) {
        return RxHelper.fromCallable(() -> channelUploads(channelIdOf(reloadPageKey)));
    }

    private MediaGroup channelUploads(String channelId) throws IOException {
        if (channelId == null) {
            return new ProviderMediaGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "Uploads");
        }

        ProviderMediaGroup group = videoPage(MediaGroup.TYPE_CHANNEL_UPLOADS, "Uploads", RumbleApi.channelPath(channelId), 1);
        group.setChannelId(channelId);
        return group;
    }

    // Search

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText) {
        return RxHelper.fromCallable(() -> search(searchText));
    }

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText, int options) {
        return getSearchObserve(searchText);
    }

    private List<MediaGroup> search(String text) throws IOException {
        List<MediaGroup> result = new ArrayList<>();
        ProviderMediaGroup videos = videoPage(MediaGroup.TYPE_SEARCH, "Videos", RumbleApi.searchVideosPath(text), 1);

        if (!videos.isEmpty()) {
            result.add(videos);
        }

        try { // channels are optional: do not fail the whole search
            ProviderMediaGroup channels = new ProviderMediaGroup(MediaGroup.TYPE_SEARCH, "Channels");

            for (ProviderMediaItem channel : RumbleApi.searchChannels(text)) {
                channels.add(channel);
            }

            if (!channels.isEmpty()) {
                result.add(channels);
            }
        } catch (IOException e) {
            // NOP
        }

        return result;
    }
}
