package com.liskovsoft.smartyoutubetv2.common.providers.odysee;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;
import com.liskovsoft.smartyoutubetv2.common.providers.odysee.OdyseeApi.Query;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubContentService;

import io.reactivex.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Odysee implementation of the app's content sections (home, search, channels, subscriptions, history...).<br/>
 * There is no login: subscriptions and history are stored locally.
 */
public class OdyseeContentService extends StubContentService {
    private static final int MAX_FEED_CHANNELS = 100;
    private final ProviderStore mStore;

    private static class Row {
        final String title;
        final Query query;

        Row(String title, Query query) {
            this.title = title;
            this.query = query;
        }
    }

    public OdyseeContentService(ProviderStore store) {
        mStore = store;
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
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_GAMING, tagRows("gaming")));
    }

    @Override
    public Observable<List<MediaGroup>> getMusicObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_MUSIC, tagRows("music")));
    }

    @Override
    public Observable<List<MediaGroup>> getNewsObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_NEWS, tagRows("news")));
    }

    @Override
    public Observable<List<MediaGroup>> getSportsObserve() {
        return RxHelper.fromCallable(() -> fetchRows(MediaGroup.TYPE_SPORTS, tagRows("sports")));
    }

    private List<Row> homeRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Trending", OdyseeApi.videos().orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("New", OdyseeApi.videos().orderBy("release_time").newerThanDays(3).perChannel(2)));
        rows.add(new Row("Top this week", OdyseeApi.videos().orderBy("effective_amount").newerThanDays(7).perChannel(2)));
        rows.add(new Row("Gaming", OdyseeApi.videos().tags("gaming").orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("Music", OdyseeApi.videos().tags("music").orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("News", OdyseeApi.videos().tags("news").orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("Science & Technology", OdyseeApi.videos().tags("science", "technology").orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("Sports", OdyseeApi.videos().tags("sports").orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("Education", OdyseeApi.videos().tags("education").orderBy("trending_group", "trending_mixed").perChannel(2)));
        return rows;
    }

    private List<Row> trendingRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Trending today", OdyseeApi.videos().orderBy("trending_group", "trending_mixed").newerThanDays(1).perChannel(2)));
        rows.add(new Row("Trending this week", OdyseeApi.videos().orderBy("trending_group", "trending_mixed").newerThanDays(7).perChannel(2)));
        rows.add(new Row("Most supported this month", OdyseeApi.videos().orderBy("effective_amount").newerThanDays(30).perChannel(2)));
        return rows;
    }

    private List<Row> tagRows(String tag) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Trending", OdyseeApi.videos().tags(tag).orderBy("trending_group", "trending_mixed").perChannel(2)));
        rows.add(new Row("New", OdyseeApi.videos().tags(tag).orderBy("release_time").newerThanDays(7).perChannel(2)));
        rows.add(new Row("Top this week", OdyseeApi.videos().tags(tag).orderBy("effective_amount").newerThanDays(7).perChannel(2)));
        return rows;
    }

    /**
     * Loads all rows in parallel. A row that fails is skipped; fails only if every row failed.
     */
    private List<MediaGroup> fetchRows(int type, List<Row> rows) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<MediaGroup> result = new ArrayList<>();
        Exception lastError = null;

        try {
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();

            for (Row row : rows) {
                tasks.add(() -> videoPage(type, row.title, row.query, 1));
            }

            List<Future<ProviderMediaGroup>> futures = pool.invokeAll(tasks);

            for (Future<ProviderMediaGroup> future : futures) {
                try {
                    ProviderMediaGroup group = future.get();

                    if (group != null && !group.isEmpty()) {
                        result.add(group);
                    }
                } catch (ExecutionException e) {
                    lastError = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                }
            }
        } finally {
            pool.shutdown();
        }

        if (result.isEmpty() && lastError != null) {
            throw lastError;
        }

        return result;
    }

    /**
     * One page of videos for the query. The group knows how to load the next page.
     */
    private ProviderMediaGroup videoPage(int type, String title, Query query, int page) throws IOException {
        List<OdyseeClaim> claims = OdyseeApi.claimSearch(query, page);
        ProviderMediaGroup group = new ProviderMediaGroup(type, title);

        for (OdyseeClaim claim : claims) {
            if (claim.isPlayable()) {
                group.add(claim.toMediaItem());
            }
        }

        if (claims.size() >= query.pageSize) {
            group.setNext(() -> videoPage(type, title, query, page + 1));
        }

        return group;
    }

    // Subscriptions, channels and history (local)

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() {
        return RxHelper.fromCallable(() -> {
            List<String> ids = followedChannelIds();

            if (ids.isEmpty()) {
                return new ProviderMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions");
            }

            Query query = OdyseeApi.videos().channels(ids.toArray(new String[0])).orderBy("release_time").size(36);
            return videoPage(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions", query, 1);
        });
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNewContentObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNameObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(true));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByLastViewedObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    private MediaGroup channelsGroup(boolean sortByName) {
        List<ProviderMediaItem> channels = mStore.getChannels();

        if (sortByName) {
            Collections.sort(channels, (a, b) -> safe(a.title).compareToIgnoreCase(safe(b.title)));
        }

        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "Channels");

        for (ProviderMediaItem channel : channels) {
            group.add(channel);
        }

        return group;
    }

    @Override
    public Observable<MediaGroup> getHistoryObserve() {
        return RxHelper.fromCallable(() -> {
            ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_HISTORY, "History");

            for (ProviderMediaItem video : mStore.getHistory()) {
                group.add(video);
            }

            return group;
        });
    }

    @Override
    public void clearHistory() {
        mStore.clearHistory();
    }

    private List<String> followedChannelIds() {
        List<String> ids = new ArrayList<>();

        for (ProviderMediaItem channel : mStore.getChannels()) {
            if (channel.channelId != null && ids.size() < MAX_FEED_CHANNELS) {
                ids.add(channel.channelId);
            }
        }

        return ids;
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
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Latest videos", OdyseeApi.videos().channels(channelId).orderBy("release_time")));
        rows.add(new Row("Most popular", OdyseeApi.videos().channels(channelId).orderBy("effective_amount")));
        List<MediaGroup> result = fetchRows(MediaGroup.TYPE_CHANNEL, rows);

        for (MediaGroup group : result) {
            ((ProviderMediaGroup) group).setChannelId(channelId);
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

        ProviderMediaGroup group = videoPage(MediaGroup.TYPE_CHANNEL_UPLOADS, "Uploads",
                OdyseeApi.videos().channels(channelId).orderBy("release_time").size(36), 1);
        group.setChannelId(channelId);
        return group;
    }

    @Override
    public Observable<MediaGroup> continueGroupObserve(MediaGroup mediaGroup) {
        return RxHelper.fromCallable(() -> {
            if (mediaGroup instanceof ProviderMediaGroup && ((ProviderMediaGroup) mediaGroup).getNext() != null) {
                ProviderMediaGroup next = ((ProviderMediaGroup) mediaGroup).getNext().load();
                next.setChannelId(mediaGroup.getChannelId());
                return next;
            }

            return new ProviderMediaGroup(mediaGroup != null ? mediaGroup.getType() : MediaGroup.TYPE_UNDEFINED, null);
        });
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
        ProviderMediaGroup videos = searchVideos(text, 0);

        if (!videos.isEmpty()) {
            result.add(videos);
        }

        try { // channels are optional: do not fail the whole search
            List<String> channelIds = OdyseeApi.searchIds(text, true, 0, 12);
            ProviderMediaGroup channels = new ProviderMediaGroup(MediaGroup.TYPE_SEARCH, "Channels");

            for (OdyseeClaim claim : OdyseeApi.claimsByIds(channelIds, true)) {
                channels.add(claim.toMediaItem());
            }

            if (!channels.isEmpty()) {
                result.add(channels);
            }
        } catch (IOException e) {
            // NOP
        }

        return result;
    }

    private ProviderMediaGroup searchVideos(String text, int from) throws IOException {
        final int size = OdyseeApi.PAGE_SIZE;
        List<String> ids = OdyseeApi.searchIds(text, false, from, size);
        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_SEARCH, "Videos");

        for (OdyseeClaim claim : OdyseeApi.claimsByIds(ids, false)) {
            if (claim.isPlayable()) {
                group.add(claim.toMediaItem());
            }
        }

        if (ids.size() >= size) {
            group.setNext(() -> searchVideos(text, from + size));
        }

        return group;
    }

    // Helpers

    static String channelIdOf(MediaItem item) {
        if (item == null) {
            return null;
        }

        String key = item.getReloadPageKey();

        if (key != null && key.startsWith(ProviderMediaItem.CHANNEL_KEY_PREFIX)) {
            return key.substring(ProviderMediaItem.CHANNEL_KEY_PREFIX.length());
        }

        return item.getChannelId();
    }

    static String channelIdOf(String reloadPageKey) {
        if (reloadPageKey != null && reloadPageKey.startsWith(ProviderMediaItem.CHANNEL_KEY_PREFIX)) {
            return reloadPageKey.substring(ProviderMediaItem.CHANNEL_KEY_PREFIX.length());
        }

        return null;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
