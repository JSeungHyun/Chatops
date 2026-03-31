package com.chatops.domain.auth.controller;

import com.chatops.domain.auth.dto.LoginResponse;
import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.auth.service.AuthService;
import com.chatops.global.config.CustomAuthenticationEntryPoint;
import com.chatops.global.config.JwtAuthenticationFilter;
import com.chatops.global.config.JwtTokenProvider;
import com.chatops.global.config.SecurityConfig;
import com.chatops.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("POST /auth/register - 성공 200")
    void register_성공() throws Exception {
        UserResponse response = UserResponse.builder()
            .id("user-1").email("test@example.com").nickname("testuser")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        given(authService.register(any())).willReturn(response);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "nickname", "testuser",
                    "password", "password123"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.nickname").value("testuser"));
    }

    @Test
    @DisplayName("POST /auth/register - 유효성 검증 실패 400")
    void register_유효성검증실패_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "invalid-email",
                    "nickname", "a",
                    "password", "12345"
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - 이메일 중복 409")
    void register_이메일중복_409() throws Exception {
        given(authService.register(any()))
            .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "nickname", "testuser",
                    "password", "password123"
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value(409));
    }

    @Test
    @DisplayName("POST /auth/login - 성공 200")
    void login_성공() throws Exception {
        LoginResponse response = new LoginResponse("jwt-token",
            new LoginResponse.LoginUser("user-1", "test@example.com", "testuser"));
        given(authService.login(anyString(), anyString())).willReturn(response);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password123"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.user.id").value("user-1"));
    }

    @Test
    @DisplayName("POST /auth/login - 인증 실패 401")
    void login_인증실패_401() throws Exception {
        given(authService.login(anyString(), anyString()))
            .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "wrongpassword"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    @DisplayName("GET /auth/check-nickname - 사용 가능")
    void checkNickname_사용가능() throws Exception {
        given(authService.checkNicknameAvailable("newname")).willReturn(true);

        mockMvc.perform(get("/auth/check-nickname").param("nickname", "newname"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("GET /auth/check-nickname - 이미 존재")
    void checkNickname_이미존재() throws Exception {
        given(authService.checkNicknameAvailable("taken")).willReturn(false);

        mockMvc.perform(get("/auth/check-nickname").param("nickname", "taken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }
}
