package com.chatops.domain.auth.controller;

import com.chatops.domain.auth.dto.CreateUserRequest;
import com.chatops.domain.auth.dto.LoginRequest;
import com.chatops.domain.auth.dto.LoginResponse;
import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.auth.service.AuthService;
import com.chatops.global.common.annotation.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody CreateUserRequest request) {
        return authService.register(request);
    }

    @RateLimit(maxRequests = 10, windowSeconds = 60)
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @GetMapping("/check-nickname")
    public Map<String, Boolean> checkNickname(@RequestParam @Size(min = 2, max = 20) String nickname) {
        return Map.of("available", authService.checkNicknameAvailable(nickname));
    }
}
