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
        return get(url, null);
    }

    /**
     * @param extraHeaders replaces the default headers of the same name. May be null.
     */
    public static String get(String url, Map<String, String> extraHeaders) throws IOException {
        return execute(OkHttpManager.instance().doGetRequest(url, headers(extraHeaders)), url);
    }

    public static String postJson(String url, String json) throws IOException {
        return postJson(url, json, null);
    }

    public static String postJson(String url, String json, Map<String, String> extraHeaders) throws IOException {
        Map<String, String> headers = headers(extraHeaders);
        headers.put("Content-Type", "application/json");
        return execute(OkHttpManager.instance().doPostRequest(url, headers, json, "application/json"), url);
    }

    /**
     * Checks that the url is playable: asks for the first byte only.
     */
    public static boolean isReachable(String url) {
        int code = probe(url);
        return code == 200 || code == 206;
    }

    /**
     * HTTP status of a one-byte request, or 0 when the server could not be reached.
     */
    public static int probe(String url) {
        Response response = null;

        try {
            Map<String, String> headers = headers(null);
            headers.put("Range", "bytes=0-0");
            response = OkHttpManager.instance().doGetRequest(url, headers);
            return response != null ? response.code() : 0;
        } catch (RuntimeException e) {
            return 0;
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }

    /**
     * Size of the file behind the url in bytes (asks for one byte and reads the total), or -1 when the server doesn't say.
     */
    public static long contentLength(String url) {
        Response response = null;

        try {
            Map<String, String> headers = headers(null);
            headers.put("Range", "bytes=0-0");
            response = OkHttpManager.instance().doGetRequest(url, headers);

            if (response == null) {
                return -1;
            }

            String range = response.header("Content-Range"); // "bytes 0-0/12345"

            if (response.code() == 206 && range != null && range.contains("/")) {
                try {
                    return Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }

            return response.code() == 200 && response.body() != null ? response.body().contentLength() : -1;
        } catch (RuntimeException e) {
            return -1;
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
                throw new HttpStatusException(response.code(), url);
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
