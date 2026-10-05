package com.liskovsoft.smartyoutubetv2.common.providers.odysee;

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
 * Odysee playback info, video details and (local) channel follow/unfollow.
 */
public class OdyseeMediaItemService extends StubMediaItemService {
    private final ProviderStore mStore;

    public OdyseeMediaItemService(ProviderStore store) {
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
            return ProviderFormatInfo.unplayable(videoId, "Odysee: " + e.getMessage());
        }
    }

    private MediaItemFormatInfo loadFormatInfoOrThrow(String videoId) throws IOException {
        OdyseeClaim claim = videoId != null ? OdyseeApi.claimById(videoId) : null;

        if (claim == null) {
            return ProviderFormatInfo.unplayable(videoId, "Video not found on Odysee");
        }

        if (claim.isPaid) {
            return ProviderFormatInfo.unplayable(videoId, "Paid videos are not supported");
        }

        if (claim.isRestricted) {
            return ProviderFormatInfo.unplayable(videoId, "Members-only or purchased videos are not supported yet");
        }

        if (!claim.isPlayable()) {
            return ProviderFormatInfo.unplayable(videoId, "This video can't be played here");
        }

        OdyseeApi.StreamLookup lookup = OdyseeApi.resolveStreamUrl(claim);

        if (lookup.url == null) {
            String reason = lookup.failureCode == 401 || lookup.failureCode == 403
                    ? "Odysee wants an account or membership for this video (HTTP " + lookup.failureCode + ")"
                    : lookup.failureCode == 404 ? "Odysee has no stream for this video (HTTP 404)"
                    : "Could not reach Odysee's video servers";
            return ProviderFormatInfo.unplayable(videoId, reason);
        }

        String url = lookup.url;

        mStore.addToHistory(claim.toMediaItem());

        return ProviderFormatInfo.playable(videoId, url)
                .describe(claim.getTitle(), claim.getAuthor(), claim.channelId, claim.description, claim.durationSec);
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
        OdyseeClaim claim = videoId != null ? OdyseeApi.claimById(videoId) : null;

        if (claim == null) {
            return null;
        }

        ProviderMediaItem item = claim.toMediaItem();
        ProviderMetadata metadata = new ProviderMetadata();
        metadata.title = claim.getTitle();
        metadata.secondTitle = item.secondTitle;
        metadata.description = claim.description;
        metadata.author = claim.getAuthor();
        metadata.authorImageUrl = claim.channelThumbnailUrl;
        metadata.publishedDate = claim.releaseTimeSec > 0 ? ProviderMediaItem.timeAgo(claim.releaseTimeSec * 1000) : null;
        metadata.videoId = videoId;
        metadata.channelId = claim.channelId;
        metadata.subscribed = mStore.isFollowed(claim.channelId);
        metadata.durationMs = claim.durationSec * 1000;

        try { // suggestions are optional: the video must play even if they fail
            addSuggestions(metadata, claim);
        } catch (IOException e) {
            // NOP
        }

        return metadata;
    }

    private void addSuggestions(ProviderMetadata metadata, OdyseeClaim current) throws IOException {
        if (current.channelId != null) {
            ProviderMediaGroup more = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "More from " + current.getAuthor());

            for (OdyseeClaim claim : OdyseeApi.claimSearch(OdyseeApi.videos().channels(current.channelId).orderBy("release_time"), 1)) {
                if (claim.isPlayable() && !claim.claimId.equals(current.claimId)) {
                    more.add(claim.toMediaItem());
                }
            }

            if (!more.isEmpty()) {
                metadata.suggestions.add(more);
                metadata.nextVideo = more.getMediaItems().get(0);
            }
        }

        ProviderMediaGroup trending = new ProviderMediaGroup(MediaGroup.TYPE_SUGGESTIONS, "Trending");

        for (OdyseeClaim claim : OdyseeApi.claimSearch(OdyseeApi.videos().orderBy("trending_group", "trending_mixed").perChannel(1), 1)) {
            if (claim.isPlayable() && !claim.claimId.equals(current.claimId)) {
                trending.add(claim.toMediaItem());
            }
        }

        if (!trending.isEmpty()) {
            metadata.suggestions.add(trending);

            if (metadata.nextVideo == null) {
                metadata.nextVideo = trending.getMediaItems().get(0);
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
            List<String> ids = java.util.Collections.singletonList(channelId);
            List<OdyseeClaim> channels = OdyseeApi.claimsByIds(ids, true); // proper name and icon

            if (!channels.isEmpty()) {
                mStore.follow(channels.get(0).toMediaItem());
                return;
            }
        } catch (Exception e) {
            // Fall back to what the item already knows
        }

        String name = hint != null && hint.getAuthor() != null ? hint.getAuthor() : channelId;
        mStore.follow(ProviderMediaItem.channel(channelId, name, null));
    }
}
