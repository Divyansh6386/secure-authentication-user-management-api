package com.company.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate-limit configuration, bound from {@code app.rate-limit.*}.
 *
 * <p>{@code capacity} requests are allowed per {@code windowSeconds} window, per
 * client IP, on the throttled auth endpoints. Disable with {@code enabled=false}
 * (the test profile does this so the suite is not throttled).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int capacity,
        @DefaultValue("60") long windowSeconds
) {
}
