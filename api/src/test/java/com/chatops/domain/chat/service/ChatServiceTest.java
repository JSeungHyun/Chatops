package com.chatops.domain.chat.service;

import com.chatops.domain.chat.dto.ChatRoomResponse;
import com.chatops.domain.chat.dto.CreateRoomRequest;
import com.chatops.domain.chat.dto.MessageResponse;
import com.chatops.domain.chat.dto.SendMessageRequest;
import com.chatops.domain.chat.entity.ChatRoom;
import com.chatops.domain.chat.entity.ChatRoomMember;
import com.chatops.domain.chat.entity.RoomType;
import com.chatops.domain.chat.repository.ChatRoomMemberRepository;
import com.chatops.domain.chat.repository.ChatRoomRepository;
import com.chatops.domain.message.entity.Message;
import com.chatops.domain.message.entity.MessageType;
import com.chatops.domain.message.repository.MessageRepository;
import com.chatops.domain.user.entity.User;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.global.common.dto.PageResponse;
import com.chatops.support.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("createRoom - 1대1 성공")
    void createRoom_1대1_성공() {
        ChatRoom savedRoom = TestFixture.createChatRoom("room-1", null, RoomType.DIRECT);
        CreateRoomRequest request = TestFixture.createRoomRequest(null, RoomType.DIRECT, List.of("user-2"));

        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(savedRoom);
        given(chatRoomMemberRepository.save(any(ChatRoomMember.class)))
            .willReturn(TestFixture.createChatRoomMember("m-1", "user-1", "room-1"));
        given(chatRoomMemberRepository.saveAll(anyList())).willReturn(List.of());
        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(savedRoom));
        given(chatRoomMemberRepository.findByRoomId("room-1")).willReturn(List.of(
            TestFixture.createChatRoomMember("m-1", "user-1", "room-1"),
            TestFixture.createChatRoomMember("m-2", "user-2", "room-1")
        ));
        given(userRepository.findById("user-1")).willReturn(Optional.of(TestFixture.createUser()));
        given(userRepository.findById("user-2")).willReturn(Optional.of(
            TestFixture.createUser("user-2", "user2@example.com", "user2")));

        ChatRoomResponse result = chatService.createRoom("user-1", request);

        assertThat(result.getId()).isEqualTo("room-1");
        assertThat(result.getType()).isEqualTo(RoomType.DIRECT);
        assertThat(result.getMembers()).hasSize(2);
    }

    @Test
    @DisplayName("createRoom - 그룹 성공")
    void createRoom_그룹_성공() {
        ChatRoom savedRoom = TestFixture.createChatRoom("room-1", "Group Chat", RoomType.GROUP);
        CreateRoomRequest request = TestFixture.createRoomRequest("Group Chat", RoomType.GROUP,
            List.of("user-2", "user-3"));

        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(savedRoom);
        given(chatRoomMemberRepository.save(any(ChatRoomMember.class)))
            .willReturn(TestFixture.createChatRoomMember("m-1", "user-1", "room-1"));
        given(chatRoomMemberRepository.saveAll(anyList())).willReturn(List.of());
        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(savedRoom));
        given(chatRoomMemberRepository.findByRoomId("room-1")).willReturn(List.of(
            TestFixture.createChatRoomMember("m-1", "user-1", "room-1"),
            TestFixture.createChatRoomMember("m-2", "user-2", "room-1"),
            TestFixture.createChatRoomMember("m-3", "user-3", "room-1")
        ));
        given(userRepository.findById("user-1")).willReturn(Optional.of(TestFixture.createUser()));
        given(userRepository.findById("user-2")).willReturn(Optional.of(
            TestFixture.createUser("user-2", "user2@example.com", "user2")));
        given(userRepository.findById("user-3")).willReturn(Optional.of(
            TestFixture.createUser("user-3", "user3@example.com", "user3")));

        ChatRoomResponse result = chatService.createRoom("user-1", request);

        assertThat(result.getName()).isEqualTo("Group Chat");
        assertThat(result.getType()).isEqualTo(RoomType.GROUP);
        assertThat(result.getMembers()).hasSize(3);
    }

    @Test
    @DisplayName("getRoomsByUserId - 방 목록 조회")
    void getRoomsByUserId_방목록조회() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        ChatRoomMember member = TestFixture.createChatRoomMember("m-1", "user-1", "room-1");
        User user = TestFixture.createUser();
        member.setUser(user);

        given(chatRoomRepository.findByMembersUserId("user-1")).willReturn(List.of(room));
        given(chatRoomMemberRepository.findByRoomIdInWithUser(List.of("room-1"))).willReturn(List.of(member));
        given(messageRepository.findLastMessagesByRoomIds(List.of("room-1"))).willReturn(List.of());

        List<ChatRoomResponse> result = chatService.getRoomsByUserId("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("room-1");
    }

    @Test
    @DisplayName("getRoomById - 성공")
    void getRoomById_성공() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("user-1", "room-1")).willReturn(true);
        given(chatRoomMemberRepository.findByRoomId("room-1")).willReturn(List.of(
            TestFixture.createChatRoomMember("m-1", "user-1", "room-1")
        ));
        given(userRepository.findById("user-1")).willReturn(Optional.of(TestFixture.createUser()));

        ChatRoomResponse result = chatService.getRoomById("room-1", "user-1");

        assertThat(result.getId()).isEqualTo("room-1");
    }

    @Test
    @DisplayName("getRoomById - 권한 없음 403")
    void getRoomById_권한없음_403() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("outsider", "room-1")).willReturn(false);

        assertThatThrownBy(() -> chatService.getRoomById("room-1", "outsider"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("sendMessage - 성공")
    void sendMessage_성공() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        Message savedMessage = TestFixture.createMessage("msg-1", "Hello", "user-1", "room-1");
        User sender = TestFixture.createUser();
        SendMessageRequest request = TestFixture.sendMessageRequest("Hello");

        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("user-1", "room-1")).willReturn(true);
        given(messageRepository.save(any(Message.class))).willReturn(savedMessage);
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(room);
        given(userRepository.findById("user-1")).willReturn(Optional.of(sender));

        MessageResponse result = chatService.sendMessage("user-1", "room-1", request);

        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getRoomId()).isEqualTo("room-1");
    }

    @Test
    @DisplayName("sendMessage - 권한 없음 403")
    void sendMessage_권한없음_403() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        SendMessageRequest request = TestFixture.sendMessageRequest("Hello");

        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("outsider", "room-1")).willReturn(false);

        assertThatThrownBy(() -> chatService.sendMessage("outsider", "room-1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("getMessages - 페이지네이션")
    void getMessages_페이지네이션() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        Message msg = TestFixture.createMessage("msg-1", "Hello", "user-1", "room-1");
        User sender = TestFixture.createUser();

        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("user-1", "room-1")).willReturn(true);
        given(messageRepository.findByRoomIdOrderByCreatedAtDesc(eq("room-1"), any(PageRequest.class)))
            .willReturn(new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1));
        given(userRepository.findById("user-1")).willReturn(Optional.of(sender));

        PageResponse<MessageResponse> result = chatService.getMessages("room-1", "user-1", 1, 20);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        assertThat(result.getMeta().getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMessages - 권한 없음 403")
    void getMessages_권한없음_403() {
        ChatRoom room = TestFixture.createChatRoom("room-1", "Room", RoomType.DIRECT);
        given(chatRoomRepository.findById("room-1")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByUserIdAndRoomId("outsider", "room-1")).willReturn(false);

        assertThatThrownBy(() -> chatService.getMessages("room-1", "outsider", 1, 20))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not a member");
    }
}
