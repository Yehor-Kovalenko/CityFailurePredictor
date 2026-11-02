package com.citydisruptors.api_gateway.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong(0);

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1, 100, clock::get);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void refillsTokensOverTime() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 2, 100, clock::get);
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");
        assertThat(limiter.tryAcquire("client")).isFalse();

        clock.addAndGet(SECOND / 2);

        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void neverRefillsAboveCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 10, 100, clock::get);
        limiter.tryAcquire("client");

        clock.addAndGet(60 * SECOND);

        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void limitsEveryClientSeparately() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, 100, clock::get);

        assertThat(limiter.tryAcquire("first")).isTrue();
        assertThat(limiter.tryAcquire("first")).isFalse();
        assertThat(limiter.tryAcquire("second")).isTrue();
    }

    @Test
    void evictsRefilledBucketsWhenTooManyClientsAreTracked() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, 2, clock::get);
        limiter.tryAcquire("first");
        limiter.tryAcquire("second");

        limiter.tryAcquire("third");
        assertThat(limiter.trackedClients()).isEqualTo(3);

        clock.addAndGet(10 * SECOND);
        limiter.tryAcquire("fourth");

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, 1, 1, clock::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(1, 0, 1, clock::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(1, 1, 0, clock::get))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
