package com.company.starter.security.ratelimit;

import com.company.starter.common.error.ErrorCode;
import com.company.starter.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Throttles the unauthenticated auth endpoints (login + register) per client IP
 * to blunt credential-stuffing and brute-force attempts. Over-limit requests get
 * a {@code 429} with a {@code Retry-After} header and the standard error envelope.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register");

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitingService.isEnabled() || !isLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + request.getRequestURI();
        ConsumptionProbe probe = rateLimitingService.tryConsume(key);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        writeTooManyRequests(request, response, retryAfterSeconds);
    }

    private boolean isLimited(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response,
            long retryAfterSeconds) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        ErrorCode errorCode = ErrorCode.RATE_LIMITED;
        HttpStatus httpStatus = errorCode.getHttpStatus();

        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                errorCode.name(),
                "Too many requests. Please retry later.",
                request.getRequestURI(),
                null);

        response.setStatus(httpStatus.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
