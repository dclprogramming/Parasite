package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rumble has no public API: listings are read from the site's HTML, streams from its embed JSON.<br/>
 * Parsing is deliberately tolerant: a missing detail (title, thumbnail, date...) never drops the video.
 */
public final class RumbleParser {
    private static final Pattern VIDEO_HREF = Pattern.compile("href=\"(?:https?://rumble\\.com)?/(v[0-9a-z]{3,12})-([^\"?#]*)\\.html[^\"]*\"");
    private static final Pattern ITEM_SPLIT = Pattern.compile("(?=<(?:div|li|article)[^>]*class=\"[^\"]*\\bvideostream\\b(?![_-])[^\"]*\")");
    private static final Pattern ANCHOR_VIDEO = Pattern.compile("<a\\b[^>]*href=\"(?:https?://rumble\\.com)?/(v[0-9a-z]{3,12})-([^\"?#]*)\\.html[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern CHANNEL_HREF = Pattern.compile("href=\"(?:https?://rumble\\.com)?/(c|user)/([^\"/?#]+)[^\"]*\"");
    private static final Pattern CHANNEL_ANCHOR = Pattern.compile("<a\\b[^>]*href=\"(?:https?://rumble\\.com)?/(c|user)/([^\"/?#]+)[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern IMG_SRC = Pattern.compile("(?:data-src|src)=\"(https?:[^\"]+)\"");
    private static final Pattern TITLE_H3 = Pattern.compile("<h3\\b[^>]*thumbnail__title[^>]*>(.*?)</h3>", Pattern.DOTALL);
    private static final Pattern ANY_H3 = Pattern.compile("<h3\\b[^>]*>(.*?)</h3>", Pattern.DOTALL);
    private static final Pattern DURATION = Pattern.compile("<span[^>]*duration[^>]*>\\s*((?:\\d+:)?\\d+:\\d{2})\\s*</span>");
    private static final Pattern CHANNEL_NAME = Pattern.compile("channel__name[^>]*>(.*?)</(?:div|span|a)>", Pattern.DOTALL);
    private static final Pattern DATETIME = Pattern.compile("datetime=\"([^\"]+)\"");
    private static final Pattern OG_IMAGE = Pattern.compile("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"");
    private static final Pattern OG_TITLE = Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"");
    private static final Pattern PAGE_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private RumbleParser() {
    }

    /** One video found in a listing. */
    public static class Entry {
        public String id;
        public String title;
        public String thumb;
        public String channelId; // "c/Name" or "user/Name"
        public String channelName;
        public long durationSec;
        public long publishedMs;

        public ProviderMediaItem toMediaItem() {
            ProviderMediaItem item = ProviderMediaItem.video(id);
            item.title = title;
            item.author = channelName;
            item.channelId = channelId;
            item.cardImageUrl = thumb;
            item.backgroundImageUrl = thumb;
            item.durationMs = durationSec * 1000;
            item.publishedMs = publishedMs;
            item.secondTitle = ProviderMediaItem.buildSecondTitle(channelName, publishedMs);
            return item;
        }
    }

    /** Playback info from the embed JSON. */
    public static class Stream {
        public String title;
        public String authorName;
        public String channelId;
        public String thumb;
        public long durationSec;
        public long publishedMs;
        public boolean live;
        public String mp4Url; // best mp4 not above 1080p
        public String hlsUrl;

        public ProviderMediaItem toMediaItem(String videoId) {
            ProviderMediaItem item = ProviderMediaItem.video(videoId);
            item.title = title;
            item.author = authorName;
            item.channelId = channelId;
            item.cardImageUrl = thumb;
            item.backgroundImageUrl = thumb;
            item.durationMs = durationSec * 1000;
            item.publishedMs = publishedMs;
            item.secondTitle = ProviderMediaItem.buildSecondTitle(authorName, publishedMs);
            return item;
        }
    }

    // Listings

    public static List<Entry> parseListing(String html) {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (html == null) {
            return result;
        }

        for (String chunk : ITEM_SPLIT.split(html)) {
            Matcher link = VIDEO_HREF.matcher(chunk);

            if (!link.find()) {
                continue;
            }

            Entry entry = parseChunk(chunk, link.group(1), link.group(2));

            if (seen.add(entry.id)) {
                result.add(entry);
            }
        }

        addMissingByWindows(html, seen, result); // pages whose items don't use the usual class names

        if (result.isEmpty()) { // markup changed: fall back to plain links
            Matcher anchor = ANCHOR_VIDEO.matcher(html);

            while (anchor.find()) {
                String id = anchor.group(1);

                if (!seen.add(id)) {
                    continue;
                }

                Entry entry = new Entry();
                entry.id = id;
                entry.title = firstNonEmpty(textOf(anchor.group(3)), titleFromSlug(anchor.group(2)));
                entry.thumb = firstImage(anchor.group(3));
                result.add(entry);
            }
        }

        return result;
    }

    /**
     * Every video link starts a window that ends at the next video's link. Used for videos
     * the class-based split did not find.
     */
    private static void addMissingByWindows(String html, Set<String> seen, List<Entry> result) {
        List<String> ids = new ArrayList<>();
        List<String> slugs = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher link = VIDEO_HREF.matcher(html);

        while (link.find()) {
            if (!ids.contains(link.group(1))) {
                ids.add(link.group(1));
                slugs.add(link.group(2));
                starts.add(link.start());
            }
        }

        for (int i = 0; i < ids.size(); i++) {
            if (seen.contains(ids.get(i))) {
                continue;
            }

            int end = i + 1 < ids.size() ? starts.get(i + 1) : Math.min(html.length(), starts.get(i) + 4000);
            Entry entry = parseChunk(html.substring(starts.get(i), end), ids.get(i), slugs.get(i));
            entry.durationSec = 0; // the duration badge sits before the link: it would belong to the previous video

            seen.add(entry.id);
            result.add(entry);
        }
    }

    private static Entry parseChunk(String chunk, String id, String slug) {
        Entry entry = new Entry();
        entry.id = id;

        Matcher h3 = TITLE_H3.matcher(chunk);
        String title = h3.find() ? textOf(h3.group(1)) : null;

        if (title == null) {
            Matcher any = ANY_H3.matcher(chunk);
            title = any.find() ? textOf(any.group(1)) : null;
        }

        if (title == null) { // no heading: use the text of the video link itself
            Matcher anchor = ANCHOR_VIDEO.matcher(chunk);

            while (title == null && anchor.find()) {
                title = textOf(anchor.group(3));
            }
        }

        entry.title = firstNonEmpty(title, titleFromSlug(slug));
        entry.thumb = firstImage(chunk);

        Matcher duration = DURATION.matcher(chunk);
        if (duration.find()) {
            entry.durationSec = parseDuration(duration.group(1));
        }

        Matcher channel = CHANNEL_HREF.matcher(chunk);
        if (channel.find()) {
            entry.channelId = channel.group(1) + "/" + channel.group(2);
            Matcher name = CHANNEL_NAME.matcher(chunk);
            entry.channelName = firstNonEmpty(name.find() ? textOf(name.group(1)) : null, channel.group(2));
        }

        Matcher date = DATETIME.matcher(chunk);
        if (date.find()) {
            entry.publishedMs = parseIsoDate(date.group(1));
        }

        return entry;
    }

    /**
     * Channel cards of the channel search page (and channel links in general). Deduplicated by channel.
     */
    public static List<ProviderMediaItem> parseChannels(String html) {
        List<ProviderMediaItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (html == null) {
            return result;
        }

        Matcher anchor = CHANNEL_ANCHOR.matcher(html);

        while (anchor.find()) {
            String channelId = anchor.group(1) + "/" + anchor.group(2);
            String inner = anchor.group(3);
            String thumb = firstImage(inner);
            String name = textOf(inner);

            if (thumb == null && name == null) {
                continue;
            }

            if (!seen.add(channelId)) {
                continue;
            }

            result.add(ProviderMediaItem.channel(channelId, firstNonEmpty(name, anchor.group(2)), thumb));
        }

        return result;
    }

    /**
     * Channel name and picture from the channel page header (Open Graph tags).
     */
    public static ProviderMediaItem parseChannelPage(String channelId, String html) {
        String name = null;
        String thumb = null;

        if (html != null) {
            Matcher image = OG_IMAGE.matcher(html);
            thumb = image.find() ? unescape(image.group(1)) : null;
            Matcher title = OG_TITLE.matcher(html);
            name = title.find() ? textOf(title.group(1)) : null;

            if (name == null) {
                Matcher page = PAGE_TITLE.matcher(html);
                name = page.find() ? textOf(page.group(1)) : null;
            }
        }

        if (name != null) {
            name = name.replaceAll("\\s*[|\\-–—]\\s*Rumble.*$", "").trim();
        }

        String fallbackName = channelId.substring(channelId.indexOf('/') + 1);
        return ProviderMediaItem.channel(channelId, firstNonEmpty(name, fallbackName), thumb);
    }

    // Embed JSON

    public static Stream parseEmbed(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Stream stream = new Stream();

            stream.title = textOf(root.optString("title", null));
            stream.thumb = root.isNull("i") ? null : root.optString("i", null);
            stream.durationSec = root.optLong("duration");
            stream.live = root.optInt("live") != 0;
            stream.publishedMs = parseIsoDate(root.isNull("pubDate") ? null : root.optString("pubDate", null));

            JSONObject author = root.optJSONObject("author");
            if (author != null) {
                stream.authorName = textOf(author.optString("name", null));
                stream.channelId = channelIdFromUrl(author.isNull("url") ? null : author.optString("url", null));
            }

            JSONObject ua = root.optJSONObject("ua"); // is an empty array when nothing can be played
            if (ua != null) {
                stream.mp4Url = bestUrl(ua.optJSONObject("mp4"));

                if (stream.mp4Url == null) {
                    stream.mp4Url = bestUrl(ua.optJSONObject("webm"));
                }

                JSONObject hls = ua.optJSONObject("hls");
                if (hls != null) {
                    stream.hlsUrl = bestUrl(hls);
                }
            }

            if (stream.mp4Url == null) { // layout differs: look for media links anywhere in the JSON
                MediaScan scan = new MediaScan();
                scan.visit(null, root);
                stream.mp4Url = scan.best(scan.mp4);

                if (stream.mp4Url == null) {
                    stream.mp4Url = scan.best(scan.webm);
                }
                if (stream.hlsUrl == null) {
                    stream.hlsUrl = scan.hls;
                }
            }

            if (stream.mp4Url == null) { // older embed format
                JSONObject u = root.optJSONObject("u");
                JSONObject mp4 = u != null ? u.optJSONObject("mp4") : null;
                stream.mp4Url = mp4 != null && !mp4.isNull("url") ? mp4.optString("url", null) : null;
            }

            return stream;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result of reading the embed page's own player: {"src": "...", "urls": [...], "title": "...", "image": "..."}.
     */
    public static Stream parseScrape(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Stream stream = new Stream();
            MediaScan scan = new MediaScan();
            org.json.JSONArray urls = root.optJSONArray("urls");

            for (int i = 0; urls != null && i < urls.length(); i++) {
                scan.add(null, 0, urls.optString(i, ""));
            }

            stream.mp4Url = scan.mp4.isEmpty() ? scan.best(scan.webm) : (String) scan.mp4.get(0)[1];
            stream.hlsUrl = scan.hls;
            stream.title = textOf(root.optString("title", null));

            if (stream.title != null) {
                stream.title = stream.title.replaceAll("\\s*[|\\-–—]\\s*Rumble.*$", "").trim();
            }

            stream.thumb = root.isNull("image") || root.optString("image", "").isEmpty() ? null : root.optString("image");
            return stream.mp4Url != null || stream.hlsUrl != null ? stream : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Collects media links from an unknown JSON layout: any string value that looks like a media url.
     */
    private static class MediaScan {
        final List<Object[]> mp4 = new ArrayList<>(); // {height, url}
        final List<Object[]> webm = new ArrayList<>();
        String hls;

        void visit(String key, Object node) {
            if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                JSONObject meta = object.optJSONObject("meta");
                int height = meta != null ? meta.optInt("h", 0) : 0;

                if (height == 0 && key != null) {
                    height = parseInt(key);
                }

                Iterator<String> keys = object.keys();

                while (keys.hasNext()) {
                    String child = keys.next();
                    Object value = object.opt(child);

                    if (value instanceof String) {
                        add(child.equals("url") ? key : child, height, (String) value);
                    } else {
                        visit(child, value);
                    }
                }
            } else if (node instanceof org.json.JSONArray) {
                org.json.JSONArray array = (org.json.JSONArray) node;

                for (int i = 0; i < array.length(); i++) {
                    visit(key, array.opt(i));
                }
            }
        }

        private void add(String key, int height, String value) {
            String lower = value.toLowerCase(Locale.US);

            if (!lower.startsWith("http")) {
                return;
            }

            int h = height > 0 ? height : parseInt(key != null ? key : "");

            if (lower.contains(".mp4")) {
                mp4.add(new Object[]{h, value});
            } else if (lower.contains(".webm")) {
                webm.add(new Object[]{h, value});
            } else if (lower.contains(".m3u8") && hls == null) {
                hls = value;
            }
        }

        /** Highest resolution up to 1080p, else the first one. */
        String best(List<Object[]> list) {
            String best = null;
            int bestHeight = -1;

            for (Object[] entry : list) {
                int h = (Integer) entry[0];

                if (h <= 1080 && h > bestHeight) {
                    bestHeight = h;
                    best = (String) entry[1];
                }
            }

            return best != null ? best : (list.isEmpty() ? null : (String) list.get(0)[1]);
        }
    }

    /**
     * Picks the highest resolution up to 1080p from {"720": {"url": ...}, "480": {...}} (keys are heights or names).
     */
    private static String bestUrl(JSONObject variants) {
        if (variants == null) {
            return null;
        }

        String best = null;
        int bestHeight = -1;
        String anyUrl = null;
        Iterator<String> keys = variants.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject variant = variants.optJSONObject(key);

            if (variant == null || variant.isNull("url")) {
                continue;
            }

            String url = variant.optString("url", null);

            if (url == null || url.isEmpty()) {
                continue;
            }

            anyUrl = url;
            JSONObject meta = variant.optJSONObject("meta");
            int height = meta != null ? meta.optInt("h", 0) : 0;

            if (height == 0) {
                height = parseInt(key);
            }

            if (height <= 1080 && height > bestHeight) {
                bestHeight = height;
                best = url;
            }
        }

        return best != null ? best : anyUrl;
    }

    // Helpers

    /**
     * "https://rumble.com/c/Name" -> "c/Name", "/user/Name" -> "user/Name"
     */
    public static String channelIdFromUrl(String url) {
        if (url == null) {
            return null;
        }

        Matcher matcher = CHANNEL_HREF.matcher("href=\"" + url + "\"");
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : null;
    }

    /**
     * Newest first. Entries without a date keep their relative order after the dated ones.
     */
    public static void sortNewestFirst(List<ProviderMediaItem> items) {
        Collections.sort(items, new Comparator<ProviderMediaItem>() {
            @Override
            public int compare(ProviderMediaItem a, ProviderMediaItem b) {
                return Long.compare(b.publishedMs, a.publishedMs);
            }
        });
    }

    static long parseDuration(String text) {
        long seconds = 0;

        for (String part : text.split(":")) {
            seconds = seconds * 60 + parseInt(part);
        }

        return seconds;
    }

    static long parseIsoDate(String value) {
        if (value == null) {
            return 0;
        }

        try {
            String normalized = value.trim().replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2").replaceFirst("Z$", "+0000");
            normalized = normalized.replaceFirst("\\.\\d+", ""); // drop fractions
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            Date date = format.parse(normalized);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    private static String firstImage(String html) {
        Matcher tags = IMG_TAG.matcher(html);
        String first = null;

        while (tags.find()) {
            String tag = tags.group();
            Matcher src = IMG_SRC.matcher(tag);

            if (!src.find()) {
                continue;
            }

            String url = unescape(src.group(1));

            if (tag.contains("thumbnail__image") || tag.contains("videostream__image")) {
                return url;
            }

            if (first == null) {
                first = url;
            }
        }

        return first;
    }

    private static String titleFromSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return null;
        }

        String text = slug.replace('-', ' ').trim();
        return text.isEmpty() ? null : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Strips tags, decodes entities, collapses whitespace. Returns null for blank text. */
    static String textOf(String html) {
        if (html == null) {
            return null;
        }

        String text = unescape(html.replaceAll("(?s)<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    static String unescape(String text) {
        return text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
