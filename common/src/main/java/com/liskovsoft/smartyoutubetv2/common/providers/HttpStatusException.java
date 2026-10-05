package com.liskovsoft.smartyoutubetv2.common.providers;

import java.io.IOException;

/**
 * A request that reached the server but was answered with an error status.
 */
public class HttpStatusException extends IOException {
    public final int code;

    public HttpStatusException(int code, String url) {
        super("HTTP " + code + " from " + url);
        this.code = code;
    }
}
