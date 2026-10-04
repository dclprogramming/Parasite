// GENERATED no-op defaults for providers that do not implement every CommentsService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import io.reactivex.Observable;

import java.util.Collections;

public class StubCommentsService implements CommentsService {
    @Override
    public Observable<CommentGroup> getCommentsObserve(String key) { return Observable.empty(); }

    @Override
    public Observable<Void> toggleLikeObserve(String key) { return Observable.empty(); }

    @Override
    public Observable<Void> toggleDislikeObserve(String key) { return Observable.empty(); }

}
