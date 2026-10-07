package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private static final String TAG = "ParasiteBrowser";
    private static final long TIMEOUT_SEC = 45;
    private static final int CHECK_ATTEMPTS = 25; // one per second
    private static final Object LOCK = new Object(); // one request at a time
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Everything below is touched on the main thread only (except the request fields)
    private static WebView sWebView;
    private static String sClearedOrigin;
    private static boolean sPageLoaded;
    private static Request sCurrent;

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger();

    private static class Request {
        final int id = NEXT_ID.incrementAndGet(); // late answers of an abandoned request must not finish a newer one
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
            } finally {
                MAIN.post(BrowserFetcher::park);
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

    /**
     * Loads {@code pageUrl} in the hidden browser and runs {@code script} once a second until it returns a non-empty
     * string (or {@code maxWaitSec} passes). The script must return a string; "" means "not ready yet".
     */
    public static String scrape(String pageUrl, String script, int maxWaitSec) throws IOException {
        return scrape(pageUrl, script, maxWaitSec, false);
    }

    /**
     * @param autoplay let the page start video by itself (needed to read what a player loads; otherwise keep it off)
     */
    public static String scrape(String pageUrl, String script, int maxWaitSec, boolean autoplay) throws IOException {
        Context context = ProviderData.getAppContext();

        if (context == null) {
            throw new IOException("Browser is not ready yet");
        }

        synchronized (LOCK) {
            Request request = new Request();
            MAIN.post(() -> beginScrape(context, pageUrl, script, maxWaitSec, autoplay, request));

            try {
                if (!request.done.await(maxWaitSec + 15L, TimeUnit.SECONDS)) {
                    throw new IOException("Browser scrape timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } finally {
                MAIN.post(BrowserFetcher::park); // stop whatever the page is doing (video, scripts) right away
            }

            if (request.error != null) {
                throw new IOException(request.error);
            }

            return request.body;
        }
    }

    // Main thread

    /** Leaves the page: no video, timers or scripts keep running (and holding a decoder) between requests. */
    private static void park() {
        if (sWebView != null) {
            sWebView.loadUrl("about:blank");
            sWebView.onPause();
        }

        sClearedOrigin = null;
        sPageLoaded = false;
    }

    private static void beginScrape(Context context, String pageUrl, String script, int maxWaitSec, boolean autoplay, Request request) {
        try {
            sCurrent = request;
            WebView view = webView(context);
            view.onResume();
            view.getSettings().setMediaPlaybackRequiresUserGesture(!autoplay);
            sPageLoaded = false;
            view.loadUrl(pageUrl);
            pollScrape(view, script, request, 0, maxWaitSec);
        } catch (Throwable e) {
            Log.e(TAG, "WebView failed", e);
            request.finish(0, null, "Browser is not available: " + e);
        }
    }

    private static void pollScrape(WebView view, String script, Request request, int attempt, int maxAttempts) {
        MAIN.postDelayed(() -> {
            try {
                view.evaluateJavascript(script, value -> {
                    String result = decodeJsString(value);

                    if (result != null && !result.isEmpty()) {
                        request.finish(200, result, null);
                    } else if (attempt + 1 >= maxAttempts) {
                        view.evaluateJavascript(DIAGNOSE, state -> request.finish(0, null,
                                "The page's player did not start (" + decodeJsString(state) + ")"));
                    } else {
                        pollScrape(view, script, request, attempt + 1, maxAttempts);
                    }
                });
            } catch (Throwable e) {
                request.finish(0, null, "Browser scrape failed: " + e);
            }
        }, 1000);
    }

    /** What the page looks like: title, whether a video element exists, and the start of its text. */
    private static final String DIAGNOSE = "(function(){var t=document.body?document.body.innerText.replace(/\\s+/g,' ').substring(0,60):'';"
            + "return 'title: '+document.title+'; video element: '+(document.querySelector('video')?'yes':'no')+'; text: '+t;})()";

    /** evaluateJavascript hands back a JSON-encoded value: "\"text\"" or null. */
    private static String decodeJsString(String value) {
        if (value == null || value.equals("null")) {
            return null;
        }

        try {
            Object parsed = new org.json.JSONTokener(value).nextValue();
            return parsed instanceof String ? (String) parsed : null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private static void begin(Context context, String origin, String url, Request request) {
        try {
            sCurrent = request;
            WebView view = webView(context);
            view.onResume();
            view.getSettings().setMediaPlaybackRequiresUserGesture(true);

            if (origin.equals(sClearedOrigin)) {
                runFetch(view, url, request);
            } else {
                sPageLoaded = false;
                view.loadUrl(origin + "/robots.txt"); // a tiny page of the site, enough for same-site requests
                waitForBrowserCheck(view, origin, url, request, 0);
            }
        } catch (Throwable e) { // e.g. no WebView installed on this device
            Log.e(TAG, "WebView failed", e);
            request.finish(0, null, "Browser fetch is not available: " + e);
        }
    }

    private static WebView webView(Context context) {
        if (sWebView == null) {
            WebView view = new WebView(context.getApplicationContext());
            WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(true); // pages must not start videos on their own: they would hold the TV's video decoder
            settings.setUserAgentString(settings.getUserAgentString().replace("; wv", "")); // look like plain Chrome
            view.addJavascriptInterface(new Bridge(), "ParasiteBridge");
            // Never shown, but pages lay out for a desktop-sized window and load everything inside it
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(4000, android.view.View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 1280, 4000);
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String pageUrl) {
                    sPageLoaded = pageUrl != null && !pageUrl.startsWith("about:"); // the parked blank page doesn't count
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
                + ".then(function(r){return r.text().then(function(t){ParasiteBridge.done(" + request.id + ",r.status,t);});})"
                + ".catch(function(e){ParasiteBridge.fail(" + request.id + ",String(e));});})();";
        view.evaluateJavascript(script, null);
    }

    /** Called from the page (a background thread). */
    public static class Bridge {
        @JavascriptInterface
        public void done(int id, int status, String body) {
            Request request = sCurrent;

            Log.d(TAG, "fetch finished, status " + status);

            if (request != null && request.id == id) {
                request.finish(status, body, null);
            }
        }

        @JavascriptInterface
        public void fail(int id, String message) {
            Request request = sCurrent;

            if (request != null && request.id == id) {
                request.finish(0, null, "Browser fetch failed: " + message);
            }
        }
    }
}
