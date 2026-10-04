package com.liskovsoft.smartyoutubetv2.common.providers.model;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain {@link MediaGroup} used by non-YouTube providers.<br/>
 * Paging is done by a {@link Next} callback that remembers the query of the next page.
 */
public class ProviderMediaGroup implements MediaGroup {
    public interface Next {
        ProviderMediaGroup load() throws Exception;
    }

    private final int mType;
    private final String mTitle;
    private final List<MediaItem> mItems = new ArrayList<>();
    private String mChannelId;
    private Next mNext;

    public ProviderMediaGroup(int type, String title) {
        mType = type;
        mTitle = title;
    }

    public ProviderMediaGroup add(MediaItem item) {
        mItems.add(item);
        return this;
    }

    public ProviderMediaGroup setChannelId(String channelId) {
        mChannelId = channelId;
        return this;
    }

    public ProviderMediaGroup setNext(Next next) {
        mNext = next;
        return this;
    }

    public Next getNext() {
        return mNext;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public List<MediaItem> getMediaItems() {
        return mItems;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getChannelId() {
        return mChannelId;
    }

    @Override
    public String getParams() {
        return null;
    }

    @Override
    public String getReloadPageKey() {
        return null;
    }

    @Override
    public String getNextPageKey() {
        return mNext != null ? "provider:next" : null;
    }

    @Override
    public String getChannelUrl() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return mItems.isEmpty();
    }
}
