package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION = 3600000; // 1 hour

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(TEST_SECRET, TEST_EXPIRATION);
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMIN)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should generate signed JWT containing subject, username, role, and valid dates")
    void shouldGenerateValidTokenWithCorrectClaims() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, "admin")).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractUserId(token)).isEqualTo(testUser.getId().toString());

        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("Should invalidate token when username does not match")
    void shouldBeInvalidForWrongUsername() {
        String token = jwtService.generateToken(testUser);

        assertThat(jwtService.isTokenValid(token, "otheruser")).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void shouldDetectExpiredToken() {
        // Service with negative expiration -> immediately expired
        JwtService expiredJwtService = new JwtServiceImpl(TEST_SECRET, -1000);
        String token = expiredJwtService.generateToken(testUser);

        assertThat(expiredJwtService.isTokenExpired(token)).isTrue();
        assertThat(expiredJwtService.isTokenValid(token, "admin")).isFalse();
    }

    @Test
    @DisplayName("Should invalidate malformed or tampered token")
    void shouldInvalidateTamperedToken() {
        String token = jwtService.generateToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.isTokenValid(tamperedToken, "admin")).isFalse();
    }
}
