package com.citydisruptors.api_gateway.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class TokenBucketRateLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final double capacity;
    private final double refillPerNano;
    private final int maxTrackedClients;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, int maxTrackedClients, LongSupplier nanoClock) {
        if (capacity < 1 || refillPerSecond <= 0 || maxTrackedClients < 1) {
            throw new IllegalArgumentException("capacity, refill rate and client limit must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.maxTrackedClients = maxTrackedClients;
        this.nanoClock = nanoClock;
    }

    public boolean tryAcquire(String clientKey) {
        long now = nanoClock.getAsLong();
        if (buckets.size() >= maxTrackedClients && !buckets.containsKey(clientKey)) {
            evictRefilledBuckets(now);
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> new Bucket(capacity, now));
        synchronized (bucket) {
            bucket.refill(now);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return true;
            }
            return false;
        }
    }

    int trackedClients() {
        return buckets.size();
    }

    private void evictRefilledBuckets(long now) {
        buckets.values().removeIf(bucket -> {
            synchronized (bucket) {
                bucket.refill(now);
                return bucket.tokens >= capacity;
            }
        });
    }

    private final class Bucket {
        private double tokens;
        private long lastRefill;

        private Bucket(double tokens, long lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }

        private void refill(long now) {
            long elapsed = now - lastRefill;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                lastRefill = now;
            }
        }
    }
}
