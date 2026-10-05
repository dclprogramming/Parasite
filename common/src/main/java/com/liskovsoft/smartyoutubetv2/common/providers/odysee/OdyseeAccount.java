package com.liskovsoft.smartyoutubetv2.common.providers.odysee;

import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;

import java.io.IOException;
import java.util.List;

/**
 * Copies the channels followed on the Odysee account into the local follow list.
 */
public final class OdyseeAccount {
    private OdyseeAccount() {
    }

    /**
     * @return number of followed channels of the account
     */
    public static int importFollows(ProviderStore store, String authToken) throws IOException {
        List<String> uris = OdyseeApi.followedUris(authToken);
        List<OdyseeClaim> channels = OdyseeApi.resolveChannels(uris);

        for (OdyseeClaim channel : channels) {
            store.follow(channel.toMediaItem());
        }

        return channels.size();
    }
}
