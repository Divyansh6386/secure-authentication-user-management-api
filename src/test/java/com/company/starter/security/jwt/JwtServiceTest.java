package com.company.starter.security.jwt;

import com.company.starter.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final AppProperties props = new AppProperties(
            new AppProperties.JwtProperties(
                    "test-secret-test-secret-test-secret-0123456789",
                    15,
                    7));
    private final JwtService jwtService = new JwtService(props);

    @Test
    void accessTokenRoundTripsSubjectRolesAndVersion() {
        String subject = UUID.randomUUID().toString();

        String token = jwtService.generateToken(
                subject,
                TokenType.ACCESS,
                3,
                java.util.List.of("ADMIN"));

        assertThat(jwtService.getSubject(token)).isEqualTo(subject);
        assertThat(jwtService.getRoles(token)).containsExactly("ADMIN");
        assertThat(jwtService.getTokenVersion(token)).isEqualTo(3);
    }

    @Test
    void validAccessTokenPassesTypeCheck() {
        String token = jwtService.generateToken("sub", TokenType.ACCESS, 0);

        assertThat(jwtService.isValid(token, TokenType.ACCESS)).isTrue();
        assertThat(jwtService.isValid(token, TokenType.REFRESH)).isFalse();
    }

    @Test
    void refreshTokenIsNotAcceptedWhereAccessIsExpected() {
        String refresh = jwtService.generateToken("sub", TokenType.REFRESH, 0);

        assertThat(jwtService.isValid(refresh, TokenType.REFRESH)).isTrue();
        assertThat(jwtService.isValid(refresh, TokenType.ACCESS)).isFalse();
    }

    @Test
    void garbageAndTamperedTokensAreRejected() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();

        String token = jwtService.generateToken("sub", TokenType.ACCESS, 0);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService(
                new AppProperties(new AppProperties.JwtProperties(
                        "another-secret-another-secret-0123456789",
                        15,
                        7)));
        String foreignToken = other.generateToken("sub", TokenType.ACCESS, 0);

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }
}
