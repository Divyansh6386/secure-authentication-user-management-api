package com.company.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional admin-seed credentials, bound from {@code app.admin.*}.
 *
 * <p>Only registered when {@code app.admin-seed.enabled=true}. Components are
 * defaulted to blank so enabling the seed in an environment that does not define
 * {@code app.admin.*} binds cleanly; the seeder then skips on blank values.
 */
@ConfigurationProperties(prefix = "app.admin")
@ConditionalOnProperty(
        name = "app.admin-seed.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public record AdminProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String password
) {
}
