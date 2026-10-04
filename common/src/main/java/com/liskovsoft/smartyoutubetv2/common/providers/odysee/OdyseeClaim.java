package com.liskovsoft.smartyoutubetv2.common.providers.odysee;

import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import org.json.JSONObject;

/**
 * One LBRY claim (video or channel) as returned by claim_search.
 */
public class OdyseeClaim {
    public String claimId;
    public String name;
    public String title;
    public String description;
    public String thumbnailUrl;
    public String sdHash;
    public String mediaType;
    public boolean isChannel;
    public boolean isPaid;
    public long durationSec;
    public long releaseTimeSec;
    public String channelId;
    public String channelName;
    public String channelTitle;
    public String channelThumbnailUrl;

    public static OdyseeClaim from(JSONObject json) {
        if (json == null) {
            return null;
        }

        OdyseeClaim claim = new OdyseeClaim();
        JSONObject value = json.optJSONObject("value");

        claim.claimId = optString(json, "claim_id");
        claim.name = optString(json, "name");
        claim.isChannel = "channel".equals(optString(json, "value_type"));

        if (claim.claimId == null) {
            return null;
        }

        if (value != null) {
            claim.title = optString(value, "title");
            claim.description = optString(value, "description");
            JSONObject thumbnail = value.optJSONObject("thumbnail");
            claim.thumbnailUrl = thumbnail != null ? optString(thumbnail, "url") : null;
            JSONObject source = value.optJSONObject("source");
            if (source != null) {
                claim.sdHash = optString(source, "sd_hash");
                claim.mediaType = optString(source, "media_type");
            }
            JSONObject video = value.optJSONObject("video");
            claim.durationSec = video != null ? video.optLong("duration") : 0;
            claim.isPaid = value.optJSONObject("fee") != null;
            claim.releaseTimeSec = parseLong(optString(value, "release_time"));
        }

        if (claim.releaseTimeSec <= 0) {
            claim.releaseTimeSec = json.optLong("timestamp");
        }

        JSONObject channel = json.optJSONObject("signing_channel");

        if (channel != null) {
            claim.channelId = optString(channel, "claim_id");
            claim.channelName = optString(channel, "name");
            JSONObject channelValue = channel.optJSONObject("value");

            if (channelValue != null) {
                claim.channelTitle = optString(channelValue, "title");
                JSONObject channelThumb = channelValue.optJSONObject("thumbnail");
                claim.channelThumbnailUrl = channelThumb != null ? optString(channelThumb, "url") : null;
            }
        }

        if (claim.isChannel) {
            claim.channelId = claim.claimId;
            claim.channelName = claim.name;
            claim.channelTitle = claim.title;
            claim.channelThumbnailUrl = claim.thumbnailUrl;
        }

        return claim;
    }

    public String getAuthor() {
        if (channelTitle != null && !channelTitle.isEmpty()) {
            return channelTitle;
        }

        return channelName != null ? channelName.replaceFirst("^@", "") : null;
    }

    public String getTitle() {
        return title != null && !title.isEmpty() ? title : name;
    }

    public boolean isPlayable() {
        return !isChannel && !isPaid && sdHash != null && sdHash.length() >= 6;
    }

    public ProviderMediaItem toMediaItem() {
        if (isChannel) {
            ProviderMediaItem channel = ProviderMediaItem.channel(claimId, getTitle(), thumbnailUrl);
            channel.secondTitle = name;
            return channel;
        }

        ProviderMediaItem item = ProviderMediaItem.video(claimId);
        item.title = getTitle();
        item.author = getAuthor();
        item.channelId = channelId;
        item.cardImageUrl = thumbnailUrl;
        item.backgroundImageUrl = thumbnailUrl;
        item.description = description;
        item.durationMs = durationSec * 1000;
        item.publishedMs = releaseTimeSec * 1000;
        item.secondTitle = ProviderMediaItem.buildSecondTitle(item.author, item.publishedMs);
        return item;
    }

    private static String optString(JSONObject json, String key) {
        if (json.isNull(key)) {
            return null;
        }

        String result = json.optString(key, null);
        return result != null && !result.isEmpty() ? result : null;
    }

    private static long parseLong(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
