package com.chatops.domain.user.controller;

import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.domain.user.service.UserService;
import com.chatops.global.config.CustomAuthenticationEntryPoint;
import com.chatops.global.redis.RedisService;
import com.chatops.global.config.JwtAuthenticationFilter;
import com.chatops.global.config.JwtTokenProvider;
import com.chatops.global.config.SecurityConfig;
import com.chatops.support.TestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RedisService redisService;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private String setupAuth() {
        String token = "test-jwt-token";
        User user = TestFixture.createUser();
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken(token)).willReturn("user-1");
        given(userRepository.findById("user-1")).willReturn(Optional.of(user));
        return token;
    }

    @Test
    @DisplayName("GET /users/me - 인증된 유저 정보 반환 200")
    void getMe_성공() throws Exception {
        String token = setupAuth();
        UserResponse response = UserResponse.builder()
            .id("user-1").email("test@example.com").nickname("testuser")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        given(userService.findById("user-1")).willReturn(response);

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("user-1"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /users/me - 미인증 401")
    void getMe_미인증_401() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/search - 검색 결과 200")
    void searchUsers_성공() throws Exception {
        String token = setupAuth();
        UserResponse response = UserResponse.builder()
            .id("user-1").email("test@example.com").nickname("testuser")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        given(userService.searchByNickname("test")).willReturn(List.of(response));

        mockMvc.perform(get("/users/search")
                .param("nickname", "test")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nickname").value("testuser"));
    }

    @Test
    @DisplayName("GET /users/{id} - 유저 조회 200")
    void getUser_성공() throws Exception {
        String token = setupAuth();
        UserResponse response = UserResponse.builder()
            .id("user-2").email("other@example.com").nickname("other")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        given(userService.findById("user-2")).willReturn(response);

        mockMvc.perform(get("/users/user-2")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("user-2"));
    }

    @Test
    @DisplayName("PATCH /users/{id} - 닉네임 업데이트 200")
    void updateUser_성공() throws Exception {
        String token = setupAuth();
        UserResponse response = UserResponse.builder()
            .id("user-1").email("test@example.com").nickname("newnick")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        given(userService.update(eq("user-1"), any())).willReturn(response);

        mockMvc.perform(patch("/users/user-1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nickname", "newnick"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("newnick"));
    }

    @Test
    @DisplayName("PATCH /users/{id} - 유효성 검증 실패 400")
    void updateUser_유효성검증실패_400() throws Exception {
        String token = setupAuth();

        mockMvc.perform(patch("/users/user-1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nickname", "a"))))
            .andExpect(status().isBadRequest());
    }
}
