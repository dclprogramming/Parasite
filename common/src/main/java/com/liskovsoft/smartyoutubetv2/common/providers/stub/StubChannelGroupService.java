// GENERATED no-op defaults for providers that do not implement every ChannelGroupService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import android.net.Uri;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item;
import java.io.File;
import java.util.List;
import io.reactivex.Observable;

import java.util.Collections;

public class StubChannelGroupService implements ChannelGroupService {
    @Override
    public List<ItemGroup> getChannelGroups() { return Collections.emptyList(); }

    @Override
    public void addChannelGroup(ItemGroup group) {  }

    @Override
    public void removeChannelGroup(ItemGroup group) {  }

    @Override
    public ItemGroup createChannelGroup(String title, String iconUrl, List<Item> channels) { return null; }

    @Override
    public void renameChannelGroup(ItemGroup channelGroup, String title) {  }

    @Override
    public Item createChannel(String channelId, String title, String iconUrl) { return null; }

    @Override
    public ItemGroup findChannelGroupById(String channelGroupId) { return null; }

    @Override
    public ItemGroup findChannelGroupByTitle(String title) { return null; }

    @Override
    public String[] findChannelIdsForGroup(String channelGroupId) { return new String[0]; }

    @Override
    public Observable<List<ItemGroup>> importGroupsObserve(Uri uri) { return Observable.empty(); }

    @Override
    public Observable<List<ItemGroup>> importGroupsObserve(File file) { return Observable.empty(); }

    @Override
    public void exportData(String data) {  }

    @Override
    public boolean isEmpty() { return false; }

}
