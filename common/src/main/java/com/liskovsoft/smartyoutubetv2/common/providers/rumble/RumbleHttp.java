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
    private static volatile long sDirectBlockedUntil;

    private RumbleHttp() {
    }

    static String get(String path, String referer, boolean json) throws IOException {
        String url = ORIGIN + path;
        boolean directFirst = System.currentTimeMillis() >= sDirectBlockedUntil;
        IOException directError = null;

        if (directFirst) {
            try {
                String body = ProviderHttp.get(url, headers(referer, json));

                if (!isBlockPage(body, json)) {
                    return body;
                }

                directError = new IOException("bot-check page instead of data");
                blockDirect();
            } catch (HttpStatusException e) {
                directError = e;

                if (e.code == 403 || e.code == 429 || e.code == 503) {
                    blockDirect();
                }
            } catch (IOException e) {
                directError = e;
            }
        }

        try {
            return BrowserFetcher.fetch(ORIGIN, url);
        } catch (IOException browserError) {
            if (!directFirst) { // the browser failed while plain requests were being skipped: give them another go
                try {
                    String body = ProviderHttp.get(url, headers(referer, json));

                    if (!isBlockPage(body, json)) {
                        return body;
                    }
                } catch (IOException ignored) {
                    // report the browser error below
                }
            }

            throw directError != null ? new IOException(directError.getMessage() + "; browser: " + browserError.getMessage()) : browserError;
        }
    }

    /** Skip plain requests for a while after Rumble refused one. Retried later in case the block was temporary. */
    private static void blockDirect() {
        sDirectBlockedUntil = System.currentTimeMillis() + 10 * 60 * 1000;
    }

    /**
     * Is this answer a bot-check / error page instead of the requested data?
     */
    static boolean isBlockPage(String body, boolean json) {
        String text = body == null ? "" : body.trim();

        if (json) {
            return text.startsWith("<"); // data answers are JSON (even a bare "false"); HTML is a check or error page
        }

        // Note: "/cdn-cgi/challenge-platform/" also appears on normal pages, so it is not a sign of a block
        String head = (text.length() > 4000 ? text.substring(0, 4000) : text).toLowerCase(java.util.Locale.US);
        return head.contains("just a moment") || head.contains("cf-chl-") || head.contains("enable javascript and cookies")
                || head.contains("<title>attention required");
    }

    /**
     * Always through the hidden browser, which holds the login cookies.
     */
    static String getWithSession(String path) throws IOException {
        return BrowserFetcher.fetch(ORIGIN, ORIGIN + path);
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
