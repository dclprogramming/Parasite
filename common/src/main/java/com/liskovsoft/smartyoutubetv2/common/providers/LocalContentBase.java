package com.liskovsoft.smartyoutubetv2.common.providers;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaGroup;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;
import com.liskovsoft.smartyoutubetv2.common.providers.stub.StubContentService;

import io.reactivex.Observable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Parts of the content service shared by providers that have no login here:
 * followed channels and watch history live in a local {@link ProviderStore}.
 */
public abstract class LocalContentBase extends StubContentService {
    private static final int CHANNEL_SEARCH_PAGES = 5;   // how far into a channel's list a search looks
    private static final int CHANNEL_SEARCH_MAX = 60;    // stop once this many videos matched

    /**
     * Search inside one channel. These sites give no channel search of their own, so the channel's list is read
     * page by page (the pages are cached, so the ones already seen cost nothing) and kept where the title has every
     * word of the query.
     */
    protected static ProviderMediaGroup searchChannelPages(int type, String channelId, String query, ProviderMediaGroup... firstPages) throws Exception {
        ProviderMediaGroup result = new ProviderMediaGroup(type, "Search: " + query);
        result.setChannelId(channelId);

        String[] words = query == null ? new String[0] : query.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+");

        if (words.length == 0 || words[0].isEmpty()) {
            return result;
        }

        java.util.Set<String> seen = new java.util.HashSet<>();

        for (ProviderMediaGroup page : firstPages) {
            for (int i = 0; page != null && i < CHANNEL_SEARCH_PAGES && result.getMediaItems().size() < CHANNEL_SEARCH_MAX; i++) {
                for (MediaItem item : page.getMediaItems()) {
                    if (matchesAll(item.getTitle(), words) && seen.add(item.getVideoId() != null ? item.getVideoId() : String.valueOf(item.getTitle()))) {
                        result.add(item);
                    }
                }

                page = page.getNext() != null ? page.getNext().load() : null;
            }
        }

        return result;
    }

    private static boolean matchesAll(CharSequence title, String[] words) {
        if (title == null) {
            return false;
        }

        String lower = title.toString().toLowerCase(java.util.Locale.ROOT);

        for (String word : words) {
            if (!lower.contains(word)) {
                return false;
            }
        }

        return true;
    }

    protected final ProviderStore mStore;

    protected LocalContentBase(ProviderStore store) {
        mStore = store;
    }

    // Followed channels

    @Override
    public Observable<MediaGroup> getSubscribedChannelsObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNewContentObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNameObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(true));
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByLastViewedObserve() {
        return RxHelper.fromCallable(() -> channelsGroup(false));
    }

    protected MediaGroup channelsGroup(boolean sortByName) {
        List<ProviderMediaItem> channels = mStore.getChannels();

        if (sortByName) {
            Collections.sort(channels, (a, b) -> safe(a.title).compareToIgnoreCase(safe(b.title)));
        }

        ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "Channels");

        for (ProviderMediaItem channel : channels) {
            group.add(channel);
        }

        return group;
    }

    // History

    @Override
    public Observable<MediaGroup> getHistoryObserve() {
        return RxHelper.fromCallable(() -> {
            ProviderMediaGroup group = new ProviderMediaGroup(MediaGroup.TYPE_HISTORY, "History");

            for (ProviderMediaItem video : mStore.getHistory()) {
                group.add(video);
            }

            return group;
        });
    }

    @Override
    public void clearHistory() {
        mStore.clearHistory();
    }

    // Paging

    @Override
    public Observable<MediaGroup> continueGroupObserve(MediaGroup mediaGroup) {
        return RxHelper.fromCallable(() -> {
            if (mediaGroup instanceof ProviderMediaGroup && ((ProviderMediaGroup) mediaGroup).getNext() != null) {
                ProviderMediaGroup next = ((ProviderMediaGroup) mediaGroup).getNext().load();
                next.setChannelId(mediaGroup.getChannelId());
                return next;
            }

            return new ProviderMediaGroup(mediaGroup != null ? mediaGroup.getType() : MediaGroup.TYPE_UNDEFINED, null);
        });
    }

    // Helpers

    /**
     * Runs the loaders in parallel. A loader that fails is skipped; throws only if every loader failed.
     */
    protected List<MediaGroup> runParallel(List<Callable<ProviderMediaGroup>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, tasks.size())));
        List<MediaGroup> result = new ArrayList<>();
        Exception lastError = null;

        try {
            List<Future<ProviderMediaGroup>> futures = pool.invokeAll(tasks);

            for (Future<ProviderMediaGroup> future : futures) {
                try {
                    ProviderMediaGroup group = future.get();

                    if (group != null && !group.isEmpty()) {
                        result.add(group);
                    }
                } catch (ExecutionException e) {
                    lastError = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                }
            }
        } finally {
            pool.shutdown();
        }

        if (result.isEmpty() && lastError != null) {
            throw lastError;
        }

        return result;
    }

    public static String channelIdOf(MediaItem item) {
        if (item == null) {
            return null;
        }

        String key = item.getReloadPageKey();

        if (key != null && key.startsWith(ProviderMediaItem.CHANNEL_KEY_PREFIX)) {
            return key.substring(ProviderMediaItem.CHANNEL_KEY_PREFIX.length());
        }

        return item.getChannelId();
    }

    public static String channelIdOf(String reloadPageKey) {
        if (reloadPageKey != null && reloadPageKey.startsWith(ProviderMediaItem.CHANNEL_KEY_PREFIX)) {
            return reloadPageKey.substring(ProviderMediaItem.CHANNEL_KEY_PREFIX.length());
        }

        return null;
    }

    protected static String safe(String value) {
        return value != null ? value : "";
    }
}
