package com.company.starter.security.jwt;

import com.company.starter.common.error.ErrorCode;
import com.company.starter.common.error.ErrorResponse;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String message) throws IOException {

        if (response.isCommitted()) {
            return;
        }
        SecurityContextHolder.clearContext();

        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        HttpStatus httpStatus = errorCode.getHttpStatus();

        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                null);

        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), body);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No header => continue (public endpoints).
        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Header exists but malformed => 401.
        if (!authHeader.startsWith("Bearer ")) {
            writeUnauthorized(request, response, "Invalid Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            writeUnauthorized(request, response, "Invalid token");
            return;
        }

        try {
            // Token present but invalid/expired => 401 (do NOT continue).
            if (!jwtService.isValid(token, TokenType.ACCESS)) {
                writeUnauthorized(request, response, "Invalid or expired token");
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var claims = jwtService.parseClaims(token);

                String subject = claims.getSubject();
                if (subject == null || subject.isBlank()) {
                    writeUnauthorized(request, response, "Invalid token: missing subject");
                    return;
                }

                UUID userId;
                try {
                    userId = UUID.fromString(subject);
                } catch (IllegalArgumentException ex) {
                    writeUnauthorized(request, response, "Invalid token subject");
                    return;
                }

                int tokenVersion = JwtService.toTokenVersion(claims.get("ver"));

                Optional<User> userOpt = userRepository.findByIdAndDisabledAtIsNull(userId);
                if (userOpt.isEmpty() || userOpt.get().getTokenVersion() != tokenVersion) {
                    writeUnauthorized(request, response, "Invalid token");
                    return;
                }
                User user = userOpt.get();

                // Authority is derived from the persisted user role, never from the
                // token claims. This is the single source of truth: a role change (or
                // demotion) takes effect immediately and a stale "roles" claim in an
                // already-issued access token can never grant elevated access.
                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            // No silent failure.
            log.warn("JWT filter failure: {}", ex.getMessage());
            writeUnauthorized(request, response, "Invalid token");
        }
    }
}
