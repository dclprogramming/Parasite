package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import com.liskovsoft.smartyoutubetv2.common.providers.ProviderStore;

import java.io.IOException;
import java.util.List;

/**
 * Copies the channels followed on the Twitch account into the local follow list.
 */
public final class TwitchAccount {
    private TwitchAccount() {
    }

    /**
     * @return number of followed channels of the account
     */
    public static int importFollows(ProviderStore store) throws IOException {
        List<String> logins = TwitchApi.followedLogins();

        if (logins.isEmpty()) {
            return 0;
        }

        int count = 0;

        for (TwitchApi.User user : TwitchApi.users(logins)) {
            store.follow(user.toChannelItem());
            count++;
        }

        return count;
    }
}
