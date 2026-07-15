package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP upload rate limit: max 20 uploads per minute (spec §8). In-memory sliding
 * window per IP. Resets when the test/app restarts — sufficient for the anonymous MVP.
 */
@Service
public class UploadRateLimiter {
    private static final int MAX_PER_MINUTE = 20;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    public void check(String ip) {
        if (ip == null || ip.isBlank()) return;
        // Bound memory: if many distinct IPs have accumulated, evict expired windows so the
        // map cannot grow unbounded under a rotating-IP attack.
        if (counters.size() > 10_000) {
            long cutoff = System.currentTimeMillis() - 60_000L;
            counters.entrySet().removeIf(e -> e.getValue().windowStart < cutoff);
        }
        Window w = counters.computeIfAbsent(ip, k -> new Window());
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (now - w.windowStart > 60_000L) {
                w.count = 0;
                w.windowStart = now;
            }
            w.count++;
            if (w.count > MAX_PER_MINUTE) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "上传过于频繁，请稍后再试", 429);
            }
        }
    }

    private static final class Window {
        long windowStart = System.currentTimeMillis();
        int count = 0;
    }
}
