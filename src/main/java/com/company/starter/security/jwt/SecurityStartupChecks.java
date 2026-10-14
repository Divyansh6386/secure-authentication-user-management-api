package com.company.starter.security.jwt;

import com.company.starter.config.AppProperties;
import com.company.starter.config.CorsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Fail-fast validation of security-critical configuration. If any check fails
 * the application context refuses to start, so a misconfigured deployment never
 * comes up with a weak secret or an open CORS policy.
 */
@Component
public class SecurityStartupChecks {

    private final AppProperties appProperties;
    private final CorsProperties corsProperties;

    public SecurityStartupChecks(AppProperties appProperties, CorsProperties corsProperties) {
        this.appProperties = appProperties;
        this.corsProperties = corsProperties;
    }

    @PostConstruct
    public void validateCriticalSecurityConfig() {
        // JWT
        String secret = appProperties.jwt().secret() == null ? "" : appProperties.jwt().secret().trim();
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be provided (non-blank). Refusing to start.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long. Refusing to start.");
        }

        // CORS (extra safety)
        List<String> origins = Arrays.stream(corsProperties.allowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must be provided. Refusing to start.");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not contain '*'. Refusing to start.");
        }
    }
}
