package com.chatops.domain.auth.service;

import com.chatops.domain.auth.dto.CreateUserRequest;
import com.chatops.domain.auth.dto.LoginResponse;
import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.global.config.JwtTokenProvider;
import com.chatops.support.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("register - 성공")
    void register_성공() {
        CreateUserRequest request = TestFixture.createUserRequest("test@example.com", "testuser", "password123");
        User savedUser = TestFixture.createUser();

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.empty());
        given(userRepository.findByNickname("testuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        UserResponse result = authService.register(request);

        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getNickname()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("register - 이메일 중복 409")
    void register_이메일중복_409() {
        CreateUserRequest request = TestFixture.createUserRequest("test@example.com", "testuser", "password123");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(TestFixture.createUser()));

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("register - 닉네임 중복 409")
    void register_닉네임중복_409() {
        CreateUserRequest request = TestFixture.createUserRequest("new@example.com", "testuser", "password123");
        given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
        given(userRepository.findByNickname("testuser")).willReturn(Optional.of(TestFixture.createUser()));

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Nickname already exists");
    }

    @Test
    @DisplayName("login - 성공")
    void login_성공() {
        User user = TestFixture.createUser();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
        given(jwtTokenProvider.generateToken("user-1", "test@example.com")).willReturn("jwt-token");

        LoginResponse result = authService.login("test@example.com", "password123");

        assertThat(result.getAccessToken()).isEqualTo("jwt-token");
        assertThat(result.getUser().getId()).isEqualTo("user-1");
        assertThat(result.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("login - 이메일 없음 401")
    void login_이메일없음_401() {
        given(userRepository.findByEmail("wrong@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("wrong@example.com", "password123"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login - 비밀번호 불일치 401")
    void login_비밀번호불일치_401() {
        User user = TestFixture.createUser();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpass", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.login("test@example.com", "wrongpass"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("checkNicknameAvailable - 사용 가능 true")
    void checkNicknameAvailable_사용가능_true() {
        given(userRepository.findByNickname("newname")).willReturn(Optional.empty());

        assertThat(authService.checkNicknameAvailable("newname")).isTrue();
    }

    @Test
    @DisplayName("checkNicknameAvailable - 이미 존재 false")
    void checkNicknameAvailable_이미존재_false() {
        given(userRepository.findByNickname("testuser")).willReturn(Optional.of(TestFixture.createUser()));

        assertThat(authService.checkNicknameAvailable("testuser")).isFalse();
    }
}
