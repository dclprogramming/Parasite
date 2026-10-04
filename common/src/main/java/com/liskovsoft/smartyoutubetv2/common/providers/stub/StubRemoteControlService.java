// GENERATED no-op defaults for providers that do not implement every RemoteControlService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.mediaserviceinterfaces.data.Command;
import io.reactivex.Observable;

import java.util.Collections;

public class StubRemoteControlService implements RemoteControlService {
    @Override
    public String getPairingCode() { return null; }

    @Override
    public Observable<String> getPairingCodeObserve() { return Observable.empty(); }

    @Override
    public Observable<Command> getCommandObserve() { return Observable.empty(); }

    @Override
    public Observable<Void> postStartPlayingObserve(String videoId, long positionMs, long durationMs, int state) { return Observable.empty(); }

    @Override
    public Observable<Void> postStateChangeObserve(long positionMs, long durationMs, int state) { return Observable.empty(); }

    @Override
    public Observable<Void> postVolumeChangeObserve(int volume) { return Observable.empty(); }

    @Override
    public Observable<Void> postSubtitleChangeObserve(String vssId, String languageCodee) { return Observable.empty(); }

    @Override
    public Observable<Void> resetDataObserve() { return Observable.empty(); }

}
