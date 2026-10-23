package io.infra.adapter.identity.provider.support;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed-window counters per key, kept in this process and bounded in size.
 *
 * <p>Used for the request limit of the identity protocol surface and for failed-login
 * throttling per account and source (root {@code BRX-RS-SECURITY-014}); neither locks the
 * identity for other entries or sources.</p>
 *
 * @since 3.2.0
 */
public final class SourceRateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, long[]> windows = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, long[]> eldest) {
            return size() > MAX_KEYS;
        }
    };

    public SourceRateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    public SourceRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Counts one event; returns {@code false} when the key already used up its window. */
    public synchronized boolean tryAcquire(String key) {
        long now = clock.millis();
        long[] window = windows.get(key);
        if (window == null || now - window[0] >= windowMillis) {
            windows.put(key, new long[] {now, 1});
            return true;
        }
        if (window[1] >= limit) {
            return false;
        }
        window[1]++;
        return true;
    }

    /** Returns whether the key has used up its window, without counting. */
    public synchronized boolean isExhausted(String key) {
        long[] window = windows.get(key);
        return window != null && clock.millis() - window[0] < windowMillis && window[1] >= limit;
    }

    public synchronized void reset(String key) {
        windows.remove(key);
    }
}
