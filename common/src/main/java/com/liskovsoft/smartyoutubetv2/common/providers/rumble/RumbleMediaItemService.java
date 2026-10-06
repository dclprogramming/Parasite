package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.LocalContentBase;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMetadata;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubMediaItemService;

import io.reactivex.Observable;

import java.io.IOException;
import java.util.List;

/**
 * Rumble playback info, video details and (local) channel follow/unfollow.
 */
public class RumbleMediaItemService extends StubMediaItemService {
    private final ProviderStore mStore;

    public RumbleMediaItemService(ProviderStore store) {
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
        } catch (IOException e) {
            return ProviderFormatInfo.unplayable(videoId, "Rumble: " + e.getMessage());
        }
    }

    private MediaItemFormatInfo loadFormatInfoOrThrow(String videoId) throws IOException {
        if (videoId == null) {
            return ProviderFormatInfo.unplayable(null, "Video not found on Rumble");
        }

        RumbleParser.Stream stream = RumbleApi.stream(videoId);

        String url = stream.mp4Url != null ? stream.mp4Url : (stream.hlsUrl != null ? stream.hlsUrl : stream.webmUrl); // the player reads the type from the file extension

        if (url == null) {
            return ProviderFormatInfo.unplayable(videoId, "This video can't be played here");
        }

        mStore.addToHistory(stream.toMediaItem(videoId));

        return ProviderFormatInfo.playable(videoId, url)
                .describe(stream.title, stream.authorName, stream.channelId, "Rumble stream: " + stream.describeChoice(), stream.durationSec);
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

        RumbleParser.Stream stream;

        try {
            stream = RumbleApi.stream(videoId);
        } catch (IOException e) { // details are optional: the video itself loads separately
            ProviderMetadata minimal = new ProviderMetadata();
            minimal.title = "Rumble video";
            minimal.videoId = videoId;
            return minimal;
        }

        ProviderMediaItem item = stream.toMediaItem(videoId);
        ProviderMetadata metadata = new ProviderMetadata();
        metadata.title = stream.title;
        metadata.secondTitle = item.secondTitle;
        metadata.description = "Rumble stream: " + stream.describeChoice();
        metadata.author = stream.authorName;
        metadata.publishedDate = stream.publishedMs > 0 ? ProviderMediaItem.timeAgo(stream.publishedMs) : null;
        metadata.videoId = videoId;
        metadata.channelId = stream.channelId;
        metadata.subscribed = mStore.isFollowed(stream.channelId);
        metadata.durationMs = stream.durationSec * 1000;

        try { // suggestions are optional: the video must play even if they fail
            addSuggestions(metadata, stream, videoId);
        } catch (IOException e) {
            // NOP
        }

        return metadata;
    }

    private void addSuggestions(ProviderMetadata metadata, RumbleParser.Stream current, String videoId) throws IOException {
        if (current.channelId != null) {
            ProviderMediaGroup more = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "More from " + current.authorName);

            for (RumbleParser.Entry entry : RumbleApi.listing(RumbleApi.channelPath(current.channelId), 1)) {
                if (!videoId.equals(entry.stem)) {
                    more.add(entry.toMediaItem());
                }
            }

            if (!more.isEmpty()) {
                metadata.suggestions.add(more);
                metadata.nextVideo = more.getMediaItems().get(0);
            }
        }

        ProviderMediaGroup top = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "Top today");

        for (RumbleParser.Entry entry : RumbleApi.listing(RumbleApi.browsePath("views", "today"), 1)) {
            if (!videoId.equals(entry.stem)) {
                top.add(entry.toMediaItem());
            }
        }

        if (!top.isEmpty()) {
            metadata.suggestions.add(top);

            if (metadata.nextVideo == null) {
                metadata.nextVideo = top.getMediaItems().get(0);
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
        if (channelId == null) {
            return;
        }

        try {
            mStore.follow(RumbleApi.channelInfo(channelId)); // proper name and icon
            return;
        } catch (Exception e) {
            // Fall back to what the item already knows
        }

        String name = hint != null && hint.getAuthor() != null ? hint.getAuthor() : channelId.substring(channelId.indexOf('/') + 1);
        mStore.follow(ProviderMediaItem.channel(channelId, name, null));
    }
}
