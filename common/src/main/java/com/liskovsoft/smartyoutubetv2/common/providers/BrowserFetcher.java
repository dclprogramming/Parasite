package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Loads pages through a hidden WebView, for sites that refuse plain HTTP clients (e.g. Cloudflare checks).<br/>
 * The WebView passes the browser check once, then requests are made with fetch() inside the page,
 * so they carry the browser's cookies and fingerprint.
 */
public final class BrowserFetcher {
    private static final long TIMEOUT_SEC = 45;
    private static final int CHECK_ATTEMPTS = 25; // one per second
    private static final Object LOCK = new Object(); // one request at a time
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Everything below is touched on the main thread only (except the request fields)
    private static WebView sWebView;
    private static String sClearedOrigin;
    private static boolean sPageLoaded;
    private static Request sCurrent;

    private static class Request {
        final CountDownLatch done = new CountDownLatch(1);
        volatile int status;
        volatile String body;
        volatile String error;

        void finish(int status, String body, String error) {
            this.status = status;
            this.body = body;
            this.error = error;
            done.countDown();
        }
    }

    private BrowserFetcher() {
    }

    /**
     * @param origin e.g. "https://rumble.com". The page that passes the browser check.
     * @param url    absolute url on the same origin.
     */
    public static String fetch(String origin, String url) throws IOException {
        Context context = ProviderData.getAppContext();

        if (context == null) {
            throw new IOException("Browser fetch is not ready yet");
        }

        synchronized (LOCK) {
            Request request = new Request();
            MAIN.post(() -> begin(context, origin, url, request));

            try {
                if (!request.done.await(TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    throw new IOException("Browser fetch timed out: " + url);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }

            if (request.error != null) {
                throw new IOException(request.error);
            }

            if (request.status < 200 || request.status >= 300) {
                throw new HttpStatusException(request.status, url);
            }

            return request.body;
        }
    }

    // Main thread

    private static void begin(Context context, String origin, String url, Request request) {
        try {
            sCurrent = request;
            WebView view = webView(context);

            if (origin.equals(sClearedOrigin)) {
                runFetch(view, url, request);
            } else {
                sPageLoaded = false;
                view.loadUrl(origin + "/");
                waitForBrowserCheck(view, origin, url, request, 0);
            }
        } catch (Throwable e) { // e.g. no WebView installed on this device
            request.finish(0, null, "Browser fetch is not available: " + e);
        }
    }

    private static WebView webView(Context context) {
        if (sWebView == null) {
            WebView view = new WebView(context.getApplicationContext());
            WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUserAgentString(settings.getUserAgentString().replace("; wv", "")); // look like plain Chrome
            view.addJavascriptInterface(new Bridge(), "ParasiteBridge");
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String pageUrl) {
                    sPageLoaded = true;
                }
            });
            sWebView = view;
        }

        return sWebView;
    }

    /**
     * The site may show an interstitial ("Just a moment...") that solves itself in a real browser. Wait for it.
     */
    private static void waitForBrowserCheck(WebView view, String origin, String url, Request request, int attempt) {
        MAIN.postDelayed(() -> {
            try {
                if (!sPageLoaded) {
                    retryOrFail(view, origin, url, request, attempt);
                    return;
                }

                view.evaluateJavascript("document.title", title -> {
                    String text = title != null ? title.toLowerCase() : "";
                    boolean challenge = text.contains("just a moment") || text.contains("attention required") || text.contains("checking");

                    if (!challenge) {
                        sClearedOrigin = origin;
                        runFetch(view, url, request);
                    } else {
                        retryOrFail(view, origin, url, request, attempt);
                    }
                });
            } catch (Throwable e) {
                request.finish(0, null, "Browser check failed: " + e);
            }
        }, 1000);
    }

    private static void retryOrFail(WebView view, String origin, String url, Request request, int attempt) {
        if (attempt + 1 >= CHECK_ATTEMPTS) {
            request.finish(0, null, "The site's browser check did not pass");
        } else {
            waitForBrowserCheck(view, origin, url, request, attempt + 1);
        }
    }

    private static void runFetch(WebView view, String url, Request request) {
        String script = "(function(){fetch(" + JSONObject.quote(url) + ",{credentials:'include'})"
                + ".then(function(r){return r.text().then(function(t){ParasiteBridge.done(r.status,t);});})"
                + ".catch(function(e){ParasiteBridge.fail(String(e));});})();";
        view.evaluateJavascript(script, null);
    }

    /** Called from the page (a background thread). */
    private static class Bridge {
        @JavascriptInterface
        public void done(int status, String body) {
            Request request = sCurrent;

            if (request != null) {
                request.finish(status, body, null);
            }
        }

        @JavascriptInterface
        public void fail(String message) {
            Request request = sCurrent;

            if (request != null) {
                request.finish(0, null, "Browser fetch failed: " + message);
            }
        }
    }
}
