package com.company.starter.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @NotBlank(message = "CORS allowed-origins must be provided")
        String allowedOrigins
) {
}
