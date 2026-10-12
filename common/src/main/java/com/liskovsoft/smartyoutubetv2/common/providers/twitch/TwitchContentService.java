package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.LocalContentBase;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import io.reactivex.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Twitch implementation of the app's content sections.<br/>
 * Home / Trending: what is live right now, and the busiest categories. Gaming: the live streams of each top category.
 * There is no sign-in: followed channels and history are stored on this device.
 */
public class TwitchContentService extends LocalContentBase {
    private static final int HOME_GAMES = 6;
    private static final int GAMING_GAMES = 12;
    private static final int MAX_FOLLOWED = 100;

    public TwitchContentService(ProviderStore store) {
        super(store);
    }

    // Rows

    @Override
    public Observable<List<MediaGroup>> getHomeObserve() {
        return RxHelper.fromCallable(() -> {
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();
            tasks.add(() -> streamsPage(MediaGroup.TYPE_HOME, "Live now", null, null));
            addGameRows(tasks, MediaGroup.TYPE_HOME, HOME_GAMES);
            return runParallel(tasks);
        });
    }

    @Override
    public Observable<List<MediaGroup>> getTrendingObserve() {
        return RxHelper.fromCallable(() -> {
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();
            tasks.add(() -> streamsPage(MediaGroup.TYPE_TRENDING, "Most watched live", null, null));
            return runParallel(tasks);
        });
    }

    @Override
    public Observable<List<MediaGroup>> getGamingObserve() {
        return RxHelper.fromCallable(() -> {
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();
            addGameRows(tasks, MediaGroup.TYPE_GAMING, GAMING_GAMES);
            return runParallel(tasks);
        });
    }

    private void addGameRows(List<Callable<ProviderMediaGroup>> tasks, int type, int count) throws IOException {
        for (TwitchApi.Game game : TwitchApi.topGames(count)) {
            tasks.add(() -> streamsPage(type, game.displayName, game.name, null));
        }
    }

    /**
     * One page of live streams: the busiest overall, or of one category. The group knows how to load the next page.
     */
    private ProviderMediaGroup streamsPage(int type, String title, String game, String after) throws IOException {
        TwitchApi.Page page = game == null ? TwitchApi.topStreams(after) : TwitchApi.gameStreams(game, after);
        ProviderMediaGroup group = new ProviderMediaGroup(type, title);

        for (ProviderMediaItem item : page.items) {
            group.add(item);
        }

        if (page.cursor != null) {
            group.setNext(() -> streamsPage(type, title, game, page.cursor));
        }

        return group;
    }

    /**
     * One page of a channel's past videos. The group knows how to load the next page.
     */
    private ProviderMediaGroup videosPage(int type, String title, String login, String videoType, String after) throws IOException {
        TwitchApi.Page page = TwitchApi.videos(login, videoType, after);
        ProviderMediaGroup group = new ProviderMediaGroup(type, title);
        group.setChannelId(login);

        for (ProviderMediaItem item : page.items) {
            group.add(item);
        }

        if (page.cursor != null) {
            group.setNext(() -> videosPage(type, title, login, videoType, page.cursor));
        }

        return group;
    }

    // Subscriptions (followed channels that are live now)

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() {
        return RxHelper.fromCallable(() -> {
            ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, "Followed channels live now");
            List<String> logins = followedLogins();

            if (logins.isEmpty()) {
                return group;
            }

            List<ProviderMediaItem> live = new ArrayList<>();

            for (TwitchApi.User user : TwitchApi.users(logins)) {
                if (user.live != null) {
                    live.add(user.live);
                }
            }

            Collections.sort(live, new Comparator<ProviderMediaItem>() {
                @Override
                public int compare(ProviderMediaItem a, ProviderMediaItem b) {
                    return Long.compare(b.publishedMs, a.publishedMs); // newest broadcast first
                }
            });

            for (ProviderMediaItem item : live) {
                group.add(item);
            }

            return group;
        });
    }

    private List<String> followedLogins() {
        List<String> logins = new ArrayList<>();

        for (ProviderMediaItem channel : mStore.getChannels()) {
            if (channel.channelId != null && logins.size() < MAX_FOLLOWED) {
                logins.add(channel.channelId);
            }
        }

        return logins;
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

    private List<MediaGroup> channelRows(String login) throws Exception {
        if (login == null) {
            return new ArrayList<>();
        }

        List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();
        tasks.add(() -> {
            ProviderMediaGroup now = new ProviderMediaGroup(MediaGroup.TYPE_CHANNEL, "Live now");
            TwitchApi.User user = TwitchApi.user(login);

            if (user != null && user.live != null) {
                now.add(user.live);
            }

            return now;
        });
        tasks.add(() -> videosPage(MediaGroup.TYPE_CHANNEL, "Past broadcasts", login, "ARCHIVE", null));
        tasks.add(() -> videosPage(MediaGroup.TYPE_CHANNEL, "Highlights", login, "HIGHLIGHT", null));

        List<MediaGroup> result = runParallel(tasks);

        for (MediaGroup group : result) {
            ((ProviderMediaGroup) group).setChannelId(login);
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

    private MediaGroup channelUploads(String login) throws IOException {
        if (login == null) {
            return new ProviderMediaGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "Uploads");
        }

        return videosPage(MediaGroup.TYPE_CHANNEL_UPLOADS, "Past broadcasts", login, "ARCHIVE", null);
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
        List<TwitchApi.User> users = TwitchApi.searchChannels(text);

        ProviderMediaGroup live = new ProviderMediaGroup(MediaGroup.TYPE_SEARCH, "Live now");
        ProviderMediaGroup channels = new ProviderMediaGroup(MediaGroup.TYPE_SEARCH, "Channels");

        for (TwitchApi.User user : users) {
            if (user.live != null) {
                live.add(user.live);
            }

            channels.add(user.toChannelItem());
        }

        if (!live.isEmpty()) {
            result.add(live);
        }

        if (!channels.isEmpty()) {
            result.add(channels);
        }

        return result;
    }
}
