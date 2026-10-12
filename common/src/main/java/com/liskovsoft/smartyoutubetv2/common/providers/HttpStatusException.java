package com.liskovsoft.smartyoutubetv2.common.providers;

import java.io.IOException;

/**
 * A request that reached the server but was answered with an error status.
 */
public class HttpStatusException extends IOException {
    public final int code;
    /** Start of the error page / JSON the server sent with the status. May be null. */
    public final String body;

    public HttpStatusException(int code, String url) {
        this(code, url, null);
    }

    public HttpStatusException(int code, String url, String body) {
        super("HTTP " + code + " from " + url + (body != null && !body.isEmpty() ? ": " + body : ""));
        this.code = code;
        this.body = body;
    }
}
