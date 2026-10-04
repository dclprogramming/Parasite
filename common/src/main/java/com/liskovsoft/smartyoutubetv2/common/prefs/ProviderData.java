package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;

/**
 * Which video provider the app is set to use. Only YouTube is wired up so far; Rumble and Odysee
 * can be selected in the UI once {@link #isAvailable(int)} returns true for them.
 */
public final class ProviderData {
    public static final int YOUTUBE = 0;
    public static final int RUMBLE = 1;
    public static final int ODYSEE = 2;
    public static final int COUNT = 3;

    private static final String PREFS = "parasite_providers";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_SIDEBAR_MIGRATED = "sidebar_migrated";

    private static Context sAppContext;

    private ProviderData() {
    }

    /** Call once from Application.onCreate: the service layer reads the provider before any UI exists. */
    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
    }

    public static Context getAppContext() {
        return sAppContext;
    }

    /** The provider chosen in the Providers page (YouTube until init is called). */
    public static int getSelected() {
        return sAppContext != null ? getSelected(sAppContext) : YOUTUBE;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getSelected(Context context) {
        int selected = prefs(context).getInt(KEY_SELECTED, YOUTUBE);
        return isAvailable(selected) ? selected : YOUTUBE;
    }

    public static void setSelected(Context context, int provider) {
        if (isAvailable(provider)) {
            prefs(context).edit().putInt(KEY_SELECTED, provider).commit();
        }
    }

    /** Flip these to true as provider modules get implemented (Rumble is not done yet). */
    public static boolean isAvailable(int provider) {
        return provider == YOUTUBE || provider == ODYSEE;
    }

    /**
     * Sidebar sections that make sense for the active provider.<br/>
     * Everything is available on YouTube. Other providers have no account here, so account-only
     * sections (my videos, playlists, notifications, shorts...) are hidden.
     */
    public static boolean supportsSection(int sectionId) {
        if (getSelected() == YOUTUBE) {
            return true;
        }

        switch (sectionId) {
            case MediaGroup.TYPE_HOME:
            case MediaGroup.TYPE_TRENDING:
            case MediaGroup.TYPE_SPORTS:
            case MediaGroup.TYPE_GAMING:
            case MediaGroup.TYPE_NEWS:
            case MediaGroup.TYPE_MUSIC:
            case MediaGroup.TYPE_CHANNEL_UPLOADS:
            case MediaGroup.TYPE_SUBSCRIPTIONS:
            case MediaGroup.TYPE_HISTORY:
            case MediaGroup.TYPE_PLAYBACK_QUEUE:
            case MediaGroup.TYPE_SETTINGS:
            case BrowseSection.ID_PROVIDERS:
                return true;
            default:
                return false;
        }
    }

    /** One-time flag: the Providers button has been added to an existing sidebar. */
    public static boolean isSidebarMigrated(Context context) {
        return prefs(context).getBoolean(KEY_SIDEBAR_MIGRATED, false);
    }

    public static void setSidebarMigrated(Context context) {
        prefs(context).edit().putBoolean(KEY_SIDEBAR_MIGRATED, true).apply();
    }
}
