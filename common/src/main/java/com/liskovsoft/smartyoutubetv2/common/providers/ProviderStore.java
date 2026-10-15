package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Local "account" for providers that have no login here: followed channels and watch history.<br/>
 * One store per provider, kept in app preferences.
 */
public class ProviderStore {
    private static final int MAX_HISTORY = 200;
    private final SharedPreferences mPrefs;
    // The saved lists are read from storage once and kept here: a follow or a history entry used to parse the whole
    // saved list again each time (the channel sync does that hundreds of times in a row)
    private final java.util.Map<String, List<ProviderMediaItem>> mCache = new java.util.HashMap<>();

    public ProviderStore(Context context, String name) {
        mPrefs = context.getApplicationContext().getSharedPreferences("parasite_store_" + name, Context.MODE_PRIVATE);
    }

    // Followed channels

    public synchronized List<ProviderMediaItem> getChannels() {
        return new ArrayList<>(items("channels"));
    }

    public synchronized boolean isFollowed(String channelId) {
        if (channelId == null) {
            return false;
        }

        for (ProviderMediaItem item : items("channels")) {
            if (channelId.equals(item.channelId)) {
                return true;
            }
        }

        return false;
    }

    public synchronized void follow(ProviderMediaItem channel) {
        if (channel == null || channel.channelId == null || isFollowed(channel.channelId)) {
            return;
        }

        List<ProviderMediaItem> items = getChannels();
        items.add(0, channel);
        writeItems("channels", items, Integer.MAX_VALUE);
    }

    /** Replaces a followed channel's saved details (e.g. once its picture is known). Keeps the list order. */
    public synchronized void updateChannel(ProviderMediaItem channel) {
        if (channel == null || channel.channelId == null) {
            return;
        }

        List<ProviderMediaItem> items = getChannels();

        for (int i = 0; i < items.size(); i++) {
            if (channel.channelId.equals(items.get(i).channelId)) {
                items.set(i, channel);
                writeItems("channels", items, Integer.MAX_VALUE);
                return;
            }
        }
    }

    public synchronized void unfollow(String channelId) {
        List<ProviderMediaItem> items = getChannels();
        List<ProviderMediaItem> result = new ArrayList<>();

        for (ProviderMediaItem item : items) {
            if (channelId == null || !channelId.equals(item.channelId)) {
                result.add(item);
            }
        }

        writeItems("channels", result, Integer.MAX_VALUE);
    }

    // Watch history (newest first)

    public synchronized List<ProviderMediaItem> getHistory() {
        return new ArrayList<>(items("history"));
    }

    public synchronized void addToHistory(ProviderMediaItem video) {
        if (video == null || video.videoId == null) {
            return;
        }

        List<ProviderMediaItem> items = getHistory();
        List<ProviderMediaItem> result = new ArrayList<>();
        result.add(video);

        for (ProviderMediaItem item : items) {
            if (!video.videoId.equals(item.videoId)) {
                result.add(item);
            }
        }

        writeItems("history", result, MAX_HISTORY);
    }

    public synchronized void clearHistory() {
        mCache.remove("history");
        mPrefs.edit().remove("history").apply();
    }

    private List<ProviderMediaItem> items(String key) {
        List<ProviderMediaItem> cached = mCache.get(key);

        if (cached == null) {
            cached = readItems(key);
            mCache.put(key, cached);
        }

        return cached;
    }

    private List<ProviderMediaItem> readItems(String key) {
        List<ProviderMediaItem> result = new ArrayList<>();
        String raw = mPrefs.getString(key, null);

        if (raw == null) {
            return result;
        }

        try {
            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {
                result.add(ProviderMediaItem.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Broken data. Start from scratch
            mPrefs.edit().remove(key).apply();
        }

        return result;
    }

    private void writeItems(String key, List<ProviderMediaItem> items, int max) {
        JSONArray array = new JSONArray();
        List<ProviderMediaItem> kept = new ArrayList<>(Math.min(items.size(), max));

        for (int i = 0; i < items.size() && i < max; i++) {
            array.put(items.get(i).toJson());
            kept.add(items.get(i));
        }

        mCache.put(key, kept);
        mPrefs.edit().putString(key, array.toString()).apply();
    }
}
