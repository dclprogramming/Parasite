package com.liskovsoft.smartyoutubetv2.common.providers;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubChannelGroupService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubCommentsService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubLiveChatService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubNotificationsService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubRemoteControlService;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubSignInService;

/**
 * {@link ServiceManager} of a non-YouTube provider. Everything an account would be needed for
 * (login, comments, live chat, notifications, remote control) is a harmless no-op.
 */
public class ProviderServiceManager implements ServiceManager {
    private final ContentService mContentService;
    private final MediaItemService mMediaItemService;
    private final SignInService mSignInService = new StubSignInService();
    private final RemoteControlService mRemoteControlService = new StubRemoteControlService();
    private final LiveChatService mLiveChatService = new StubLiveChatService();
    private final CommentsService mCommentsService = new StubCommentsService();
    private final NotificationsService mNotificationsService = new StubNotificationsService();
    private final ChannelGroupService mChannelGroupService = new StubChannelGroupService();

    public ProviderServiceManager(ContentService contentService, MediaItemService mediaItemService) {
        mContentService = contentService;
        mMediaItemService = mediaItemService;
    }

    @Override
    public SignInService getSignInService() {
        return mSignInService;
    }

    @Override
    public RemoteControlService getRemoteControlService() {
        return mRemoteControlService;
    }

    @Override
    public ContentService getContentService() {
        return mContentService;
    }

    @Override
    public MediaItemService getMediaItemService() {
        return mMediaItemService;
    }

    @Override
    public LiveChatService getLiveChatService() {
        return mLiveChatService;
    }

    @Override
    public CommentsService getCommentsService() {
        return mCommentsService;
    }

    @Override
    public NotificationsService getNotificationsService() {
        return mNotificationsService;
    }

    @Override
    public ChannelGroupService getChannelGroupService() {
        return mChannelGroupService;
    }

    @Override
    public void invalidateCache() {
        // NOP
    }

    @Override
    public void refreshCacheIfNeeded() {
        // NOP
    }

    @Override
    public void switchNextClient() {
        // NOP
    }

    @Override
    public void switchNextClientNow() {
        // NOP
    }

    @Override
    public void switchNextSubsFormat() {
        // NOP
    }
}
