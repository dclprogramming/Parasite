package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Rumble web access: HTML listing pages, and the embed JSON that holds the stream urls.
 */
public final class RumbleApi {
    private static final String SITE = "https://rumble.com";
    /** Runs inside the embed page: lists the media urls the page's player has loaded. Returns "" until there is one. */
    private static final String PLAYER_SCRIPT = "(function(){var o={src:'',urls:[],title:document.title||'',image:''};"
            + "var v=document.querySelector('video');"
            + "if(v){try{v.muted=true;v.play();}catch(e){}o.src=v.currentSrc||v.src||'';}"
            + "var b=document.querySelector('[class*=\"play-button\"],[class*=\"PlayButton\"],[class*=\"big-play\"],button[aria-label*=\"lay\"]');"
            + "if(b){try{b.click();}catch(e){}}"
            + "try{performance.getEntriesByType('resource').forEach(function(r){if(/\\.(mp4|m3u8|webm)(\\?|$)/i.test(r.name)){o.urls.push(r.name);}});}catch(e){}"
            + "if(o.src&&o.src.indexOf('blob:')!==0){o.urls.push(o.src);}"
            + "var m=document.querySelector('meta[property=\"og:image\"]');if(m){o.image=m.content;}"
            + "return o.urls.length?JSON.stringify(o):'';})()";
    /** A page with fewer videos than this is the last one. */
    public static final int MIN_FULL_PAGE = 8;

    private RumbleApi() {
    }

    /**
     * Videos listed on a Rumble page (browse, category, channel or search). Page numbers start from 1.
     */
    public static List<RumbleParser.Entry> listing(String path, int page) throws IOException {
        if (page > 1) {
            path += (path.contains("?") ? "&" : "?") + "page=" + page;
        }

        return RumbleParser.parseListing(RumbleHttp.get(path, SITE + "/", false));
    }

    public static String browsePath(String sort, String date) {
        return "/videos?sort=" + sort + "&date=" + date;
    }

    public static String categoryPath(String category) {
        return "/category/" + category;
    }

    public static String channelPath(String channelId) {
        return "/" + channelId;
    }

    public static String searchVideosPath(String text) {
        return "/search/video?q=" + encode(text);
    }

    public static List<ProviderMediaItem> searchChannels(String text) throws IOException {
        return RumbleParser.parseChannels(RumbleHttp.get("/search/channel?q=" + encode(text), SITE + "/", false));
    }

    /**
     * Name and picture of a channel, read from its page.
     */
    public static ProviderMediaItem channelInfo(String channelId) throws IOException {
        return RumbleParser.parseChannelPage(channelId, RumbleHttp.get(channelPath(channelId), SITE + "/", false));
    }

    /**
     * Metadata and stream urls of a video. {@code videoId} is the short embed id, e.g. "v7cwvbs".
     */
    public static RumbleParser.Stream stream(String videoId) throws IOException {
        StringBuilder problems = new StringBuilder();
        String embedId;

        try {
            embedId = embedIdOf(videoId);
        } catch (IOException e) {
            throw new IOException("watch page: " + e.getMessage());
        }

        for (String version : new String[]{"u4", "u3"}) {
            try {
                String json = RumbleHttp.get("/embedJS/" + version + "/?request=video&ver=2&v=" + encode(embedId), SITE + "/embed/" + embedId + "/", true);
                RumbleParser.Stream stream = RumbleParser.parseEmbed(json);

                if (stream != null && (stream.mp4Url != null || stream.hlsUrl != null)) {
                    return stream;
                }

                problems.append(version).append(": ").append(stream == null ? "not JSON (" + snippet(json) + ")" : "no stream in answer").append("; ");
            } catch (IOException e) {
                problems.append(version).append(": ").append(e.getMessage()).append("; ");
            }
        }

        // The API refused us: let the embed page's own player fetch the stream, and read it from there
        try {
            RumbleParser.Stream stream = RumbleParser.parseScrape(BrowserFetcher.scrape(SITE + "/embed/" + embedId + "/", PLAYER_SCRIPT, 25));

            if (stream != null) {
                return stream;
            }

            problems.append("player: no stream found");
        } catch (IOException e) {
            problems.append("player: ").append(e.getMessage());
        }

        throw new IOException(problems.toString());
    }

    /**
     * Like {@link #listing} but always through the hidden browser, so the signed-in session cookies are sent.
     */
    public static List<RumbleParser.Entry> listingSignedIn(String path, int page) throws IOException {
        if (page > 1) {
            path += (path.contains("?") ? "&" : "?") + "page=" + page;
        }

        return RumbleParser.parseListing(RumbleHttp.getWithSession(path));
    }

    /**
     * Does the home page show the signed-in header? Uses the browser session.
     */
    public static boolean looksSignedIn() throws IOException {
        String html = RumbleHttp.getWithSession("/").toLowerCase(java.util.Locale.US);
        return html.contains("/logout") || html.contains("sign out") || html.contains("log out");
    }

    private static final java.util.Map<String, String> EMBED_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Rumble's player id of a video. {@code videoId} is the page name from the watch address ("v7cwvbs-some-title").
     */
    static String embedIdOf(String videoId) throws IOException {
        String cached = EMBED_IDS.get(videoId);

        if (cached != null) {
            return cached;
        }

        String embedId = RumbleParser.findEmbedId(RumbleHttp.get("/" + videoId + ".html", SITE + "/", false));

        if (embedId == null) {
            throw new IOException("player id not found in the page");
        }

        EMBED_IDS.put(videoId, embedId);
        return embedId;
    }

    /** First characters of an answer, for error messages. */
    private static String snippet(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() > 70 ? flat.substring(0, 70) : flat;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
