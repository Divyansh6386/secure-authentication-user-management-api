package com.company.starter.security.ratelimit;

import com.company.starter.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingServiceTest {

    private RateLimitingService service(int capacity, long windowSeconds) {
        return new RateLimitingService(new RateLimitProperties(true, capacity, windowSeconds));
    }

    @Test
    void allowsUpToCapacityThenBlocks() {
        RateLimitingService service = service(2, 60);

        assertThat(service.tryConsume("ip:/login").isConsumed()).isTrue();
        assertThat(service.tryConsume("ip:/login").isConsumed()).isTrue();
        assertThat(service.tryConsume("ip:/login").isConsumed()).isFalse();
    }

    @Test
    void keysAreThrottledIndependently() {
        RateLimitingService service = service(1, 60);

        assertThat(service.tryConsume("a").isConsumed()).isTrue();
        assertThat(service.tryConsume("a").isConsumed()).isFalse();

        // A different key has its own bucket.
        assertThat(service.tryConsume("b").isConsumed()).isTrue();
    }
}
