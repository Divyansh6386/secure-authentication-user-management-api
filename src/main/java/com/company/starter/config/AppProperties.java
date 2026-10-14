package com.company.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application JWT configuration, bound from {@code app.jwt.*}.
 *
 * <p>Constructor-bound record: relaxed binding maps {@code app.jwt.access-exp-minutes}
 * to {@link JwtProperties#accessExpMinutes()} and so on.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(JwtProperties jwt) {

    public record JwtProperties(
            String secret,
            long accessExpMinutes,
            long refreshExpDays
    ) {
    }
}
