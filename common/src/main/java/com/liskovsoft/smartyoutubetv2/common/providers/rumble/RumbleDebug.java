package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a Rumble page gives no videos, say what the page looked like (first few times per run),
 * so a changed page layout can be spotted without a debugger.
 */
final class RumbleDebug {
    private static final String TAG = "ParasiteRumble";
    private static final Pattern VIDEO_LINK = Pattern.compile("href=\"[^\"]*/v[0-9a-z]{3,12}-[^\"]*\\.html");
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static int sReports;

    private RumbleDebug() {
    }

    static String describe(String path, String html) {
        int links = 0;
        Matcher link = VIDEO_LINK.matcher(html);

        while (link.find()) {
            links++;
        }

        Matcher title = TITLE.matcher(html);
        String pageTitle = title.find() ? title.group(1).replaceAll("\\s+", " ").trim() : "no title";
        return "Rumble " + path + ": no videos found. Page: " + html.length() + " chars, " + links + " video links, \"" + pageTitle + "\"";
    }

    static synchronized void emptyListing(String path, String html) {
        String message = describe(path, html == null ? "" : html);
        Log.w(TAG, message);

        Context context = ProviderData.getAppContext();

        if (context == null || sReports >= 3) {
            return;
        }

        sReports++;
        new Handler(Looper.getMainLooper()).post(() -> MessageHelpers.showLongMessage(context, message));
    }
}
