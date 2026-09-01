package com.airbus.inventory.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    // 32+ byte secret required for HS256; matches the demo default's format (base64-encoded).
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "unit-test-only-secret-key-not-used-anywhere-else".getBytes());

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3600000L, 604800000L);

    @Test
    void generatedAccessTokenRoundTripsUsernameAndRole() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    void isTokenValidTrueForMatchingUsername() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token, "admin")).isTrue();
    }

    @Test
    void isTokenValidFalseForMismatchedUsername() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token, "someone-else")).isFalse();
    }

    @Test
    void isTokenValidFalseForExpiredToken() {
        JwtUtil expiringSoonUtil = new JwtUtil(SECRET, -1000L, 604800000L);
        String token = expiringSoonUtil.generateToken("admin", "ADMIN");

        assertThat(expiringSoonUtil.isTokenValid(token, "admin")).isFalse();
    }

    @Test
    void refreshTokenIsFlaggedByType() {
        String refreshToken = jwtUtil.generateRefreshToken("admin");
        String accessToken = jwtUtil.generateToken("admin", "ADMIN");

        assertThat(jwtUtil.isRefreshToken(refreshToken)).isTrue();
        assertThat(jwtUtil.isRefreshToken(accessToken)).isFalse();
        assertThat(jwtUtil.extractUsername(refreshToken)).isEqualTo("admin");
    }
}
