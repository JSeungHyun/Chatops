package com.chatops.domain.chat.controller;

import com.chatops.domain.chat.dto.ChatRoomResponse;
import com.chatops.domain.chat.dto.MessageResponse;
import com.chatops.domain.chat.dto.CreateRoomRequest;
import com.chatops.domain.chat.dto.SendMessageRequest;
import com.chatops.domain.chat.dto.SendMessageResult;
import com.chatops.domain.chat.service.ChatService;
import com.chatops.global.common.annotation.RateLimit;
import com.chatops.global.common.dto.PageResponse;
import com.chatops.global.redis.RedisMessageRelay;
import com.chatops.domain.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for chat room operations.
 * Message sending via REST also broadcasts to STOMP subscribers
 * so that other connected clients receive the message in real-time.
 */
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessageRelay redisMessageRelay;

    @PostMapping
    public ChatRoomResponse createRoom(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateRoomRequest request) {
        ChatRoomResponse room = chatService.createRoom(user.getId(), request);
        redisMessageRelay.subscribeToRoom(room.getId());
        return room;
    }

    @GetMapping
    public List<ChatRoomResponse> getRooms(@AuthenticationPrincipal User user) {
        return chatService.getRoomsByUserId(user.getId());
    }

    @GetMapping("/{id}")
    public ChatRoomResponse getRoom(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        return chatService.getRoomById(id, user.getId());
    }

    @RateLimit(maxRequests = 30, windowSeconds = 60, byUser = true)
    @PostMapping("/{id}/messages")
    public MessageResponse sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request) {
        SendMessageResult result = chatService.sendMessage(user.getId(), id, request);
        MessageResponse response = result.messageResponse();
        messagingTemplate.convertAndSend("/topic/room/" + id, response);
        redisMessageRelay.publishToChannel("/topic/room/" + id, response);

        // Send to each member's personal queue (reuses member list from service layer)
        for (String memberId : result.notifyUserIds()) {
            messagingTemplate.convertAndSendToUser(memberId, "/queue/messages", response);
            redisMessageRelay.publishUserMessage(memberId, response);
        }
        return response;
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageResponse> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return chatService.getMessages(id, user.getId(), page, limit);
    }
}
