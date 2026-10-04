// GENERATED no-op defaults for providers that do not implement every NotificationsService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import io.reactivex.Observable;

import java.util.Collections;

public class StubNotificationsService implements NotificationsService {
    @Override
    public MediaGroup getNotificationItems() { return null; }

    @Override
    public void hideNotification(MediaItem item) {  }

    @Override
    public void setNotificationState(NotificationState state) {  }

    @Override
    public Observable<MediaGroup> getNotificationItemsObserve() { return Observable.empty(); }

    @Override
    public Observable<Void> hideNotificationObserve(MediaItem item) { return Observable.empty(); }

    @Override
    public Observable<Void> setNotificationStateObserve(NotificationState state) { return Observable.empty(); }

}
