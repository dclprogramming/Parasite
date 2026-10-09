package com.liskovsoft.smartyoutubetv2.common.providers.model;

import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Video details shown under the player (title, channel, description) for non-YouTube providers.
 */
public class ProviderMetadata implements MediaItemMetadata {
    public String title;
    public CharSequence secondTitle;
    public String description;
    public String author;
    public String authorImageUrl;
    public String publishedDate;
    public String videoId;
    public String channelId;
    public boolean subscribed;
    public long durationMs;
    public boolean live;
    public MediaItem nextVideo;
    public final List<MediaGroup> suggestions = new ArrayList<>();

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public CharSequence getSecondTitle() {
        return secondTitle;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getAuthorImageUrl() {
        return authorImageUrl;
    }

    @Override
    public String getViewCount() {
        return null;
    }

    @Override
    public String getLikeCount() {
        return null;
    }

    @Override
    public String getDislikeCount() {
        return null;
    }

    @Override
    public String getSubscriberCount() {
        return null;
    }

    @Override
    public String getPublishedDate() {
        return publishedDate;
    }

    @Override
    public String getVideoId() {
        return videoId;
    }

    @Override
    public MediaItem getNextVideo() {
        return nextVideo;
    }

    @Override
    public MediaItem getShuffleVideo() {
        return null;
    }

    @Override
    public boolean isSubscribed() {
        return subscribed;
    }

    @Override
    public boolean isLive() {
        return live;
    }

    @Override
    public String getLiveChatKey() {
        return null;
    }

    @Override
    public String getCommentsKey() {
        return null;
    }

    @Override
    public boolean isUpcoming() {
        return false;
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String getParams() {
        return null;
    }

    @Override
    public int getPercentWatched() {
        return 0;
    }

    @Override
    public int getLikeStatus() {
        return LIKE_STATUS_INDIFFERENT;
    }

    @Override
    public List<MediaGroup> getSuggestions() {
        return suggestions;
    }

    @Override
    public PlaylistInfo getPlaylistInfo() {
        return null;
    }

    @Override
    public List<ChapterItem> getChapters() {
        return Collections.emptyList();
    }

    @Override
    public List<NotificationState> getNotificationStates() {
        return Collections.emptyList();
    }

    @Override
    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String getBadgeText() {
        return null;
    }
}
