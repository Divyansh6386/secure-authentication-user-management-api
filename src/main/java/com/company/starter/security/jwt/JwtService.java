package com.company.starter.security.jwt;

import com.company.starter.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_VERSION = "ver";

    private final AppProperties appProperties;
    private final SecretKey secretKey;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subject, TokenType type, int tokenVersion) {
        return generateToken(subject, type, tokenVersion, List.of());
    }

    public String generateToken(String subject, TokenType type, int tokenVersion, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = switch (type) {
            case ACCESS -> now.plus(appProperties.jwt().accessExpMinutes(), ChronoUnit.MINUTES);
            case REFRESH -> now.plus(appProperties.jwt().refreshExpDays(), ChronoUnit.DAYS);
        };

        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_VERSION, tokenVersion)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Builds a refresh token carrying a {@code jti} (its server-side row id), so
     * the token can be looked up, rotated, and revoked individually.
     */
    public String generateRefreshToken(String subject, int tokenVersion, String jti, Instant expiresAt) {
        return Jwts.builder()
                .subject(subject)
                .id(jti)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TokenType.REFRESH.name())
                .claim(CLAIM_VERSION, tokenVersion)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        return isValid(token, null);
    }

    public boolean isValid(String token, TokenType expectedType) {
        try {
            Claims claims = parseClaims(token);

            Object tokenType = claims.get(CLAIM_TYPE);
            if (expectedType != null && !expectedType.name().equals(String.valueOf(tokenType))) {
                return false;
            }

            Date exp = claims.getExpiration();
            if (exp == null) {
                return false;
            }
            return exp.toInstant().isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object rolesAny = parseClaims(token).get(CLAIM_ROLES);
        if (rolesAny instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    public int getTokenVersion(String token) {
        Object ver = parseClaims(token).get(CLAIM_VERSION);
        return toTokenVersion(ver);
    }

    /**
     * Coerces a JWT "ver" claim to an int. JSON numbers may deserialize as
     * Integer or Long, and a hand-crafted token could carry a String, so all
     * three are handled and anything else falls back to 0.
     */
    public static int toTokenVersion(Object value) {
        return switch (value) {
            case Integer i -> i;
            case Number n -> n.intValue();
            case String s -> {
                try {
                    yield Integer.parseInt(s.trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            case null, default -> 0;
        };
    }
}
