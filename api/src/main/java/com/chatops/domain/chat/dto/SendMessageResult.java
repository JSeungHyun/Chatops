package com.chatops.domain.chat.dto;

import java.util.List;

/**
 * Wraps the message response together with the list of member userIds
 * who should receive real-time notifications (excludes the sender).
 * This avoids a redundant member query in the controller layer.
 */
public record SendMessageResult(
    MessageResponse messageResponse,
    List<String> notifyUserIds
) {}
