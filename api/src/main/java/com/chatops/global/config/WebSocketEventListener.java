package com.chatops.global.config;

import com.chatops.global.redis.RedisMessageRelay;
import com.chatops.global.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisService redisService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessageRelay redisMessageRelay;

    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            if (userId != null) {
                redisService.setOnline(userId);
                Map<String, Object> payload = Map.of("userId", userId, "online", true);
                messagingTemplate.convertAndSend("/topic/presence", payload);
                redisMessageRelay.publishPresence(payload);
                log.debug("User connected: userId={}", userId);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String userId = (String) sessionAttributes.get("userId");
            if (userId != null) {
                redisService.cleanupViewerOnDisconnect(userId);
                redisService.setOffline(userId);
                Map<String, Object> payload = Map.of("userId", userId, "online", false);
                messagingTemplate.convertAndSend("/topic/presence", payload);
                redisMessageRelay.publishPresence(payload);
                log.debug("User disconnected: userId={}", userId);
            }
        }
    }
}
