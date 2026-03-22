package com.chatops.domain.chat.controller;

import com.chatops.domain.chat.dto.ChatRoomResponse;
import com.chatops.domain.chat.dto.MessageResponse;
import com.chatops.domain.chat.entity.RoomType;
import com.chatops.domain.chat.service.ChatService;
import com.chatops.domain.message.entity.MessageType;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.global.common.dto.PageResponse;
import com.chatops.global.config.CustomAuthenticationEntryPoint;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private String setupAuth() {
        String token = "test-jwt-token";
        User user = TestFixture.createUser();
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken(token)).willReturn("user-1");
        given(userRepository.findById("user-1")).willReturn(Optional.of(user));
        return token;
    }

    private ChatRoomResponse sampleRoomResponse() {
        return ChatRoomResponse.builder()
            .id("room-1").name("Test Room").type(RoomType.DIRECT)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .members(List.of()).messages(List.of())
            .build();
    }

    private MessageResponse sampleMessageResponse() {
        return MessageResponse.builder()
            .id("msg-1").content("Hello").type(MessageType.TEXT)
            .userId("user-1").roomId("room-1").createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /chats - 채팅방 생성 200")
    void createRoom_성공() throws Exception {
        String token = setupAuth();
        given(chatService.createRoom(eq("user-1"), any())).willReturn(sampleRoomResponse());

        mockMvc.perform(post("/chats")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Test Room",
                    "type", "DIRECT",
                    "memberIds", List.of("user-2")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("room-1"));
    }

    @Test
    @DisplayName("POST /chats - 유효성 검증 실패 400 (memberIds 비어있음)")
    void createRoom_유효성검증실패_400() throws Exception {
        String token = setupAuth();

        mockMvc.perform(post("/chats")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "type", "DIRECT",
                    "memberIds", List.of()
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /chats - 채팅방 목록 200")
    void getRooms_성공() throws Exception {
        String token = setupAuth();
        given(chatService.getRoomsByUserId("user-1")).willReturn(List.of(sampleRoomResponse()));

        mockMvc.perform(get("/chats")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("room-1"));
    }

    @Test
    @DisplayName("GET /chats/{id} - 채팅방 조회 200")
    void getRoom_성공() throws Exception {
        String token = setupAuth();
        given(chatService.getRoomById("room-1", "user-1")).willReturn(sampleRoomResponse());

        mockMvc.perform(get("/chats/room-1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("room-1"));
    }

    @Test
    @DisplayName("POST /chats/{id}/messages - 메시지 전송 200")
    void sendMessage_성공() throws Exception {
        String token = setupAuth();
        given(chatService.sendMessage(eq("user-1"), eq("room-1"), any())).willReturn(sampleMessageResponse());

        mockMvc.perform(post("/chats/room-1/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "Hello"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Hello"));
    }

    @Test
    @DisplayName("GET /chats/{id}/messages - 메시지 목록 200")
    void getMessages_성공() throws Exception {
        String token = setupAuth();
        PageResponse<MessageResponse> pageResponse = new PageResponse<>(
            List.of(sampleMessageResponse()),
            new PageResponse.PageMeta(1, 1, 20, 1)
        );
        given(chatService.getMessages(eq("room-1"), eq("user-1"), anyInt(), anyInt()))
            .willReturn(pageResponse);

        mockMvc.perform(get("/chats/room-1/messages")
                .header("Authorization", "Bearer " + token)
                .param("page", "1")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].content").value("Hello"))
            .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("미인증 요청 - 401 반환")
    void 미인증요청_401() throws Exception {
        mockMvc.perform(get("/chats"))
            .andExpect(status().isUnauthorized());
    }
}
