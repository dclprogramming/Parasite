package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.providers.HttpStatusException;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderHttp;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

/**
 * Twitch through the same public GraphQL endpoint the twitch.tv web player uses (no account, no API key of our own),
 * plus the "usher" service that serves the HLS playlists.<br/>
 * Item ids used by the app: {@code live:<channel login>} for a live channel and {@code vod:<video id>} for a past video.
 * Channels are identified by their login name.
 */
public final class TwitchApi {
    private static final String TAG = TwitchApi.class.getSimpleName();
    private static final String GQL_URL = "https://gql.twitch.tv/gql";
    private static final String USHER_URL = "https://usher.ttvnw.net";
    // The public client id of the twitch.tv web player. If Twitch ever rejects it, the current one is read from twitch.tv
    private static final String DEFAULT_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    private static volatile String sClientId = DEFAULT_CLIENT_ID;
    private static volatile boolean sClientIdRefreshed;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Map<String, String> HEADERS = new HashMap<>();

    public static final int PAGE_SIZE = 30;
    public static final String LIVE_PREFIX = "live:";
    public static final String VOD_PREFIX = "vod:";

    static {
        HEADERS.put("User-Agent", USER_AGENT);
        HEADERS.put("Accept", "*/*");
        HEADERS.put("Origin", "https://www.twitch.tv");
        HEADERS.put("Referer", "https://www.twitch.tv/");
    }

    private static final String STREAM_FIELDS = "id title viewersCount createdAt "
            + "previewImageURL(width: 640, height: 360) "
            + "broadcaster { login displayName profileImageURL(width: 150) } "
            + "game { name displayName }";
    private static final String USER_FIELDS = "login displayName description profileImageURL(width: 150) "
            + "stream { " + STREAM_FIELDS + " }";
    private static final String VIDEO_FIELDS = "id title lengthSeconds publishedAt viewCount "
            + "previewThumbnailURL(width: 640, height: 360) game { displayName } "
            + "owner { login displayName profileImageURL(width: 150) }";

    private static final String TOKEN_QUERY = "query PlaybackAccessToken_Template($login: String!, $isLive: Boolean!, "
            + "$vodID: ID!, $isVod: Boolean!, $playerType: String!) { "
            + "streamPlaybackAccessToken(channelName: $login, params: {platform: \"web\", playerBackend: \"mediaplayer\", "
            + "playerType: $playerType}) @include(if: $isLive) { value signature __typename } "
            + "videoPlaybackAccessToken(id: $vodID, params: {platform: \"web\", playerBackend: \"mediaplayer\", "
            + "playerType: $playerType}) @include(if: $isVod) { value signature __typename } }";

    private static volatile String sAuthToken;
    private static volatile List<Game> sSeenGames = new ArrayList<>();

    private TwitchApi() {
    }

    private static volatile Runnable sAuthRejectedListener;

    /** Called (on any thread) when Twitch refuses the stored login token; the token is dropped before it runs. */
    public static void setAuthRejectedListener(Runnable listener) {
        sAuthRejectedListener = listener;
    }

    /** The signed-in account's OAuth token, or null when signed out. */
    public static void setAuthToken(String token) {
        sAuthToken = token != null && !token.isEmpty() ? token : null;
    }

    public static boolean isSignedIn() {
        return sAuthToken != null;
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>(HEADERS);
        headers.put("Client-ID", sClientId);
        String token = sAuthToken;

        if (token != null) {
            headers.put("Authorization", "OAuth " + token);
        }

        return headers;
    }

    /**
     * Reads the client id the twitch.tv web page itself uses. Done once, only after Twitch said the id is invalid.
     * @return true if a different id was found
     */
    private static boolean refreshClientId() {
        if (sClientIdRefreshed) {
            return false;
        }

        sClientIdRefreshed = true;

        try {
            Map<String, String> page = new HashMap<>();
            page.put("User-Agent", USER_AGENT);
            String html = ProviderHttp.get("https://www.twitch.tv", page);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Client-ID\":\"([A-Za-z0-9]{20,40})\"").matcher(html);

            if (m.find() && !m.group(1).equals(sClientId)) {
                Log.d(TAG, "Using the client id found on twitch.tv");
                sClientId = m.group(1);
                return true;
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Could not read the client id from twitch.tv: " + e.getMessage());
        }

        return false;
    }

    // Models

    /** One page of a list. {@code cursor} is what to ask for the next page, null when there is none. */
    public static final class Page {
        public final List<ProviderMediaItem> items = new ArrayList<>();
        public String cursor;
    }

    public static final class Game {
        public String name;
        public String displayName;
    }

    public static final class User {
        public String login;
        public String displayName;
        public String description;
        public String avatarUrl;
        /** The live stream card of the channel, null when the channel is offline. */
        public ProviderMediaItem live;

        public ProviderMediaItem toChannelItem() {
            return ProviderMediaItem.channel(login, displayName != null ? displayName : login, avatarUrl);
        }
    }

    public static final class Vod {
        public String id;
        public String title;
        public String game;
        public String channelLogin;
        public String channelName;
        public String channelAvatar;
        public long lengthSec;
        public long publishedMs;
        public long views;
        public ProviderMediaItem item;
    }

    // Lists

    public static Page topStreams(String after) throws IOException {
        JSONObject data = query("query { streams(first: " + PAGE_SIZE + afterArg(after) + ") { edges { cursor node { " + STREAM_FIELDS + " } } } }");
        return streamPage(data.optJSONObject("streams"));
    }

    /**
     * The busiest categories. If Twitch refuses the categories query, the categories of the most watched streams are
     * used instead, so the Home and Gaming rows still appear.
     */
    public static List<Game> topGames(int count) throws IOException {
        try {
            return loadTopGames(count);
        } catch (IOException e) {
            Log.e(TAG, "Categories query failed, using the live list instead: " + e.getMessage());
            topStreams(null);
            List<Game> seen = sSeenGames;
            return seen.subList(0, Math.min(count, seen.size()));
        }
    }

    private static List<Game> loadTopGames(int count) throws IOException {
        JSONObject data = query("query { games(first: " + count + ") { edges { node { name displayName } } } }");
        List<Game> games = new ArrayList<>();
        JSONArray edges = edgesOf(data.optJSONObject("games"));

        for (int i = 0; i < edges.length(); i++) {
            JSONObject node = edges.optJSONObject(i) != null ? edges.optJSONObject(i).optJSONObject("node") : null;

            if (node != null && str(node, "name") != null) {
                Game game = new Game();
                game.name = str(node, "name");
                game.displayName = str(node, "displayName") != null ? str(node, "displayName") : game.name;
                games.add(game);
            }
        }

        return games;
    }

    public static Page gameStreams(String gameName, String after) throws IOException {
        JSONObject data = query("query { game(name: " + q(gameName) + ") { streams(first: " + PAGE_SIZE
                + afterArg(after) + ") { edges { cursor node { " + STREAM_FIELDS + " } } } } }");
        JSONObject game = data.optJSONObject("game");
        return streamPage(game != null ? game.optJSONObject("streams") : null);
    }

    /**
     * Past videos of a channel.
     * @param type ARCHIVE (past broadcasts), HIGHLIGHT or UPLOAD
     */
    public static Page videos(String login, String type, String after) throws IOException {
        JSONObject data = query("query { user(login: " + q(login) + ") { videos(first: " + PAGE_SIZE + ", type: " + type
                + afterArg(after) + ") { edges { cursor node { " + VIDEO_FIELDS + " } } } } }");
        JSONObject user = data.optJSONObject("user");
        JSONObject videos = user != null ? user.optJSONObject("videos") : null;
        Page page = new Page();
        JSONArray edges = edgesOf(videos);

        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            JSONObject node = edge != null ? edge.optJSONObject("node") : null;

            if (node != null) {
                page.items.add(parseVod(node).item);
                page.cursor = str(edge, "cursor");
            }
        }

        if (page.items.size() < PAGE_SIZE) {
            page.cursor = null; // short page = the last one
        }

        return page;
    }

    // Single items

    public static User user(String login) throws IOException {
        JSONObject data = query("query { user(login: " + q(login) + ") { " + USER_FIELDS + " } }");
        return parseUser(data.optJSONObject("user"));
    }

    /** Channels by login. Channels that don't exist are skipped. */
    public static List<User> users(List<String> logins) throws IOException {
        List<User> result = new ArrayList<>();

        for (int from = 0; from < logins.size(); from += 30) {
            StringBuilder list = new StringBuilder();

            for (String login : logins.subList(from, Math.min(logins.size(), from + 30))) {
                list.append(list.length() > 0 ? ", " : "").append(q(login.toLowerCase(Locale.US)));
            }

            JSONObject data = query("query { users(logins: [" + list + "]) { " + USER_FIELDS + " } }");
            JSONArray array = data.optJSONArray("users");

            for (int i = 0; array != null && i < array.length(); i++) {
                User user = parseUser(array.optJSONObject(i));

                if (user != null) {
                    result.add(user);
                }
            }
        }

        return result;
    }

    /**
     * Logins of the channels the signed-in account follows (up to 300).
     */
    public static List<String> followedLogins() throws IOException {
        List<String> logins = new ArrayList<>();
        String after = null;

        for (int page = 0; page < 3; page++) {
            JSONObject data = query("query { currentUser { login follows(first: 100" + afterArg(after)
                    + ") { edges { cursor node { login } } } } }");
            JSONObject me = data.optJSONObject("currentUser");

            if (me == null) {
                throw new IOException("Twitch did not accept the sign-in. Sign in again.");
            }

            JSONArray edges = edgesOf(me.optJSONObject("follows"));

            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                JSONObject node = edge != null ? edge.optJSONObject("node") : null;

                if (node != null && str(node, "login") != null) {
                    logins.add(str(node, "login").toLowerCase(Locale.US));
                    after = str(edge, "cursor");
                }
            }

            if (edges.length() < 100) {
                break;
            }
        }

        return logins;
    }

    public static Vod vod(String id) throws IOException {
        JSONObject data = query("query { video(id: " + q(id) + ") { " + VIDEO_FIELDS + " description } }");
        JSONObject video = data.optJSONObject("video");
        return video != null ? parseVod(video) : null;
    }

    /**
     * Channels matching the text. Twitch's search query has changed over time, so when it is refused the text is
     * tried as an exact channel name instead.
     */
    public static List<User> searchChannels(String text) throws IOException {
        List<User> found = new ArrayList<>();

        try {
            JSONObject data = query("query { searchFor(userQuery: " + q(text) + ", platform: \"web\", "
                    + "target: {index: CHANNEL}) { channels { edges { item { ... on User { " + USER_FIELDS + " } } } } } }");
            JSONObject channels = data.optJSONObject("searchFor") != null ? data.optJSONObject("searchFor").optJSONObject("channels") : null;
            JSONArray edges = edgesOf(channels);

            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                User user = parseUser(edge != null ? edge.optJSONObject("item") : null);

                if (user != null) {
                    found.add(user);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Channel search failed, trying an exact name: " + e.getMessage());
        }

        if (found.isEmpty()) {
            List<String> exact = new ArrayList<>();
            exact.add(text.trim().replace(" ", "").toLowerCase(Locale.US));
            found.addAll(users(exact));
        }

        return found;
    }

    // Playback

    /** HLS playlist of a live channel (all qualities). */
    public static String liveUrl(String login) throws IOException {
        String[] token = accessToken(true, login);
        return USHER_URL + "/api/v2/channel/hls/" + login.toLowerCase(Locale.US) + ".m3u8?" + usherParams(token);
    }

    /** HLS playlist of a past video (all qualities). */
    public static String vodUrl(String id) throws IOException {
        String[] token = accessToken(false, id);
        return USHER_URL + "/vod/" + id + ".m3u8?" + usherParams(token);
    }

    private static String usherParams(String[] token) throws IOException {
        try {
            return "allow_source=true&allow_audio_only=true&player_backend=mediaplayer&fast_bread=false&playlist_include_framerate=true"
                    + "&supported_codecs=avc1&p=" + (100000 + new Random().nextInt(9000000))
                    + "&sig=" + URLEncoder.encode(token[1], "UTF-8")
                    + "&token=" + URLEncoder.encode(token[0], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
    }

    /**
     * @return {token value, signature}
     */
    private static String[] accessToken(boolean live, String id) throws IOException {
        IOException lastError = null;

        for (String playerType : new String[]{"site", "embed"}) {
            try {
                JSONObject variables = new JSONObject();
                variables.put("isLive", live);
                variables.put("login", live ? id.toLowerCase(Locale.US) : "");
                variables.put("isVod", !live);
                variables.put("vodID", live ? "" : id);
                variables.put("playerType", playerType);

                JSONObject data = query(TOKEN_QUERY, variables);
                JSONObject token = data.optJSONObject(live ? "streamPlaybackAccessToken" : "videoPlaybackAccessToken");

                if (token != null && str(token, "value") != null && str(token, "signature") != null) {
                    return new String[]{str(token, "value"), str(token, "signature")};
                }

                lastError = new IOException("Twitch gave no access token");
            } catch (JSONException e) {
                lastError = new IOException(e);
            } catch (IOException e) {
                lastError = e;
            }
        }

        throw lastError != null ? lastError : new IOException("Twitch gave no access token");
    }

    // GraphQL plumbing

    private static JSONObject query(String query) throws IOException {
        return query(query, null);
    }

    /**
     * Runs a query and returns its "data" object. Throws when Twitch answers with errors and no data.
     */
    private static JSONObject query(String query, JSONObject variables) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);

            if (variables != null) {
                body.put("variables", variables);
            }

            JSONObject root = new JSONObject(post(body.toString()));
            JSONObject data = root.optJSONObject("data");
            JSONArray errors = root.optJSONArray("errors");

            if (errors != null && errors.length() > 0 && (data == null || allNull(data))) {
                JSONObject first = errors.optJSONObject(0);
                throw new IOException("Twitch: " + (first != null ? first.optString("message", "query refused") : "query refused"));
            }

            if (data == null) {
                throw new IOException("Twitch: unexpected answer" + (root.has("message") ? " (" + root.optString("message") + ")" : ""));
            }

            return data;
        } catch (HttpStatusException e) {
            // Twitch says what is wrong with the request in the body: keep it so it can be read on screen
            throw new IOException("Twitch refused the request (HTTP " + e.code + ")"
                    + (e.body != null && !e.body.isEmpty() ? ": " + e.body : ""), e);
        } catch (JSONException e) {
            throw new IOException("Twitch: unreadable answer", e);
        }
    }

    /**
     * Sends the query. If Twitch refuses the login token, the token is dropped and the query is repeated as a
     * guest, so browsing keeps working with a bad or expired sign-in.
     */
    private static String post(String body) throws IOException {
        try {
            return ProviderHttp.postJson(GQL_URL, body, headers());
        } catch (HttpStatusException e) {
            if (e.code == 400 && e.body != null && e.body.contains("Client-ID") && refreshClientId()) {
                return post(body); // same query with the client id found on twitch.tv
            }

            if (e.code != 401 || sAuthToken == null) {
                throw e;
            }

            Log.e(TAG, "Twitch refused the login token, continuing as a guest: " + e.getMessage());
            sAuthToken = null;
            Runnable listener = sAuthRejectedListener;

            if (listener != null) {
                listener.run();
            }

            return ProviderHttp.postJson(GQL_URL, body, headers());
        }
    }

    private static boolean allNull(JSONObject data) {
        java.util.Iterator<String> keys = data.keys();

        while (keys.hasNext()) {
            if (!data.isNull(keys.next())) {
                return false;
            }
        }

        return true;
    }

    private static String afterArg(String after) {
        return after != null ? ", after: " + q(after) : "";
    }

    /** A GraphQL string literal (JSON strings are valid ones). */
    private static String q(String value) {
        return JSONObject.quote(value != null ? value : "");
    }

    // Parsing

    private static JSONArray edgesOf(JSONObject connection) {
        JSONArray edges = connection != null ? connection.optJSONArray("edges") : null;
        return edges != null ? edges : new JSONArray();
    }

    private static Page streamPage(JSONObject streams) {
        Page page = new Page();
        JSONArray edges = edgesOf(streams);

        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            ProviderMediaItem item = streamItem(edge != null ? edge.optJSONObject("node") : null);

            if (item != null) {
                page.items.add(item);
                page.cursor = str(edge, "cursor");
            }
        }

        if (edges.length() < PAGE_SIZE) {
            page.cursor = null; // short page = the last one
        }

        return page;
    }

    /** A live stream card. Null when the data has no channel. */
    private static ProviderMediaItem streamItem(JSONObject stream) {
        JSONObject broadcaster = stream != null ? stream.optJSONObject("broadcaster") : null;
        String login = broadcaster != null ? str(broadcaster, "login") : null;

        if (login == null) {
            return null;
        }

        String name = str(broadcaster, "displayName") != null ? str(broadcaster, "displayName") : login;
        JSONObject game = stream.optJSONObject("game");
        String gameName = game != null ? str(game, "displayName") : null;
        rememberGame(game);
        long viewers = stream.optLong("viewersCount");

        ProviderMediaItem item = ProviderMediaItem.video(LIVE_PREFIX + login.toLowerCase(Locale.US));
        item.title = str(stream, "title") != null ? str(stream, "title") : name + " is live";
        item.author = name;
        item.channelId = login.toLowerCase(Locale.US);
        item.live = true;
        item.cardImageUrl = str(stream, "previewImageURL");
        item.backgroundImageUrl = item.cardImageUrl;
        item.publishedMs = parseDate(str(stream, "createdAt"));

        StringBuilder second = new StringBuilder(name);
        second.append(" • ").append(String.format(Locale.US, "%,d", viewers)).append(" watching");

        if (gameName != null) {
            second.append(" • ").append(gameName);
        }

        item.secondTitle = second.toString();
        return item;
    }

    private static void rememberGame(JSONObject game) {
        String name = game != null ? str(game, "name") : null;

        if (name == null) {
            return;
        }

        for (Game seen : sSeenGames) {
            if (seen.name.equals(name)) {
                return;
            }
        }

        Game added = new Game();
        added.name = name;
        added.displayName = str(game, "displayName") != null ? str(game, "displayName") : name;
        List<Game> copy = new ArrayList<>(sSeenGames);
        copy.add(added);
        sSeenGames = copy;
    }

    private static User parseUser(JSONObject json) {
        String login = json != null ? str(json, "login") : null;

        if (login == null) {
            return null;
        }

        User user = new User();
        user.login = login.toLowerCase(Locale.US);
        user.displayName = str(json, "displayName") != null ? str(json, "displayName") : login;
        user.description = str(json, "description");
        user.avatarUrl = str(json, "profileImageURL");
        user.live = streamItem(json.optJSONObject("stream"));
        return user;
    }

    private static Vod parseVod(JSONObject video) {
        Vod vod = new Vod();
        JSONObject owner = video.optJSONObject("owner");
        JSONObject game = video.optJSONObject("game");

        vod.id = str(video, "id");
        vod.title = str(video, "title") != null ? str(video, "title") : "Untitled broadcast";
        vod.game = game != null ? str(game, "displayName") : null;
        vod.channelLogin = owner != null && str(owner, "login") != null ? str(owner, "login").toLowerCase(Locale.US) : null;
        vod.channelName = owner != null && str(owner, "displayName") != null ? str(owner, "displayName") : vod.channelLogin;
        vod.channelAvatar = owner != null ? str(owner, "profileImageURL") : null;
        vod.lengthSec = video.optLong("lengthSeconds");
        vod.publishedMs = parseDate(str(video, "publishedAt"));
        vod.views = video.optLong("viewCount");

        ProviderMediaItem item = ProviderMediaItem.video(VOD_PREFIX + vod.id);
        item.title = vod.title;
        item.author = vod.channelName;
        item.channelId = vod.channelLogin;
        item.durationMs = vod.lengthSec * 1000;
        item.publishedMs = vod.publishedMs;
        item.cardImageUrl = str(video, "previewThumbnailURL");
        item.backgroundImageUrl = item.cardImageUrl;
        item.description = str(video, "description");
        item.secondTitle = ProviderMediaItem.buildSecondTitle(vod.channelName, vod.publishedMs);
        vod.item = item;
        return vod;
    }

    /** The value of a string field, or null when missing, JSON null or empty. */
    private static String str(JSONObject json, String key) {
        if (json == null || json.isNull(key)) {
            return null;
        }

        String value = json.optString(key, null);
        return value != null && !value.isEmpty() ? value : null;
    }

    private static long parseDate(String iso) {
        if (iso == null) {
            return 0;
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso.replaceAll("\\.\\d+", ""));
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }
}
