// GENERATED no-op defaults for providers that do not implement every LiveChatService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import io.reactivex.Observable;

import java.util.Collections;

public class StubLiveChatService implements LiveChatService {
    @Override
    public Observable<ChatItem> openLiveChatObserve(String chatKey) { return Observable.empty(); }

}
