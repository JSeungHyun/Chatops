package com.chatops.domain.user.controller;

import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.user.dto.UpdateUserRequest;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.service.UserService;
import com.chatops.global.redis.RedisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RedisService redisService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal User user) {
        return userService.findById(user.getId());
    }

    @GetMapping("/search")
    public List<UserResponse> searchUsers(
            @RequestParam(required = false) @Size(min = 2, max = 20) String nickname,
            @RequestParam(required = false) @Size(min = 2, max = 50) String keyword) {
        if (keyword != null) {
            return userService.searchByKeyword(keyword);
        }
        if (nickname != null) {
            return userService.searchByNickname(nickname);
        }
        return List.of();
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable String id) {
        return userService.findById(id);
    }

    @PatchMapping("/{id}")
    public UserResponse updateUser(@AuthenticationPrincipal User user,
                                   @PathVariable String id,
                                   @Valid @RequestBody UpdateUserRequest request) {
        if (!user.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot update another user's profile");
        }
        return userService.update(id, request);
    }

    @GetMapping("/online-status")
    public Map<String, Boolean> getOnlineStatus(@RequestParam @Size(max = 100) List<String> userIds) {
        return redisService.getOnlineStatuses(userIds);
    }
}
