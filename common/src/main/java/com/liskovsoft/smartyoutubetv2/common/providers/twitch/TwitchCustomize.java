package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Customize" sidebar entry of Twitch: which rows Home and Gaming show, and how much each row holds.
 * Changes apply the next time the section is loaded (open it again or refresh).
 */
public final class TwitchCustomize {
    private TwitchCustomize() {
    }

    public static List<SettingsItem> items(Context context) {
        TwitchPrefs prefs = new TwitchPrefs(context);
        List<SettingsItem> items = new ArrayList<>();

        items.add(new SettingsItem("Home rows", () -> homeRows(context, prefs), R.drawable.icon_home));
        items.add(new SettingsItem("Gaming rows", () -> gamingRows(context, prefs), R.drawable.icon_gaming));
        items.add(new SettingsItem("Items per row", () -> itemsPerRow(context, prefs), R.drawable.icon_settings));
        items.add(new SettingsItem("Category rows", () -> categoryRows(context, prefs), R.drawable.icon_settings));

        return items;
    }

    private static void homeRows(Context context, TwitchPrefs prefs) {
        List<OptionItem> options = new ArrayList<>();
        options.add(toggle(prefs, "Live now", "home_live", prefs.homeLive()));
        options.add(toggle(prefs, "Categories we think you'll like", "home_categories", prefs.homeCategories()));
        options.add(toggle(prefs, "Busiest categories (Just Chatting, IRL, games...)", "home_top_categories", prefs.homeTopCategories()));

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendCheckedCategory("Rows shown on Home", options);
        dialog.showDialog("Customize Home");
    }

    private static void gamingRows(Context context, TwitchPrefs prefs) {
        List<OptionItem> options = new ArrayList<>();
        options.add(toggle(prefs, "Team FPS", "gaming_team_fps", prefs.gamingTeamFps()));
        options.add(toggle(prefs, "RPGs", "gaming_rpgs", prefs.gamingRpgs()));
        options.add(toggle(prefs, "New games", "gaming_new_games", prefs.gamingNewGames()));
        options.add(toggle(prefs, "New game streams", "gaming_new_game_streams", prefs.gamingNewGameStreams()));
        options.add(toggle(prefs, "Busiest game categories", "gaming_top_categories", prefs.gamingTopCategories()));

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendCheckedCategory("Rows shown in Gaming", options);
        dialog.showDialog("Customize Gaming");
    }

    private static void itemsPerRow(Context context, TwitchPrefs prefs) {
        List<OptionItem> options = new ArrayList<>();

        for (int count : TwitchPrefs.streamsPerRowChoices()) {
            options.add(UiOptionItem.from(String.valueOf(count), option -> prefs.setStreamsPerRow(count), prefs.streamsPerRow() == count));
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendRadioCategory("How many items each row starts with", options);
        dialog.showDialog("Items per row");
    }

    private static void categoryRows(Context context, TwitchPrefs prefs) {
        List<OptionItem> options = new ArrayList<>();

        for (int count : TwitchPrefs.categoryRowChoices()) {
            options.add(UiOptionItem.from(String.valueOf(count), option -> prefs.setCategoryRows(count), prefs.categoryRows() == count));
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendRadioCategory("How many category rows Home and Gaming show", options);
        dialog.showDialog("Category rows");
    }

    private static OptionItem toggle(TwitchPrefs prefs, String title, String key, boolean current) {
        return UiOptionItem.from(title, option -> prefs.setBoolean(key, option.isSelected()), current);
    }
}
