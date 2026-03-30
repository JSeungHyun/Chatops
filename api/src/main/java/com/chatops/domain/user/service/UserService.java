package com.chatops.domain.user.service;

import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.user.dto.UpdateUserRequest;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse findById(String id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    public List<UserResponse> searchByNickname(String nickname) {
        return userRepository.findByNicknameContainingIgnoreCase(nickname, PageRequest.of(0, 20))
            .stream().map(UserResponse::from).toList();
    }

    public List<UserResponse> searchByKeyword(String keyword) {
        return userRepository.searchByKeyword(keyword, PageRequest.of(0, 20))
            .stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getNickname() != null) {
            userRepository.findByNickname(request.getNickname())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Nickname already exists");
                });
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        try {
            User saved = userRepository.save(user);
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nickname already exists");
        }
    }
}
