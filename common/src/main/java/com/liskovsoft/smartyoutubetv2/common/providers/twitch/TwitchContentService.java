package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.LocalContentBase;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import io.reactivex.Observable;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Twitch implementation of the app's content sections.<br/>
 * Home / Trending: what is live right now, and the busiest categories. Gaming: the live streams of each top category.
 * There is no sign-in: followed channels and history are stored on this device.
 */
public class TwitchContentService extends LocalContentBase {
    private static final int MAX_FOLLOWED = 100;
    private static final int NEW_GAME_DAYS = 180;
    private static final long NEW_GAMES_CACHE_MS = 10 * 60_000;

    // Categories already on Home (the busiest ones); the Gaming section leaves them out
    private static final List<String> HOME_ONLY_CATEGORIES = Arrays.asList("just chatting", "irl");

    // Twitch has no genre lists we can ask for, so these are the well-known categories of each genre.
    // Names that don't exist (renamed games) are skipped.
    private static final List<String> TEAM_FPS = Arrays.asList(
            "VALORANT", "Counter-Strike", "Counter-Strike 2", "Overwatch 2", "Apex Legends", "Marvel Rivals",
            "Tom Clancy's Rainbow Six Siege X", "Tom Clancy's Rainbow Six Siege", "Call of Duty: Warzone",
            "Call of Duty: Black Ops 6", "Call of Duty: Black Ops 7", "Battlefield 6", "Battlefield 2042",
            "The Finals", "Deadlock", "Splitgate 2", "PUBG: BATTLEGROUNDS", "Fortnite");
    private static final List<String> RPGS = Arrays.asList(
            "Elden Ring", "Baldur's Gate 3", "Path of Exile 2", "Path of Exile", "Diablo IV", "World of Warcraft",
            "Final Fantasy XIV Online", "The Elder Scrolls Online", "Old School RuneScape", "RuneScape",
            "Cyberpunk 2077", "The Witcher 3: Wild Hunt", "Monster Hunter Wilds", "Lost Ark", "Black Desert",
            "Genshin Impact", "Honkai: Star Rail", "Fallout 76", "Starfield", "Kingdom Come: Deliverance II",
            "Clair Obscur: Expedition 33", "Last Epoch", "Dragon Age: The Veilguard", "Grim Dawn");

    private final TwitchPrefs mPrefs;
    private List<TwitchApi.Game> mNewGames;
    private long mNewGamesAt;

    public TwitchContentService(ProviderStore store, Context context) {
        super(store);
        mPrefs = new TwitchPrefs(context);
    }

    /** Applies the "streams per row" setting before anything is loaded. */
    private void applySettings() {
        TwitchApi.setPageSize(mPrefs.streamsPerRow());
    }

    // Rows

    @Override
    public Observable<List<MediaGroup>> getHomeObserve() {
        return RxHelper.fromCallable(() -> {
            applySettings();
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();

            if (mPrefs.homeLive()) {
                tasks.add(() -> streamsPage(MediaGroup.TYPE_HOME, "Live now", null, null));
            }

            if (mPrefs.homeCategories()) {
                tasks.add(() -> recommendedCategoriesRow(MediaGroup.TYPE_HOME));
            }

            if (mPrefs.homeTopCategories()) {
                addGameRowsIfPossible(tasks, MediaGroup.TYPE_HOME, mPrefs.categoryRows(), false);
            }

            return nonEmpty(runParallel(tasks));
        });
    }

    @Override
    public Observable<List<MediaGroup>> getTrendingObserve() {
        return RxHelper.fromCallable(() -> {
            applySettings();
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();
            tasks.add(() -> streamsPage(MediaGroup.TYPE_TRENDING, "Most watched live", null, null));
            return runParallel(tasks);
        });
    }

    @Override
    public Observable<List<MediaGroup>> getGamingObserve() {
        return RxHelper.fromCallable(() -> {
            applySettings();
            List<Callable<ProviderMediaGroup>> tasks = new ArrayList<>();

            if (mPrefs.gamingTeamFps()) {
                tasks.add(() -> curatedStreamsRow("Team FPS", TEAM_FPS));
            }

            if (mPrefs.gamingRpgs()) {
                tasks.add(() -> curatedStreamsRow("RPGs", RPGS));
            }

            if (mPrefs.gamingNewGames()) {
                tasks.add(this::newGamesRow);
            }

            if (mPrefs.gamingNewGameStreams()) {
                tasks.add(this::newGameStreamsRow);
            }

            if (mPrefs.gamingTopCategories()) {
                addGameRows(tasks, MediaGroup.TYPE_GAMING, mPrefs.categoryRows(), true);
            }

            return nonEmpty(runParallel(tasks));
        });
    }

    /** Rows without anything in them are left out instead of showing an empty title. */
    private static List<MediaGroup> nonEmpty(List<MediaGroup> groups) {
        List<MediaGroup> result = new ArrayList<>();

        for (MediaGroup group : groups) {
            if (group != null && group.getMediaItems() != null && !group.getMediaItems().isEmpty()) {
                result.add(group);
            }
        }

        return result;
    }

    private void addGameRows(List<Callable<ProviderMediaGroup>> tasks, int type, int count, boolean skipHomeOnes) throws IOException {
        List<TwitchApi.Game> games = TwitchApi.topGames(skipHomeOnes ? count + HOME_ONLY_CATEGORIES.size() : count);
        int added = 0;

        for (TwitchApi.Game game : games) {
            if (added >= count) {
                break;
            }

            if (skipHomeOnes && HOME_ONLY_CATEGORIES.contains(game.displayName.toLowerCase(Locale.US))) {
                continue;
            }

            tasks.add(() -> streamsPage(type, game.displayName, game.name, null));
            added++;
        }
    }

    /** Category rows are a bonus on Home: if they can't be loaded, the rest is still shown. */
    private void addGameRowsIfPossible(List<Callable<ProviderMediaGroup>> tasks, int type, int count, boolean skipHomeOnes) {
        try {
            addGameRows(tasks, type, count, skipHomeOnes);
        } catch (IOException e) {
            // NOP
        }
    }

    // Category cards

    private ProviderMediaGroup categoryCards(int type, String title, List<TwitchApi.Game> games) {
        ProviderMediaGroup group = new ProviderMediaGroup(type, title);

        for (TwitchApi.Game game : games) {
            group.add(game.toCategoryItem());
        }

        return group;
    }

    /**
     * Categories of the channels you follow or watched (the ones live right now), most common first, then the busiest
     * categories to fill the row.
     */
    private ProviderMediaGroup recommendedCategoriesRow(int type) throws IOException {
        int limit = mPrefs.streamsPerRow();
        LinkedHashSet<String> logins = new LinkedHashSet<>(followedLogins());

        for (ProviderMediaItem watched : mStore.getHistory()) {
            if (watched.channelId != null && !watched.channelId.startsWith(TwitchApi.GAME_PREFIX) && logins.size() < 60) {
                logins.add(watched.channelId);
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, TwitchApi.Game> byName = new LinkedHashMap<>();

        if (!logins.isEmpty()) {
            try {
                for (TwitchApi.User user : TwitchApi.users(new ArrayList<>(logins))) {
                    if (user.game != null) {
                        counts.put(user.game.name, counts.containsKey(user.game.name) ? counts.get(user.game.name) + 1 : 1);
                        byName.put(user.game.name, user.game);
                    }
                }
            } catch (IOException e) {
                // Personal picks are optional: fall through to the busiest categories
            }
        }

        List<TwitchApi.Game> picks = new ArrayList<>(byName.values());
        Collections.sort(picks, new Comparator<TwitchApi.Game>() {
            @Override
            public int compare(TwitchApi.Game a, TwitchApi.Game b) {
                return counts.get(b.name) - counts.get(a.name);
            }
        });

        if (picks.size() < limit) {
            for (TwitchApi.Game popular : TwitchApi.topGames(limit + picks.size())) {
                if (picks.size() < limit && !byName.containsKey(popular.name)) {
                    picks.add(popular);
                    byName.put(popular.name, popular);
                }
            }
        }

        return categoryCards(type, "Categories we think you'll like", picks.subList(0, Math.min(limit, picks.size())));
    }

    // Genre rows and new games

    /** The busiest live streams across a list of well-known categories. */
    private ProviderMediaGroup curatedStreamsRow(String title, List<String> categories) throws IOException {
        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_GAMING, title);

        for (ProviderMediaItem item : TwitchApi.streamsOfGames(categories, 6, mPrefs.streamsPerRow())) {
            group.add(item);
        }

        return group;
    }

    private synchronized List<TwitchApi.Game> newGames() {
        if (mNewGames == null || System.currentTimeMillis() - mNewGamesAt > NEW_GAMES_CACHE_MS) {
            try {
                mNewGames = TwitchApi.newGames(NEW_GAME_DAYS, 20);
                mNewGamesAt = System.currentTimeMillis();
            } catch (IOException e) {
                mNewGames = new ArrayList<>(); // Twitch can't tell us: hide the two rows rather than fail Gaming
                mNewGamesAt = System.currentTimeMillis();
            }
        }

        return mNewGames;
    }

    private ProviderMediaGroup newGamesRow() {
        List<TwitchApi.Game> games = newGames();
        return categoryCards(MediaGroup.TYPE_GAMING, "New games", games.subList(0, Math.min(mPrefs.streamsPerRow(), games.size())));
    }

    private ProviderMediaGroup newGameStreamsRow() throws IOException {
        List<String> names = new ArrayList<>();

        for (TwitchApi.Game game : newGames()) {
            names.add(game.name);
        }

        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_GAMING, "New game streams");

        for (ProviderMediaItem item : TwitchApi.streamsOfGames(names, 4, mPrefs.streamsPerRow())) {
            group.add(item);
        }

        return group;
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

    // Subscriptions: what the followed channels streamed or uploaded during the last 30 days (live now included), newest first

    private static final long FEED_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long FEED_CACHE_MS = 3 * 60_000;
    private List<ProviderMediaItem> mFeed;
    private long mFeedAt;
    private List<String> mFeedLogins;

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() {
        return RxHelper.fromCallable(() -> {
            ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, "Followed channels: last 30 days");
            List<String> logins = followedLogins();

            if (logins.isEmpty()) {
                return group;
            }

            for (ProviderMediaItem item : feed(logins)) {
                group.add(item);
            }

            return group;
        });
    }

    private synchronized List<ProviderMediaItem> feed(List<String> logins) throws IOException {
        if (mFeed != null && logins.equals(mFeedLogins) && System.currentTimeMillis() - mFeedAt < FEED_CACHE_MS) {
            return mFeed;
        }

        List<ProviderMediaItem> items = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        try { // live now
            for (TwitchApi.User user : TwitchApi.users(logins)) {
                if (user.live != null && seen.add(user.live.videoId)) {
                    items.add(user.live);
                }
            }
        } catch (IOException e) {
            // the recorded videos below are still worth showing
        }

        for (ProviderMediaItem item : TwitchApi.recentVideos(logins, 12, System.currentTimeMillis() - FEED_DAYS_MS)) {
            if (seen.add(item.videoId)) {
                items.add(item);
            }
        }

        Collections.sort(items, new Comparator<ProviderMediaItem>() {
            @Override
            public int compare(ProviderMediaItem a, ProviderMediaItem b) {
                return Long.compare(b.publishedMs, a.publishedMs); // newest first
            }
        });

        mFeed = items;
        mFeedLogins = new ArrayList<>(logins);
        mFeedAt = System.currentTimeMillis();
        return items;
    }

    private List<String> followedLogins() {
        List<String> logins = new ArrayList<>();

        for (ProviderMediaItem channel : mStore.getChannels()) {
            if (channel.channelId != null && !channel.channelId.startsWith(TwitchApi.GAME_PREFIX) && logins.size() < MAX_FOLLOWED) {
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

        if (login.startsWith(TwitchApi.GAME_PREFIX)) { // a category card: its live streams
            applySettings();
            String game = login.substring(TwitchApi.GAME_PREFIX.length());
            List<MediaGroup> rows = new ArrayList<>();
            rows.add(streamsPage(MediaGroup.TYPE_CHANNEL, game, game, null));
            return rows;
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

        if (login.startsWith(TwitchApi.GAME_PREFIX)) {
            String game = login.substring(TwitchApi.GAME_PREFIX.length());
            return streamsPage(MediaGroup.TYPE_CHANNEL_UPLOADS, game, game, null);
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
