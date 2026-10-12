package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the Twitch Home and Gaming sections show, and how much of it. Edited from the Customize sidebar entry.
 */
public final class TwitchPrefs {
    private static final String FILE = "twitch_prefs";
    private static final int[] STREAMS_PER_ROW = {10, 20, 30, 40};
    private static final int[] CATEGORY_ROWS = {3, 6, 9, 12};

    private final SharedPreferences mPrefs;

    public TwitchPrefs(Context context) {
        mPrefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // Home feeds

    public boolean homeLive() {
        return mPrefs.getBoolean("home_live", true);
    }

    public boolean homeCategories() {
        return mPrefs.getBoolean("home_categories", true);
    }

    public boolean homeTopCategories() {
        return mPrefs.getBoolean("home_top_categories", true);
    }

    // Gaming feeds

    public boolean gamingTeamFps() {
        return mPrefs.getBoolean("gaming_team_fps", true);
    }

    public boolean gamingRpgs() {
        return mPrefs.getBoolean("gaming_rpgs", true);
    }

    public boolean gamingNewGames() {
        return mPrefs.getBoolean("gaming_new_games", true);
    }

    public boolean gamingNewGameStreams() {
        return mPrefs.getBoolean("gaming_new_game_streams", true);
    }

    public boolean gamingTopCategories() {
        return mPrefs.getBoolean("gaming_top_categories", true);
    }

    public void setBoolean(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }

    // Amounts

    /** How many streams (or category cards) one row starts with. */
    public int streamsPerRow() {
        return pick(mPrefs.getInt("streams_per_row", 30), STREAMS_PER_ROW, 30);
    }

    /** How many category rows Home and Gaming show. */
    public int categoryRows() {
        return pick(mPrefs.getInt("category_rows", 6), CATEGORY_ROWS, 6);
    }

    public void setStreamsPerRow(int value) {
        mPrefs.edit().putInt("streams_per_row", value).apply();
    }

    public void setCategoryRows(int value) {
        mPrefs.edit().putInt("category_rows", value).apply();
    }

    public static int[] streamsPerRowChoices() {
        return STREAMS_PER_ROW;
    }

    public static int[] categoryRowChoices() {
        return CATEGORY_ROWS;
    }

    private static int pick(int value, int[] allowed, int fallback) {
        for (int a : allowed) {
            if (a == value) {
                return value;
            }
        }

        return fallback;
    }
}
