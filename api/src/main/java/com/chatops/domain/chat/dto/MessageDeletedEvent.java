package com.chatops.domain.chat.dto;

import lombok.Getter;

@Getter
public class MessageDeletedEvent {
    private final String id;
    private final String roomId;
    private final boolean deleted = true;

    public MessageDeletedEvent(String id, String roomId) {
        this.id = id;
        this.roomId = roomId;
    }
}
