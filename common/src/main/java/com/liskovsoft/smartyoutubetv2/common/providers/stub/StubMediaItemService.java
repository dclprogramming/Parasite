// GENERATED no-op defaults for providers that do not implement every MediaItemService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.DeArrowData;
import com.liskovsoft.mediaserviceinterfaces.data.DislikeData;
import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint;
import com.liskovsoft.mediaserviceinterfaces.data.FeedbackReasons;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import io.reactivex.Observable;
import java.util.List;
import java.util.Set;

import java.util.Collections;

public class StubMediaItemService implements MediaItemService {
    @Override
    public MediaItemFormatInfo getFormatInfo(MediaItem item) { return null; }

    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId) { return null; }

    @Override
    public MediaItemFormatInfo getFormatInfo(String videoId, String clickTrackingParams) { return null; }

    @Override
    public MediaItemStoryboard getStoryboard(MediaItem item) { return null; }

    @Override
    public MediaItemStoryboard getStoryboard(String videoId) { return null; }

    @Override
    public MediaItemMetadata getMetadata(MediaItem item) { return null; }

    @Override
    public MediaItemMetadata getMetadata(String videoId) { return null; }

    @Override
    public MediaItemMetadata getMetadata(String videoId, String playlistId, int playlistIndex, String playlistParams) { return null; }

    @Override
    public void updateHistoryPosition(MediaItem item, float positionSec) {  }

    @Override
    public void updateHistoryPosition(String videoId, float positionSec) {  }

    @Override
    public void setLike(MediaItem item) {  }

    @Override
    public void removeLike(MediaItem item) {  }

    @Override
    public void setDislike(MediaItem item) {  }

    @Override
    public void removeDislike(MediaItem item) {  }

    @Override
    public void subscribe(MediaItem item) {  }

    @Override
    public void subscribe(String channelId) {  }

    @Override
    public void unsubscribe(MediaItem item) {  }

    @Override
    public void unsubscribe(String channelId) {  }

    @Override
    public void markAsNotInterested(String feedbackToken) {  }

    @Override
    public FeedbackReasons getFeedbackReasons(String feedbackToken) { return null; }

    @Override
    public List<String> getFeedbackTokens(FeedbackEndpoint endpoint) { return Collections.emptyList(); }

    @Override
    public List<PlaylistInfo> getPlaylistsInfo(String videoId) { return Collections.emptyList(); }

    @Override
    public void removeFromPlaylist(String playlistId, String videoId) {  }

    @Override
    public void renamePlaylist(String playlistId, String newName) {  }

    @Override
    public void setPlaylistOrder(String playlistId, int playlistOrder) {  }

    @Override
    public void removePlaylist(String playlistId) {  }

    @Override
    public List<SponsorSegment> getSponsorSegments(String videoId) { return Collections.emptyList(); }

    @Override
    public List<SponsorSegment> getSponsorSegments(String videoId, Set<String> categories) { return Collections.emptyList(); }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId, String clickTrackingParams) { return Observable.empty(); }

    @Override
    public Observable<MediaItemStoryboard> getStoryboardObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<MediaItemStoryboard> getStoryboardObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<MediaItemMetadata> getMetadataObserve(String videoId, String playlistId, int playlistIndex, String playlistParams) { return Observable.empty(); }

    @Override
    public Observable<Void> updateHistoryPositionObserve(MediaItem item, float positionSec) { return Observable.empty(); }

    @Override
    public Observable<Void> updateHistoryPositionObserve(String videoId, float positionSec) { return Observable.empty(); }

    @Override
    public Observable<Void> subscribeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> subscribeObserve(String channelId) { return Observable.empty(); }

    @Override
    public Observable<Void> unsubscribeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> unsubscribeObserve(String channelId) { return Observable.empty(); }

    @Override
    public Observable<Void> markAsNotInterestedObserve(String feedbackToken) { return Observable.empty(); }

    @Override
    public Observable<FeedbackReasons> getFeedbackReasonsObserve(String feedbackToken) { return Observable.empty(); }

    @Override
    public Observable<List<String>> getFeedbackTokensObserve(FeedbackEndpoint endpoint) { return Observable.empty(); }

    @Override
    public Observable<Void> setLikeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> removeLikeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> setDislikeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> removeDislikeObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<List<PlaylistInfo>> getPlaylistsInfoObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<Void> addToPlaylistObserve(String playlistId, String videoId) { return Observable.empty(); }

    @Override
    public Observable<Void> addToPlaylistObserve(String playlistId, MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> removeFromPlaylistObserve(String playlistId, String videoId) { return Observable.empty(); }

    @Override
    public Observable<Void> renamePlaylistObserve(String playlistId, String newName) { return Observable.empty(); }

    @Override
    public Observable<Void> setPlaylistOrderObserve(String playlistId, int playlistOrder) { return Observable.empty(); }

    @Override
    public Observable<Void> savePlaylistObserve(String playlistId) { return Observable.empty(); }

    @Override
    public Observable<Void> savePlaylistObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> removePlaylistObserve(String playlistId) { return Observable.empty(); }

    @Override
    public Observable<Void> createPlaylistObserve(String playlistName, String videoId) { return Observable.empty(); }

    @Override
    public Observable<Void> createPlaylistObserve(String playlistName, MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<List<SponsorSegment>> getSponsorSegmentsObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<List<SponsorSegment>> getSponsorSegmentsObserve(String videoId, Set<String> categories) { return Observable.empty(); }

    @Override
    public Observable<DeArrowData> getDeArrowDataObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<DeArrowData> getDeArrowDataObserve(List<String> videoIds) { return Observable.empty(); }

    @Override
    public Observable<DislikeData> getDislikeDataObserve(String videoId) { return Observable.empty(); }

    @Override
    public Observable<String> getUnlocalizedTitleObserve(String videoId) { return Observable.empty(); }

}
