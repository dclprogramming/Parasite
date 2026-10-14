package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets Rumble ready while the app starts: starts the hidden browser, then loads the first page of the home, trending
 * and category lists one by one in the background (low priority: a screen the viewer opens always goes first).
 * The lists are saved, so the next start opens them at once.
 */
public final class RumblePreloader {
    private static boolean sStarted;

    private RumblePreloader() {
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

            for (String path : paths()) {
                BrowserFetcher.waitUntilIdle(60_000); // never in the way of a screen

                if (!RumbleApi.preload(path)) {
                    failed.add(path);
                }
            }

            for (String path : failed) { // once more: those that were stopped by a screen
                BrowserFetcher.waitUntilIdle(60_000);
                RumbleApi.preload(path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // preloading is only a speed-up
        }
    }
}
