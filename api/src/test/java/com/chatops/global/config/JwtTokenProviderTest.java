package com.chatops.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret",
            "this-is-a-test-secret-key-that-is-at-least-32-bytes-long-for-hmac");
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", 3600000L);
    }

    @Test
    @DisplayName("generateToken - 유효한 토큰 생성")
    void generateToken_유효한토큰생성() {
        String token = jwtTokenProvider.generateToken("user-1", "test@example.com");

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getUserIdFromToken - 정상 추출")
    void getUserIdFromToken_정상추출() {
        String token = jwtTokenProvider.generateToken("user-1", "test@example.com");

        String userId = jwtTokenProvider.getUserIdFromToken(token);

        assertThat(userId).isEqualTo("user-1");
    }

    @Test
    @DisplayName("validateToken - 유효한 토큰 true")
    void validateToken_유효한토큰_true() {
        String token = jwtTokenProvider.generateToken("user-1", "test@example.com");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken - 만료된 토큰 false")
    void validateToken_만료된토큰_false() {
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", -1000L);
        String token = jwtTokenProvider.generateToken("user-1", "test@example.com");

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken - 잘못된 서명 false")
    void validateToken_잘못된서명_false() {
        String token = jwtTokenProvider.generateToken("user-1", "test@example.com");

        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "secret",
            "different-secret-key-that-is-also-at-least-32-bytes-long-here");
        ReflectionTestUtils.setField(otherProvider, "expiration", 3600000L);

        assertThat(otherProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken - 빈 문자열 false")
    void validateToken_빈문자열_false() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }
}
