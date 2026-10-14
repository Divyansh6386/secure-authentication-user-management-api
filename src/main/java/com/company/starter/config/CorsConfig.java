package com.company.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private final CorsProperties corsProperties;
    private final Environment environment;

    public CorsConfig(CorsProperties corsProperties, Environment environment) {
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(corsProperties.allowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        boolean isProdLike = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equals("prod") || p.equals("staging"));

        /*
         * Hard security validation (fail-fast)
         */
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "CORS allowed-origins resolved to an empty list. Refusing to start.");
        }

        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS allowed-origins must not contain '*'. Refusing to start.");
        }

        if (isProdLike && origins.stream().anyMatch(
                o -> !(o.startsWith("http://") || o.startsWith("https://")))) {
            throw new IllegalStateException(
                    "CORS allowed-origins contains invalid origin(s). Only http/https are allowed in prod/staging.");
        }

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
