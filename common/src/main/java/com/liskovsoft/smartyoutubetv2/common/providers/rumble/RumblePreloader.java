package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;
import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderAuth;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets Rumble ready while the app starts: starts the hidden browser, then loads the first page of the home, trending
 * and category lists one by one in the background (low priority: a screen the viewer opens always goes first).
 * The lists are saved, so the next start opens them at once.
 */
public final class RumblePreloader {
    private static boolean sStarted;

    private static final String SUBSCRIPTIONS = "/subscriptions";

    private RumblePreloader() {
    }

    private static boolean signedIn() {
        android.content.Context context = ProviderData.getAppContext();
        return context != null && ProviderAuth.isSignedIn(context, ProviderData.RUMBLE);
    }

    public static synchronized void start() {
        if (sStarted) {
            return;
        }

        sStarted = true;
        Thread thread = new Thread(RumblePreloader::run, "RumblePreloader");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** The first page of every list the screens show. Keep in sync with RumbleContentService (home, trending, categories). */
    private static List<String> paths() {
        List<String> paths = new ArrayList<>();
        paths.add("/editor-picks");
        paths.add(RumbleApi.browsePath("views", "today"));
        paths.add(RumbleApi.categoryPath("news"));
        paths.add(RumbleApi.categoryPath("gaming"));
        paths.add(RumbleApi.browsePath("views", "this-week"));

        for (String category : new String[]{"gaming", "music", "news", "sports"}) {
            String path = RumbleApi.categoryPath(category);

            if (!paths.contains(path)) {
                paths.add(path);
            }

            paths.add(path + "?sort=views&date=today");
        }

        return paths;
    }

    private static void run() {
        try {
            Thread.sleep(2_000); // let the app draw its first screen
            BrowserFetcher.warmUp("https://rumble.com");

            List<String> failed = new ArrayList<>();

            if (signedIn()) { // the account's own feed matters most: first
                BrowserFetcher.waitUntilIdle(60_000);

                if (!RumbleApi.preloadSigned(SUBSCRIPTIONS)) {
                    failed.add(null);
                }
            }

            for (String path : paths()) {
                BrowserFetcher.waitUntilIdle(60_000); // never in the way of a screen

                if (!RumbleApi.preload(path)) {
                    failed.add(path);
                }
            }

            for (String path : failed) { // once more: those that were stopped by a screen
                BrowserFetcher.waitUntilIdle(60_000);

                if (path == null) {
                    RumbleApi.preloadSigned(SUBSCRIPTIONS);
                } else {
                    RumbleApi.preload(path);
                }
            }

            while (true) { // keep the account's feed recent while the app stays open
                Thread.sleep(15 * 60_000L);

                if (signedIn()) {
                    BrowserFetcher.waitUntilIdle(60_000);
                    RumbleApi.preloadSigned(SUBSCRIPTIONS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // preloading is only a speed-up
        }
    }
}
