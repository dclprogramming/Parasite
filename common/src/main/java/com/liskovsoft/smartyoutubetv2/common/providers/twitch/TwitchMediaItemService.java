package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.HttpStatusException;
import com.liskovsoft.smartyoutubetv2.common.providers.LocalContentBase;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMetadata;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubMediaItemService;

import io.reactivex.Observable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Twitch playback info, video details and (local) channel follow/unfollow.
 */
public class TwitchMediaItemService extends StubMediaItemService {
    private final ProviderStore mStore;

    public TwitchMediaItemService(ProviderStore store) {
        mStore = store;
    }

    // Stream info

    @Override
    public MediaItemFormatInfo getFormatInfo(MediaItem item) {
        return getFormatInfo(item != null ? item.getVideoId() : null);
    }

    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId) {
        return loadFormatInfo(videoId);
    }

    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId, String clickTrackingParams) {
        return getFormatInfo(videoId);
    }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(MediaItem item) {
        return getFormatInfoObserve(item != null ? item.getVideoId() : null);
    }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId) {
        return RxHelper.fromCallable(() -> loadFormatInfo(videoId));
    }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId, String clickTrackingParams) {
        return getFormatInfoObserve(videoId);
    }

    private MediaItemFormatInfo loadFormatInfo(String videoId) {
        try {
            return loadFormatInfoOrThrow(videoId);
        } catch (HttpStatusException e) {
            String reason = e.code == 401 || e.code == 403
                    ? "Twitch refused the request (HTTP " + e.code + "). It may be asking for a sign-in or an integrity check"
                    : "Twitch: " + e.getMessage();
            return ProviderFormatInfo.unplayable(videoId, reason);
        } catch (IOException e) {
            return ProviderFormatInfo.unplayable(videoId, e.getMessage() != null && e.getMessage().startsWith("Twitch") ? e.getMessage() : "Twitch: " + e.getMessage());
        }
    }

    private MediaItemFormatInfo loadFormatInfoOrThrow(String videoId) throws IOException {
        if (videoId == null) {
            return ProviderFormatInfo.unplayable(null, "Video not found on Twitch");
        }

        if (videoId.startsWith(TwitchApi.LIVE_PREFIX)) {
            String login = videoId.substring(TwitchApi.LIVE_PREFIX.length());
            TwitchApi.User user = TwitchApi.user(login);

            if (user == null) {
                return ProviderFormatInfo.unplayable(videoId, "Channel not found on Twitch");
            }

            if (user.live == null) {
                return ProviderFormatInfo.unplayable(videoId, user.displayName + " is offline right now");
            }

            String url = TwitchApi.liveUrl(login);
            mStore.addToHistory(user.live);

            return ProviderFormatInfo.playable(videoId, url)
                    .live(true)
                    .describe(user.live.title, user.displayName, user.login, user.description, 0);
        }

        if (videoId.startsWith(TwitchApi.VOD_PREFIX)) {
            String id = videoId.substring(TwitchApi.VOD_PREFIX.length());
            TwitchApi.Vod vod = TwitchApi.vod(id);

            if (vod == null) {
                return ProviderFormatInfo.unplayable(videoId, "This video is no longer available on Twitch");
            }

            String url = TwitchApi.vodUrl(id);
            mStore.addToHistory(vod.item);

            return ProviderFormatInfo.playable(videoId, url)
                    .describe(vod.title, vod.channelName, vod.channelLogin, vod.item.description, vod.lengthSec);
        }

        return ProviderFormatInfo.unplayable(videoId, "Unknown Twitch item");
    }

    // Video details

    @Override
    public MediaItemMetadata getMetadata(MediaItem item) {
        return getMetadata(item != null ? item.getVideoId() : null);
    }

    @Override
    public MediaItemMetadata getMetadata(String videoId) {
        try {
            return loadMetadata(videoId);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public MediaItemMetadata getMetadata(String videoId, String playlistId, int playlistIndex, String playlistParams) {
        return getMetadata(videoId);
    }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(MediaItem item) {
        return getMetadataObserve(item != null ? item.getVideoId() : null);
    }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(String videoId) {
        return RxHelper.fromCallable(() -> loadMetadata(videoId));
    }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(String videoId, String playlistId, int playlistIndex, String playlistParams) {
        return getMetadataObserve(videoId);
    }

    private MediaItemMetadata loadMetadata(String videoId) throws IOException {
        if (videoId == null) {
            return null;
        }

        ProviderMetadata metadata = new ProviderMetadata();
        metadata.videoId = videoId;
        String login;

        if (videoId.startsWith(TwitchApi.LIVE_PREFIX)) {
            login = videoId.substring(TwitchApi.LIVE_PREFIX.length());
            TwitchApi.User user = TwitchApi.user(login);

            if (user == null) {
                return null;
            }

            metadata.title = user.live != null ? user.live.title : user.displayName;
            metadata.secondTitle = user.live != null ? user.live.secondTitle : "Offline";
            metadata.description = user.description;
            metadata.author = user.displayName;
            metadata.authorImageUrl = user.avatarUrl;
            metadata.live = user.live != null;
        } else if (videoId.startsWith(TwitchApi.VOD_PREFIX)) {
            TwitchApi.Vod vod = TwitchApi.vod(videoId.substring(TwitchApi.VOD_PREFIX.length()));

            if (vod == null) {
                return null;
            }

            login = vod.channelLogin;
            metadata.title = vod.title;
            metadata.secondTitle = vod.item.secondTitle;
            metadata.description = vod.item.description;
            metadata.author = vod.channelName;
            metadata.authorImageUrl = vod.channelAvatar;
            metadata.publishedDate = vod.publishedMs > 0 ? ProviderMediaItem.timeAgo(vod.publishedMs) : null;
            metadata.durationMs = vod.lengthSec * 1000;
        } else {
            return null;
        }

        metadata.channelId = login;
        metadata.subscribed = mStore.isFollowed(login);

        try { // suggestions are optional: the video must play even if they fail
            addSuggestions(metadata, videoId, login);
        } catch (IOException e) {
            // NOP
        }

        return metadata;
    }

    private void addSuggestions(ProviderMetadata metadata, String currentId, String login) throws IOException {
        if (login != null) {
            ProviderMediaGroup more = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "More from this channel");

            for (ProviderMediaItem item : TwitchApi.videos(login, "ARCHIVE", null).items) {
                if (!currentId.equals(item.videoId)) {
                    more.add(item);
                }
            }

            if (!more.isEmpty()) {
                metadata.suggestions.add(more);
                metadata.nextVideo = more.getMediaItems().get(0);
            }
        }

        ProviderMediaGroup live = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "Live now");

        for (ProviderMediaItem item : TwitchApi.topStreams(null).items) {
            if (!currentId.equals(item.videoId)) {
                live.add(item);
            }
        }

        if (!live.isEmpty()) {
            metadata.suggestions.add(live);

            if (metadata.nextVideo == null) {
                metadata.nextVideo = live.getMediaItems().get(0);
            }
        }
    }

    // Local follow / unfollow

    @Override
    public void subscribe(MediaItem item) {
        follow(LocalContentBase.channelIdOf(item), item);
    }

    @Override
    public void subscribe(String channelId) {
        follow(channelId, null);
    }

    @Override
    public void unsubscribe(MediaItem item) {
        mStore.unfollow(LocalContentBase.channelIdOf(item));
    }

    @Override
    public void unsubscribe(String channelId) {
        mStore.unfollow(channelId);
    }

    @Override
    public Observable<Void> subscribeObserve(MediaItem item) {
        return RxHelper.fromRunnable(() -> follow(LocalContentBase.channelIdOf(item), item));
    }

    @Override
    public Observable<Void> subscribeObserve(String channelId) {
        return RxHelper.fromRunnable(() -> follow(channelId, null));
    }

    @Override
    public Observable<Void> unsubscribeObserve(MediaItem item) {
        return RxHelper.fromRunnable(() -> unsubscribe(item));
    }

    @Override
    public Observable<Void> unsubscribeObserve(String channelId) {
        return RxHelper.fromRunnable(() -> unsubscribe(channelId));
    }

    private void follow(String channelId, MediaItem hint) {
        if (channelId == null || channelId.startsWith(TwitchApi.GAME_PREFIX)) { // categories can't be followed
            return;
        }

        try {
            List<TwitchApi.User> users = TwitchApi.users(Collections.singletonList(channelId)); // proper name and avatar

            if (!users.isEmpty()) {
                mStore.follow(users.get(0).toChannelItem());
                return;
            }
        } catch (Exception e) {
            // Fall back to what the item already knows
        }

        String name = hint != null && hint.getAuthor() != null ? hint.getAuthor() : channelId;
        mStore.follow(ProviderMediaItem.channel(channelId, name, null));
    }
}
