package com.chatops.global.redis;

import com.chatops.domain.chat.repository.ChatRoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RedisMessageRelay implements MessageListener {

    private final String serverId = UUID.randomUUID().toString();

    private static final Pattern SAFE_DESTINATION = Pattern.compile(
        "^/topic/(room/[a-f0-9\\-]+(/typing)?|presence)$");

    private static final String USER_MESSAGES_CHANNEL = "user-messages";

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final RedisMessageListenerContainer listenerContainer;
    private final ChatRoomRepository chatRoomRepository;
    private final ObjectMapper objectMapper;

    public RedisMessageRelay(
            SimpMessagingTemplate messagingTemplate,
            RedisService redisService,
            RedisMessageListenerContainer listenerContainer,
            ChatRoomRepository chatRoomRepository,
            ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.redisService = redisService;
        this.listenerContainer = listenerContainer;
        this.chatRoomRepository = chatRoomRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribeAll() {
        try {
            chatRoomRepository.findAll().forEach(room -> subscribeToRoom(room.getId()));
            // Subscribe to presence channel for cross-instance online/offline events
            listenerContainer.addMessageListener(this,
                    new ChannelTopic(RedisKeyConstants.PRESENCE_CHANNEL));
            // Subscribe to user-messages channel for cross-instance personal queue relay
            listenerContainer.addMessageListener(this,
                    new ChannelTopic(USER_MESSAGES_CHANNEL));
            log.info("RedisMessageRelay initialized with serverId={}", serverId);
        } catch (Exception e) {
            log.warn("RedisMessageRelay: failed to subscribe to existing rooms on startup", e);
        }
    }

    public void subscribeToRoom(String roomId) {
        String messageChannel = RedisKeyConstants.chatChannel(roomId);
        String typingChannel = RedisKeyConstants.typingChannel(roomId);
        listenerContainer.addMessageListener(this, new ChannelTopic(messageChannel));
        listenerContainer.addMessageListener(this, new ChannelTopic(typingChannel));
        log.debug("Subscribed to Redis channels: {}, {}", messageChannel, typingChannel);
    }

    /**
     * Publishes presence events (online/offline) to PRESENCE_CHANNEL
     * so other instances can forward them to their local STOMP clients.
     */
    public void publishPresence(Object payload) {
        try {
            Map<String, Object> envelope = Map.of(
                    "serverId", serverId,
                    "destination", "/topic/presence",
                    "payload", payload
            );
            String json = objectMapper.writeValueAsString(envelope);
            redisService.publish(RedisKeyConstants.PRESENCE_CHANNEL, json);
        } catch (Exception e) {
            log.warn("RedisMessageRelay: publishPresence failed", e);
        }
    }

    /**
     * Publishes a message to a user's personal queue via Redis,
     * so other instances can forward it to their local STOMP clients.
     */
    public void publishUserMessage(String userId, Object payload) {
        try {
            Map<String, Object> envelope = Map.of(
                    "serverId", serverId,
                    "userId", userId,
                    "payload", payload
            );
            String json = objectMapper.writeValueAsString(envelope);
            redisService.publish(USER_MESSAGES_CHANNEL, json);
        } catch (Exception e) {
            log.warn("RedisMessageRelay: publishUserMessage failed for userId={}", userId, e);
        }
    }

    public void publishToChannel(String destination, Object payload) {
        try {
            String channel = destinationToChannel(destination);
            if (channel == null) {
                log.warn("RedisMessageRelay: cannot map destination to channel: {}", destination);
                return;
            }
            Map<String, Object> envelope = Map.of(
                    "serverId", serverId,
                    "destination", destination,
                    "payload", payload
            );
            String json = objectMapper.writeValueAsString(envelope);
            redisService.publish(channel, json);
        } catch (Exception e) {
            log.warn("RedisMessageRelay: publishToChannel failed for destination={}", destination, e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<?, ?> envelope = objectMapper.readValue(json, Map.class);

            String incomingServerId = (String) envelope.get("serverId");
            if (serverId.equals(incomingServerId)) {
                // Skip messages published by this instance — already delivered locally
                return;
            }

            // Handle user-messages channel (personal queue relay)
            String userId = (String) envelope.get("userId");
            if (userId != null) {
                Object payload = envelope.get("payload");
                messagingTemplate.convertAndSendToUser(userId, "/queue/messages", payload);
                log.debug("RedisMessageRelay: forwarded user message to userId={}", userId);
                return;
            }

            String destination = (String) envelope.get("destination");
            if (destination == null || !isValidDestination(destination)) {
                log.warn("RedisMessageRelay: rejected invalid destination={}", destination);
                return;
            }
            Object payload = envelope.get("payload");

            messagingTemplate.convertAndSend(destination, payload);
            log.debug("RedisMessageRelay: forwarded message to destination={}", destination);
        } catch (Exception e) {
            log.warn("RedisMessageRelay: onMessage processing failed", e);
        }
    }

    private boolean isValidDestination(String destination) {
        return SAFE_DESTINATION.matcher(destination).matches();
    }

    /**
     * Maps STOMP destination to Redis pub/sub channel name.
     * /topic/room/{roomId}         -> chat:{roomId}
     * /topic/room/{roomId}/typing  -> chat:{roomId}:typing
     */
    private String destinationToChannel(String destination) {
        if (destination == null) return null;
        // e.g. /topic/room/abc123/typing
        if (destination.endsWith("/typing")) {
            String roomId = extractRoomId(destination.substring(0, destination.length() - "/typing".length()));
            return roomId != null ? RedisKeyConstants.typingChannel(roomId) : null;
        }
        // e.g. /topic/room/abc123
        String roomId = extractRoomId(destination);
        return roomId != null ? RedisKeyConstants.chatChannel(roomId) : null;
    }

    private String extractRoomId(String destination) {
        // Expect pattern: /topic/room/{roomId}
        String prefix = "/topic/room/";
        if (destination.startsWith(prefix)) {
            String roomId = destination.substring(prefix.length());
            if (!roomId.isEmpty()) return roomId;
        }
        return null;
    }
}
