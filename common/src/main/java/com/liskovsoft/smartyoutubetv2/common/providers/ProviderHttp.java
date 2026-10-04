package com.liskovsoft.smartyoutubetv2.common.providers;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny blocking HTTP helper for non-YouTube providers. Uses the app's OkHttp client (proxy, DNS settings etc).
 */
public final class ProviderHttp {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private ProviderHttp() {
    }

    public static String get(String url) throws IOException {
        return execute(OkHttpManager.instance().doGetRequest(url, headers(null)), url);
    }

    public static String postJson(String url, String json) throws IOException {
        Map<String, String> headers = headers(null);
        headers.put("Content-Type", "application/json");
        return execute(OkHttpManager.instance().doPostRequest(url, headers, json, "application/json"), url);
    }

    /**
     * Checks that the url is playable: asks for the first byte only.
     */
    public static boolean isReachable(String url) {
        Response response = null;

        try {
            Map<String, String> headers = headers(null);
            headers.put("Range", "bytes=0-0");
            response = OkHttpManager.instance().doGetRequest(url, headers);
            return response != null && (response.code() == 200 || response.code() == 206);
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }

    private static String execute(Response response, String url) throws IOException {
        if (response == null) {
            throw new IOException("No response from " + url);
        }

        try {
            ResponseBody body = response.body();

            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code() + " from " + url);
            }

            return body.string();
        } finally {
            if (response.body() != null) {
                response.body().close();
            }
        }
    }

    private static Map<String, String> headers(Map<String, String> extra) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");

        if (extra != null) {
            headers.putAll(extra);
        }

        return headers;
    }
}
