package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;
import com.liskovsoft.smartyoutubetv2.common.providers.odysee.OdyseeContentService;
import com.liskovsoft.smartyoutubetv2.common.providers.odysee.OdyseeMediaItemService;
import com.liskovsoft.smartyoutubetv2.common.providers.rumble.RumbleContentService;
import com.liskovsoft.smartyoutubetv2.common.providers.rumble.RumbleMediaItemService;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Single entry point to the service layer of the provider chosen in the Providers page.<br/>
 * The provider is fixed for the lifetime of the process: switching it restarts the app.
 */
public final class ProviderServices {
    private static ServiceManager sManager;

    private ProviderServices() {
    }

    public static synchronized ServiceManager manager() {
        if (sManager == null) {
            if (ProviderData.getAppContext() == null) {
                return YouTubeServiceManager.instance(); // too early: don't lock in the default for the whole process
            }

            sManager = create(ProviderData.getSelected());
        }

        return sManager;
    }

    private static ServiceManager create(int provider) {
        Context context = ProviderData.getAppContext();

        if (provider == ProviderData.ODYSEE && context != null) {
            ProviderStore store = new ProviderStore(context, "odysee");
            return new ProviderServiceManager(new OdyseeContentService(store), new OdyseeMediaItemService(store));
        }

        if (provider == ProviderData.RUMBLE && context != null) {
            ProviderStore store = new ProviderStore(context, "rumble");
            return new ProviderServiceManager(new RumbleContentService(store), new RumbleMediaItemService(store));
        }

        return YouTubeServiceManager.instance();
    }
}
