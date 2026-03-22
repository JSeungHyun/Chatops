package com.chatops.support;

import com.chatops.domain.auth.dto.CreateUserRequest;
import com.chatops.domain.auth.dto.LoginRequest;
import com.chatops.domain.auth.dto.UserResponse;
import com.chatops.domain.chat.dto.CreateRoomRequest;
import com.chatops.domain.chat.dto.SendMessageRequest;
import com.chatops.domain.chat.entity.ChatRoom;
import com.chatops.domain.chat.entity.ChatRoomMember;
import com.chatops.domain.chat.entity.RoomType;
import com.chatops.domain.message.entity.Message;
import com.chatops.domain.message.entity.MessageType;
import com.chatops.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public class TestFixture {

    public static User createUser(String id, String email, String nickname) {
        return User.builder()
            .id(id)
            .email(email)
            .nickname(nickname)
            .password("encoded-password")
            .avatar(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public static User createUser() {
        return createUser("user-1", "test@example.com", "testuser");
    }

    public static ChatRoom createChatRoom(String id, String name, RoomType type) {
        return ChatRoom.builder()
            .id(id)
            .name(name)
            .type(type)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    public static ChatRoomMember createChatRoomMember(String id, String userId, String roomId) {
        return ChatRoomMember.builder()
            .id(id)
            .userId(userId)
            .roomId(roomId)
            .joinedAt(LocalDateTime.now())
            .build();
    }

    public static Message createMessage(String id, String content, String userId, String roomId) {
        return Message.builder()
            .id(id)
            .content(content)
            .type(MessageType.TEXT)
            .userId(userId)
            .roomId(roomId)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static CreateUserRequest createUserRequest(String email, String nickname, String password) {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(email);
        request.setNickname(nickname);
        request.setPassword(password);
        return request;
    }

    public static LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    public static CreateRoomRequest createRoomRequest(String name, RoomType type, List<String> memberIds) {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setName(name);
        request.setType(type);
        request.setMemberIds(memberIds);
        return request;
    }

    public static SendMessageRequest sendMessageRequest(String content) {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent(content);
        request.setType(MessageType.TEXT);
        return request;
    }

    public static UserResponse userResponse(User user) {
        return UserResponse.from(user);
    }
}
