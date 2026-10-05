package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.ProviderHttp;
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
    /** A page with fewer videos than this is the last one. */
    public static final int MIN_FULL_PAGE = 8;

    private RumbleApi() {
    }

    /**
     * Videos listed on a Rumble page (browse, category, channel or search). Page numbers start from 1.
     */
    public static List<RumbleParser.Entry> listing(String path, int page) throws IOException {
        String url = SITE + path;

        if (page > 1) {
            url += (path.contains("?") ? "&" : "?") + "page=" + page;
        }

        return RumbleParser.parseListing(ProviderHttp.get(url));
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
        return RumbleParser.parseChannels(ProviderHttp.get(SITE + "/search/channel?q=" + encode(text)));
    }

    /**
     * Name and picture of a channel, read from its page.
     */
    public static ProviderMediaItem channelInfo(String channelId) throws IOException {
        return RumbleParser.parseChannelPage(channelId, ProviderHttp.get(SITE + channelPath(channelId)));
    }

    /**
     * Metadata and stream urls of a video. {@code videoId} is the short embed id, e.g. "v7cwvbs".
     */
    public static RumbleParser.Stream stream(String videoId) throws IOException {
        String json = ProviderHttp.get(SITE + "/embedJS/u3/?request=video&ver=2&v=" + encode(videoId));
        RumbleParser.Stream stream = RumbleParser.parseEmbed(json);

        if (stream == null) {
            throw new IOException("Unexpected Rumble response for " + videoId);
        }

        return stream;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
