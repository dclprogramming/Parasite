package com.liskovsoft.smartyoutubetv2.common.providers.model;

import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Plain {@link MediaItem} used by non-YouTube providers (a video or a channel card).
 */
public class ProviderMediaItem implements MediaItem {
    public static final String CHANNEL_KEY_PREFIX = "provider:channel:";

    public int type = TYPE_VIDEO;
    public String videoId;
    public String channelId;
    public String title;
    public String author;
    public CharSequence secondTitle;
    public String cardImageUrl;
    public String backgroundImageUrl;
    public String description;
    public long durationMs;
    public long publishedMs;
    public boolean live;

    public static ProviderMediaItem video(String videoId) {
        ProviderMediaItem item = new ProviderMediaItem();
        item.type = TYPE_VIDEO;
        item.videoId = videoId;
        return item;
    }

    public static ProviderMediaItem channel(String channelId, String name, String thumbnailUrl) {
        ProviderMediaItem item = new ProviderMediaItem();
        item.type = TYPE_CHANNEL;
        item.channelId = channelId;
        item.title = name;
        item.author = name;
        item.cardImageUrl = thumbnailUrl;
        item.backgroundImageUrl = thumbnailUrl;
        return item;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        try {
            json.put("type", type);
            json.put("videoId", videoId);
            json.put("channelId", channelId);
            json.put("title", title);
            json.put("author", author);
            json.put("thumb", cardImageUrl);
            json.put("durationMs", durationMs);
            json.put("publishedMs", publishedMs);
        } catch (JSONException e) {
            // NOP. Can't happen for these value types
        }

        return json;
    }

    public static ProviderMediaItem fromJson(JSONObject json) {
        ProviderMediaItem item = new ProviderMediaItem();

        item.type = json.optInt("type", TYPE_VIDEO);
        item.videoId = json.isNull("videoId") ? null : json.optString("videoId", null);
        item.channelId = json.isNull("channelId") ? null : json.optString("channelId", null);
        item.title = json.isNull("title") ? null : json.optString("title", null);
        item.author = json.isNull("author") ? null : json.optString("author", null);
        item.cardImageUrl = json.isNull("thumb") ? null : json.optString("thumb", null);
        item.backgroundImageUrl = item.cardImageUrl;
        item.durationMs = json.optLong("durationMs");
        item.publishedMs = json.optLong("publishedMs");
        item.secondTitle = buildSecondTitle(item.author, item.publishedMs);

        return item;
    }

    /**
     * E.g. "Some Channel • 3 days ago"
     */
    public static String buildSecondTitle(String author, long publishedMs) {
        String ago = publishedMs > 0 ? timeAgo(publishedMs) : null;

        if (author != null && ago != null) {
            return author + " • " + ago;
        }

        return author != null ? author : ago;
    }

    public static String timeAgo(long publishedMs) {
        long diffSec = Math.max(0, (System.currentTimeMillis() - publishedMs) / 1000);

        if (diffSec < 3600) {
            return plural(Math.max(1, diffSec / 60), "minute");
        } else if (diffSec < 86400) {
            return plural(diffSec / 3600, "hour");
        } else if (diffSec < 86400L * 30) {
            return plural(diffSec / 86400, "day");
        } else if (diffSec < 86400L * 365) {
            return plural(diffSec / (86400L * 30), "month");
        }

        return plural(diffSec / (86400L * 365), "year");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s") + " ago";
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public boolean isLive() {
        return live;
    }

    @Override
    public boolean isUpcoming() {
        return false;
    }

    @Override
    public boolean isShorts() {
        return false;
    }

    @Override
    public int getPercentWatched() {
        return 0;
    }

    @Override
    public int getStartTimeSeconds() {
        return 0;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getFeedbackToken() {
        return null;
    }

    @Override
    public String getFeedbackToken2() {
        return null;
    }

    @Override
    public FeedbackEndpoint getFeedbackEndpoint() {
        return null;
    }

    @Override
    public String getPlaylistId() {
        return null;
    }

    @Override
    public int getPlaylistIndex() {
        return 0;
    }

    @Override
    public String getParams() {
        return null;
    }

    @Override
    public String getReloadPageKey() {
        return type == TYPE_CHANNEL && channelId != null ? CHANNEL_KEY_PREFIX + channelId : null;
    }

    @Override
    public boolean hasNewContent() {
        return false;
    }

    @Override
    public int getId() {
        String key = type == TYPE_CHANNEL ? channelId : videoId;
        return key != null ? key.hashCode() : 0;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public CharSequence getSecondTitle() {
        return secondTitle;
    }

    @Override
    public String getVideoId() {
        return videoId;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String getBadgeText() {
        return null;
    }

    @Override
    public String getProductionDate() {
        return null;
    }

    @Override
    public long getPublishedDate() {
        return publishedMs;
    }

    @Override
    public String getCardImageUrl() {
        return cardImageUrl;
    }

    @Override
    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String getVideoPreviewUrl() {
        return null;
    }

    @Override
    public String getAudioChannelConfig() {
        return null;
    }

    @Override
    public String getPurchasePrice() {
        return null;
    }

    @Override
    public String getRentalPrice() {
        return null;
    }

    @Override
    public int getRatingStyle() {
        return 0;
    }

    @Override
    public double getRatingScore() {
        return 0;
    }

    @Override
    public boolean isMovie() {
        return false;
    }

    @Override
    public boolean hasUploads() {
        return type == TYPE_CHANNEL;
    }

    @Override
    public String getClickTrackingParams() {
        return null;
    }

    @Override
    public String getSearchQuery() {
        return null;
    }
}
