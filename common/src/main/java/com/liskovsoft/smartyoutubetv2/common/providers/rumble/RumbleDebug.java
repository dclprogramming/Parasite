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
    private static int sAudits;

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

    static synchronized void emptyRendered(String path) {
        report("Rumble " + path + ": the page showed no video cards");
    }

    static synchronized void emptyListing(String path, String html) {
        report(describe(path, html == null ? "" : html));
    }

    /**
     * Checks the first cards of a freshly read list against what Rumble says about each page and whether their pictures
     * can be looked up. Says nothing when all is well. Otherwise opens a report that can be photographed and sent along.
     * Call from a background thread.
     */
    static void auditListing(String path, java.util.List<RumbleParser.Entry> entries) {
        synchronized (RumbleDebug.class) {
            if (sAudits >= 2 || entries.isEmpty()) {
                return;
            }
        }

        StringBuilder text = new StringBuilder();
        boolean problem = false;
        int count = Math.min(3, entries.size());

        text.append("Page ").append(path).append(": ").append(entries.size()).append(" cards read.\n\n");

        for (int i = 0; i < count; i++) {
            RumbleParser.Entry entry = entries.get(i);
            RumbleParser.Oembed exact = RumbleApi.oembedFor(entry.stem != null ? entry.stem : entry.id);
            boolean titleMatches = exact == null || exact.title == null || similar(entry.title, exact.title);
            String thumbHost = com.liskovsoft.smartyoutubetv2.common.providers.HostCheck.hostOf(entry.thumb);
            boolean thumbResolves = entry.thumb == null || com.liskovsoft.smartyoutubetv2.common.providers.HostCheck.resolves(entry.thumb);

            text.append("Card ").append(i + 1).append(": id ").append(entry.stem).append("\n");
            text.append("  card title: ").append(entry.title).append("\n");
            text.append("  Rumble says: ").append(exact == null ? "(no answer)" : exact.title).append(titleMatches ? "  [match]" : "  [MISMATCH]").append("\n");
            text.append("  picture: ").append(entry.thumb == null ? "NONE FOUND" : thumbHost + (thumbResolves ? " (reachable)" : " (CANNOT BE LOOKED UP - DNS)")).append("\n");

            if (!titleMatches || entry.thumb == null || !thumbResolves) {
                problem = true;

                if (entry.html != null) {
                    text.append("  markup: ").append(entry.html.length() > 420 ? entry.html.substring(0, 420) : entry.html).append("\n");
                }
            }

            text.append("\n");
        }

        if (!problem) {
            return;
        }

        synchronized (RumbleDebug.class) {
            if (sAudits >= 2) {
                return;
            }

            sAudits++;
        }

        show("Rumble check (photograph this screen)", text.toString());
    }

    private static boolean similar(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);

        if (na.isEmpty() || nb.isEmpty()) {
            return true;
        }

        String headA = na.substring(0, Math.min(16, na.length()));
        String headB = nb.substring(0, Math.min(16, nb.length()));
        return nb.contains(headA) || na.contains(headB);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static void show(String title, String text) {
        Log.w(TAG, text);
        Context context = ProviderData.getAppContext();

        if (context == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter dialog =
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(context);
                dialog.appendLongTextCategory(title, com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(text));
                dialog.showDialog(title);
            } catch (RuntimeException e) {
                MessageHelpers.showLongMessage(context, title + ": " + (text.length() > 200 ? text.substring(0, 200) : text));
            }
        });
    }

    private static void report(String message) {
        Log.w(TAG, message);

        Context context = ProviderData.getAppContext();

        if (context == null || sReports >= 3) {
            return;
        }

        sReports++;
        new Handler(Looper.getMainLooper()).post(() -> MessageHelpers.showLongMessage(context, message));
    }
}
