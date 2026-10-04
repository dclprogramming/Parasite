package com.liskovsoft.smartyoutubetv2.common.providers.model;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import io.reactivex.Observable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stream description for non-YouTube providers: a plain list of progressive (mp4) urls.
 */
public class ProviderFormatInfo implements MediaItemFormatInfo {
    private final List<String> mUrls = new ArrayList<>();
    private String mVideoId;
    private String mChannelId;
    private String mTitle;
    private String mAuthor;
    private String mDescription;
    private long mLengthSec;
    private String mUnplayableReason;
    private String mClickTrackingParams;

    public static ProviderFormatInfo playable(String videoId, String url) {
        ProviderFormatInfo info = new ProviderFormatInfo();
        info.mVideoId = videoId;
        info.mUrls.add(url);
        return info;
    }

    public static ProviderFormatInfo unplayable(String videoId, String reason) {
        ProviderFormatInfo info = new ProviderFormatInfo();
        info.mVideoId = videoId;
        info.mUnplayableReason = reason;
        return info;
    }

    public ProviderFormatInfo describe(String title, String author, String channelId, String description, long lengthSec) {
        mTitle = title;
        mAuthor = author;
        mChannelId = channelId;
        mDescription = description;
        mLengthSec = lengthSec;
        return this;
    }

    // Playback source

    @Override
    public boolean containsUrlFormats() {
        return !mUrls.isEmpty();
    }

    @Override
    public List<String> createUrlList() {
        return new ArrayList<>(mUrls);
    }

    @Override
    public boolean containsMedia() {
        return containsUrlFormats();
    }

    @Override
    public boolean isUnplayable() {
        return mUnplayableReason != null;
    }

    @Override
    public String getPlayabilityReason() {
        return mUnplayableReason;
    }

    @Override
    public boolean isStreamSeekable() {
        return true;
    }

    // Unused source types

    @Override
    public List<MediaFormat> getAdaptiveFormats() {
        return Collections.emptyList();
    }

    @Override
    public List<MediaFormat> getUrlFormats() {
        return Collections.emptyList();
    }

    @Override
    public List<MediaSubtitle> getSubtitles() {
        return Collections.emptyList();
    }

    @Override
    public String getHlsManifestUrl() {
        return null;
    }

    @Override
    public String getDashManifestUrl() {
        return null;
    }

    @Override
    public boolean containsSabrFormats() {
        return false;
    }

    @Override
    public boolean containsDashFormats() {
        return false;
    }

    @Override
    public boolean containsHlsUrl() {
        return false;
    }

    @Override
    public boolean containsDashUrl() {
        return false;
    }

    @Override
    public boolean hasExtendedHlsFormats() {
        return false;
    }

    @Override
    public InputStream createMpdStream() {
        return null;
    }

    @Override
    public Observable<InputStream> createMpdStreamObservable() {
        return Observable.empty();
    }

    @Override
    public MediaItemStoryboard createStoryboard() {
        return null;
    }

    // Metadata

    @Override
    public String getLengthSeconds() {
        return String.valueOf(mLengthSec);
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getAuthor() {
        return mAuthor;
    }

    @Override
    public String getViewCount() {
        return null;
    }

    @Override
    public String getDescription() {
        return mDescription;
    }

    @Override
    public String getVideoId() {
        return mVideoId;
    }

    @Override
    public String getChannelId() {
        return mChannelId;
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isLiveContent() {
        return false;
    }

    @Override
    public float getVolumeLevel() {
        return 100;
    }

    @Override
    public boolean isUnknownError() {
        return false;
    }

    @Override
    public String getStartTimestamp() {
        return null;
    }

    @Override
    public String getUploadDate() {
        return null;
    }

    @Override
    public long getStartTimeMs() {
        return 0;
    }

    @Override
    public int getStartSegmentNum() {
        return 0;
    }

    @Override
    public int getSegmentDurationUs() {
        return 0;
    }

    @Override
    public String getPaidContentText() {
        return null;
    }

    @Override
    public String getVideoPlaybackUstreamerConfig() {
        return null;
    }

    @Override
    public String getServerAbrStreamingUrl() {
        return null;
    }

    @Override
    public String getPoToken() {
        return null;
    }

    @Override
    public String getVisitorCookie() {
        return null;
    }

    @Override
    public ClientInfo getClientInfo() {
        return null;
    }

    // FormatInfoProvision

    @Override
    public boolean isSynced() {
        return true;
    }

    @Override
    public boolean isAuth() {
        return false;
    }

    @Override
    public String getEventId() {
        return null;
    }

    @Override
    public String getVisitorMonitoringData() {
        return null;
    }

    @Override
    public String getOfParam() {
        return null;
    }

    @Override
    public String getClickTrackingParams() {
        return mClickTrackingParams;
    }

    @Override
    public void setClickTrackingParams(String clickTrackingParams) {
        mClickTrackingParams = clickTrackingParams;
    }

    @Override
    public boolean isCacheActual() {
        return true;
    }

    @Override
    public void sync(MediaItemFormatInfo formatInfo) {
        // NOP
    }
}
