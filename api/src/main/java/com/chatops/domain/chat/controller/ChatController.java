package com.chatops.domain.chat.controller;

import com.chatops.domain.chat.dto.ChatRoomResponse;
import com.chatops.domain.chat.dto.MessageResponse;
import com.chatops.domain.chat.dto.CreateRoomRequest;
import com.chatops.domain.chat.dto.SendMessageRequest;
import com.chatops.domain.chat.entity.ChatRoomMember;
import com.chatops.domain.chat.repository.ChatRoomMemberRepository;
import com.chatops.domain.chat.service.ChatService;
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
    private final ChatRoomMemberRepository chatRoomMemberRepository;
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

    @PostMapping("/{id}/messages")
    public MessageResponse sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse response = chatService.sendMessage(user.getId(), id, request);
        messagingTemplate.convertAndSend("/topic/room/" + id, response);
        redisMessageRelay.publishToChannel("/topic/room/" + id, response);

        // Send to each member's personal queue for cross-room notifications
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(id);
        for (ChatRoomMember member : members) {
            if (!member.getUserId().equals(user.getId())) {
                messagingTemplate.convertAndSendToUser(
                    member.getUserId(), "/queue/messages", response);
                redisMessageRelay.publishUserMessage(
                    member.getUserId(), response);
            }
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
