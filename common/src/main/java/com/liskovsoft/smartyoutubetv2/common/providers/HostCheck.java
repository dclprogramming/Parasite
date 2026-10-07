package com.liskovsoft.smartyoutubetv2.common.providers;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Can this network look up the server behind a url? A DNS filter or blocklist can hide the servers that hold
 * pictures and videos, which looks like blank cards or videos that never start.
 */
public final class HostCheck {
    private static final long KEEP_MS = 5 * 60 * 1000;
    private static final Map<String, Object[]> CACHE = new ConcurrentHashMap<>(); // host -> {time, Boolean}

    private HostCheck() {
    }

    public static String hostOf(String url) {
        try {
            return url == null ? null : new URI(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * False only when the name is certainly unknown to this network's DNS. Slow answers and odd errors count as "fine":
     * the player gets to decide.
     */
    public static boolean resolves(String url) {
        String host = hostOf(url);

        if (host == null) {
            return true;
        }

        Object[] cached = CACHE.get(host);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < KEEP_MS) {
            return (Boolean) cached[1];
        }

        boolean ok = lookup(host);
        CACHE.put(host, new Object[]{System.currentTimeMillis(), ok});
        return ok;
    }

    private static boolean lookup(String host) {
        for (int attempt = 0; attempt < 2; attempt++) { // DNS errors can be one-off
            ExecutorService pool = Executors.newSingleThreadExecutor();

            try {
                Future<InetAddress> result = pool.submit(() -> InetAddress.getByName(host));
                result.get(3, TimeUnit.SECONDS);
                return true;
            } catch (TimeoutException e) {
                return true;
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof UnknownHostException)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            } finally {
                pool.shutdownNow();
            }
        }

        return false;
    }
}
