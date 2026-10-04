// GENERATED no-op defaults for providers that do not implement every ContentService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import io.reactivex.Observable;
import java.util.List;

import java.util.Collections;

public class StubContentService implements ContentService {
    @Override
    public List<MediaGroup> getSearch(String searchText) { return Collections.emptyList(); }

    @Override
    public List<MediaGroup> getSearch(String searchText, int options) { return Collections.emptyList(); }

    @Override
    public List<String> getSearchTags(String searchText) { return Collections.emptyList(); }

    @Override
    public MediaGroup getSubscriptions() { return null; }

    @Override
    public MediaGroup getRssFeed(String... channelIds) { return null; }

    @Override
    public MediaGroup getRecommended() { return null; }

    @Override
    public MediaGroup getHistory() { return null; }

    @Override
    public List<MediaGroup> getHome() { return Collections.emptyList(); }

    @Override
    public MediaGroup getSubscribedChannels() { return null; }

    @Override
    public MediaGroup getSubscribedChannelsByNewContent() { return null; }

    @Override
    public MediaGroup getSubscribedChannelsByName() { return null; }

    @Override
    public MediaGroup getSubscribedChannelsByLastViewed() { return null; }

    @Override
    public MediaGroup getGroup(MediaItem mediaItem) { return null; }

    @Override
    public MediaGroup getChannelSearch(String channelId, String query) { return null; }

    @Override
    public MediaGroup getGroup(String reloadPageKey) { return null; }

    @Override
    public MediaGroup continueGroup(MediaGroup mediaGroup) { return null; }

    @Override
    public void enableHistory(boolean enable) {  }

    @Override
    public void clearHistory() {  }

    @Override
    public void clearSearchHistory() {  }

    @Override
    public void removeSearchTag(String tag) {  }

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText) { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText, int options) { return Observable.empty(); }

    @Override
    public Observable<List<String>> getSearchTagsObserve(String searchText) { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getRssFeedObserve(String... channelIds) { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getRecommendedObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getHistoryObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getHomeObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getTrendingObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getShortsObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getKidsHomeObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getSportsObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getLiveObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getMyVideosObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getMusicObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getNewsObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getGamingObserve() { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(String channelId) { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getChannelSortingOptionsObserve(String channelId) { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getChannelSortingOptionsObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getChannelSearchObserve(String channelId, String query) { return Observable.empty(); }

    @Override
    public Observable<List<MediaGroup>> getPlaylistRowsObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getPlaylistsObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNewContentObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNameObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByLastViewedObserve() { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getGroupObserve(MediaItem mediaItem) { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> getGroupObserve(String reloadPageKey) { return Observable.empty(); }

    @Override
    public Observable<MediaGroup> continueGroupObserve(MediaGroup mediaGroup) { return Observable.empty(); }

}
