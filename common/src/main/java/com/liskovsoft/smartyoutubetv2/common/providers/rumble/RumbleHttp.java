package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.HttpStatusException;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderHttp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Rumble sits behind a bot filter that answers plain HTTP clients with 403.<br/>
 * Try a browser-like request first; when blocked, use a hidden WebView from then on.
 */
final class RumbleHttp {
    static final String ORIGIN = "https://rumble.com";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static volatile boolean sBrowserOnly;

    private RumbleHttp() {
    }

    static String get(String path, String referer, boolean json) throws IOException {
        String url = ORIGIN + path;

        if (!sBrowserOnly) {
            try {
                return ProviderHttp.get(url, headers(referer, json));
            } catch (HttpStatusException e) {
                if (e.code != 403 && e.code != 429 && e.code != 503) {
                    throw e;
                }

                sBrowserOnly = true;
            }
        }

        return BrowserFetcher.fetch(ORIGIN, url);
    }

    private static Map<String, String> headers(String referer, boolean json) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DESKTOP_UA);
        headers.put("Accept", json ? "application/json, text/javascript, */*; q=0.01" : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Referer", referer != null ? referer : ORIGIN + "/");
        headers.put("Sec-Fetch-Dest", json ? "empty" : "document");
        headers.put("Sec-Fetch-Mode", json ? "cors" : "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");

        if (json) {
            headers.put("X-Requested-With", "XMLHttpRequest");
        }

        return headers;
    }
}
