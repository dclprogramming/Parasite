package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;

/**
 * Sign-in state of the non-YouTube providers. The login itself happens on the site's own page
 * (see {@link ProviderLoginDialog}); the session lives in the WebView cookies.
 */
public final class ProviderAuth {
    public static final String ODYSEE_SITE = "https://odysee.com";
    public static final String RUMBLE_SITE = "https://rumble.com";

    private ProviderAuth() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("parasite_auth", Context.MODE_PRIVATE);
    }

    public static boolean isSignedIn(Context context, int provider) {
        return context != null && prefs(context).getBoolean("signed_" + provider, false);
    }

    public static void setSignedIn(Context context, int provider, boolean signedIn) {
        prefs(context).edit().putBoolean("signed_" + provider, signedIn).commit();
    }

    public static String getOdyseeToken(Context context) {
        return context != null ? prefs(context).getString("odysee_token", null) : null;
    }

    public static void setOdyseeToken(Context context, String token) {
        prefs(context).edit().putString("odysee_token", token).commit();
    }

    public static String siteOf(int provider) {
        return provider == ProviderData.RUMBLE ? RUMBLE_SITE : ODYSEE_SITE;
    }

    /**
     * Value of one cookie of the site, from the WebView cookie jar. Null if missing.
     */
    public static String cookie(String site, String name) {
        String cookies = CookieManager.getInstance().getCookie(site);

        if (cookies == null) {
            return null;
        }

        for (String pair : cookies.split(";")) {
            String[] parts = pair.trim().split("=", 2);

            if (parts.length == 2 && parts[0].equals(name) && !parts[1].isEmpty()) {
                return parts[1];
            }
        }

        return null;
    }

    /**
     * Forgets the account: flags, token and the site's cookies (best effort).
     */
    public static void signOut(Context context, int provider) {
        setSignedIn(context, provider, false);

        if (provider == ProviderData.ODYSEE) {
            setOdyseeToken(context, null);
        }

        String site = siteOf(provider);
        CookieManager cookies = CookieManager.getInstance();
        String all = cookies.getCookie(site);

        if (all != null) {
            for (String pair : all.split(";")) {
                String name = pair.trim().split("=", 2)[0];

                if (!name.isEmpty()) {
                    cookies.setCookie(site, name + "=; Max-Age=0; Path=/");
                }
            }
        }

        cookies.flush();
    }
}
