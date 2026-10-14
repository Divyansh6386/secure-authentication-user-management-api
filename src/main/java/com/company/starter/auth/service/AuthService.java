package com.company.starter.auth.service;

import com.company.starter.auth.dto.AuthResponse;
import com.company.starter.auth.dto.ProfileResponse;
import com.company.starter.common.error.exceptions.BadRequestException;
import com.company.starter.common.error.exceptions.ConflictException;
import com.company.starter.common.error.exceptions.NotFoundException;
import com.company.starter.common.error.exceptions.UnauthorizedException;
import com.company.starter.config.AppProperties;
import com.company.starter.security.jwt.JwtService;
import com.company.starter.security.jwt.TokenType;
import com.company.starter.user.model.RefreshToken;
import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.RefreshTokenRepository;
import com.company.starter.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    @Transactional
    public AuthResponse register(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already exists");
        }

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                Role.USER,
                0);

        User saved = userRepository.save(user);

        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmailAndDisabledAtIsNull(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        return issueTokens(user);
    }

    // noRollbackFor: the reuse-detection branch revokes the token family and bumps
    // the token version, then throws. Without this, that security response would be
    // rolled back by the thrown exception.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String refreshToken) {
        if (countChar(refreshToken, '.') != 2) {
            throw new BadRequestException("Invalid refresh token");
        }

        Claims claims;
        try {
            claims = jwtService.parseClaims(refreshToken);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Check type.
        Object type = claims.get("type");
        if (!TokenType.REFRESH.name().equals(String.valueOf(type))) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Check expiry.
        Date exp = claims.getExpiration();
        if (exp == null || !exp.toInstant().isAfter(Instant.now())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            throw new BadRequestException("Invalid refresh token");
        }

        int tokenVersion = JwtService.toTokenVersion(claims.get("ver"));

        User user = userRepository.findByIdAndDisabledAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (user.getTokenVersion() != tokenVersion) {
            throw new UnauthorizedException("Refresh token has been invalidated");
        }

        UUID jti;
        try {
            jti = UUID.fromString(claims.getId());
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken stored = refreshTokenRepository.findById(jti)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Rotation guarantees each refresh token is single-use. Seeing an already
        // revoked token means it was replayed (likely stolen): revoke the whole
        // family and bump the user's token version so every session is forced to
        // re-authenticate.
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(userId, now);
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setUpdatedAt(now);
            userRepository.save(user);
            throw new UnauthorizedException("Refresh token reuse detected");
        }

        if (stored.isExpired(now)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Rotate: revoke the presented token and issue a fresh pair.
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logoutAll(UUID currentUserId) {
        User user = userRepository.findByIdAndDisabledAtIsNull(currentUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        OffsetDateTime now = OffsetDateTime.now();
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(now);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(currentUserId, now);
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(UUID currentUserId) {
        User user = userRepository.findByIdAndDisabledAtIsNull(currentUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getTokenVersion(),
                user.getDisabledAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private AuthResponse issueTokens(User user) {
        UUID userId = user.getId();
        if (userId == null) {
            throw new IllegalStateException("User id is null");
        }

        String access = jwtService.generateToken(
                userId.toString(),
                TokenType.ACCESS,
                user.getTokenVersion(),
                List.of(user.getRole().name()));

        UUID jti = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(appProperties.jwt().refreshExpDays(), ChronoUnit.DAYS);
        String refresh = jwtService.generateRefreshToken(
                userId.toString(),
                user.getTokenVersion(),
                jti.toString(),
                expiresAt);

        refreshTokenRepository.save(new RefreshToken(
                jti,
                userId,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));

        return new AuthResponse(access, refresh, user.getRole());
    }

    private static long countChar(String value, char c) {
        return value.chars().filter(ch -> ch == c).count();
    }
}
