package com.chatops.domain.user.service;

import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.user.dto.UpdateUserRequest;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.support.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("findById - 성공")
    void findById_성공() {
        User user = TestFixture.createUser();
        given(userRepository.findById("user-1")).willReturn(Optional.of(user));

        UserResponse result = userService.findById("user-1");

        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("findById - 존재하지 않음 404")
    void findById_존재하지않음_404() {
        given(userRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById("nonexistent"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("searchByNickname - 결과 있음")
    void searchByNickname_결과있음() {
        User user = TestFixture.createUser();
        given(userRepository.findByNicknameContainingIgnoreCase(eq("test"), any(PageRequest.class)))
            .willReturn(List.of(user));

        List<UserResponse> result = userService.searchByNickname("test");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNickname()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("searchByNickname - 결과 없음 빈 리스트")
    void searchByNickname_결과없음_빈리스트() {
        given(userRepository.findByNicknameContainingIgnoreCase(eq("nobody"), any(PageRequest.class)))
            .willReturn(List.of());

        List<UserResponse> result = userService.searchByNickname("nobody");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("update - 닉네임 변경 성공")
    void update_닉네임변경_성공() {
        User user = TestFixture.createUser();
        given(userRepository.findById("user-1")).willReturn(Optional.of(user));
        given(userRepository.findByNickname("newnick")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(user);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("newnick");

        UserResponse result = userService.update("user-1", request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("update - 닉네임 중복 409")
    void update_닉네임중복_409() {
        User user = TestFixture.createUser();
        User existingUser = TestFixture.createUser("user-2", "other@example.com", "newnick");
        given(userRepository.findById("user-1")).willReturn(Optional.of(user));
        given(userRepository.findByNickname("newnick")).willReturn(Optional.of(existingUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("newnick");

        assertThatThrownBy(() -> userService.update("user-1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Nickname already exists");
    }
}
