package com.company.starter.security.ratelimit;

import com.company.starter.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-key token-bucket rate limiter.
 *
 * <p>One bucket per key (client IP + path). This is single-node only: for a
 * horizontally scaled deployment, back Bucket4j with a distributed store
 * (Redis/Hazelcast) via its {@code ProxyManager} instead of this map.
 */
@Service
public class RateLimitingService {

    private final RateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingService(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public ConsumptionProbe tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(properties.capacity())
                        .refillIntervally(properties.capacity(), Duration.ofSeconds(properties.windowSeconds())))
                .build();
    }
}
