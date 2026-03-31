package com.chatops.global.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    // ===== Online Status =====

    public void setOnline(String userId) {
        try {
            redisTemplate.opsForValue().set(
                RedisKeyConstants.userStatus(userId),
                "online",
                Duration.ofSeconds(RedisKeyConstants.USER_STATUS_TTL)
            );
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: setOnline failed for userId={}", userId);
        }
    }

    public void setOffline(String userId) {
        try {
            redisTemplate.delete(RedisKeyConstants.userStatus(userId));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: setOffline failed for userId={}", userId);
        }
    }

    public boolean isOnline(String userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyConstants.userStatus(userId)));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: isOnline check failed for userId={}", userId);
            return false;
        }
    }

    public void refreshHeartbeat(String userId) {
        setOnline(userId);
    }

    public Map<String, Boolean> getOnlineStatuses(List<String> userIds) {
        try {
            List<String> keys = userIds.stream()
                .map(RedisKeyConstants::userStatus)
                .toList();
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) return Collections.emptyMap();

            Map<String, Boolean> result = new java.util.HashMap<>();
            for (int i = 0; i < userIds.size(); i++) {
                result.put(userIds.get(i), values.get(i) != null);
            }
            return result;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getOnlineStatuses failed");
            return Collections.emptyMap();
        }
    }

    // ===== Unread Count =====

    public void incrementUnread(String userId, String roomId) {
        try {
            redisTemplate.opsForValue().increment(RedisKeyConstants.unreadCount(userId, roomId));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: incrementUnread failed for userId={}, roomId={}", userId, roomId);
        }
    }

    public void resetUnread(String userId, String roomId) {
        try {
            redisTemplate.delete(RedisKeyConstants.unreadCount(userId, roomId));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: resetUnread failed for userId={}, roomId={}", userId, roomId);
        }
    }

    public int getUnreadCount(String userId, String roomId) {
        try {
            String value = redisTemplate.opsForValue().get(RedisKeyConstants.unreadCount(userId, roomId));
            return value != null ? Integer.parseInt(value) : 0;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getUnreadCount failed for userId={}, roomId={}", userId, roomId);
            return 0;
        }
    }

    public Map<String, Integer> getUnreadCounts(String userId, List<String> roomIds) {
        try {
            List<String> keys = roomIds.stream()
                .map(roomId -> RedisKeyConstants.unreadCount(userId, roomId))
                .toList();
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) return Collections.emptyMap();

            Map<String, Integer> result = new java.util.HashMap<>();
            for (int i = 0; i < roomIds.size(); i++) {
                String val = values.get(i);
                result.put(roomIds.get(i), val != null ? Integer.parseInt(val) : 0);
            }
            return result;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getUnreadCounts failed for userId={}", userId);
            return Collections.emptyMap();
        }
    }

    // ===== Message Cache =====

    public void cacheMessage(String roomId, String messageJson) {
        try {
            String key = RedisKeyConstants.roomMessages(roomId);
            redisTemplate.opsForList().leftPush(key, messageJson);
            redisTemplate.opsForList().trim(key, 0, 49);
            redisTemplate.expire(key, Duration.ofSeconds(RedisKeyConstants.ROOM_MESSAGES_TTL));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: cacheMessage failed for roomId={}", roomId);
        }
    }

    public List<String> getCachedMessages(String roomId) {
        try {
            String key = RedisKeyConstants.roomMessages(roomId);
            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
            return messages != null ? messages : Collections.emptyList();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getCachedMessages failed for roomId={}", roomId);
            return Collections.emptyList();
        }
    }

    // ===== Typing =====

    public void setTyping(String roomId, String userId) {
        try {
            String key = RedisKeyConstants.roomTyping(roomId);
            redisTemplate.opsForSet().add(key, userId);
            redisTemplate.expire(key, Duration.ofSeconds(RedisKeyConstants.ROOM_TYPING_TTL));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: setTyping failed for roomId={}, userId={}", roomId, userId);
        }
    }

    public void removeTyping(String roomId, String userId) {
        try {
            redisTemplate.opsForSet().remove(RedisKeyConstants.roomTyping(roomId), userId);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: removeTyping failed for roomId={}, userId={}", roomId, userId);
        }
    }

    public Set<String> getTypingUsers(String roomId) {
        try {
            Set<String> members = redisTemplate.opsForSet().members(RedisKeyConstants.roomTyping(roomId));
            return members != null ? members : Collections.emptySet();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getTypingUsers failed for roomId={}", roomId);
            return Collections.emptySet();
        }
    }

    // ===== Active Viewers =====

    public void addViewer(String roomId, String userId) {
        try {
            String key = RedisKeyConstants.roomViewers(roomId);
            redisTemplate.opsForSet().add(key, userId);
            redisTemplate.expire(key, Duration.ofSeconds(RedisKeyConstants.ROOM_VIEWERS_TTL));
            // Track which room this user is viewing (for disconnect cleanup)
            redisTemplate.opsForValue().set(
                RedisKeyConstants.userViewing(userId), roomId,
                Duration.ofSeconds(RedisKeyConstants.ROOM_VIEWERS_TTL));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: addViewer failed for roomId={}, userId={}", roomId, userId);
        }
    }

    public void removeViewer(String roomId, String userId) {
        try {
            redisTemplate.opsForSet().remove(RedisKeyConstants.roomViewers(roomId), userId);
            redisTemplate.delete(RedisKeyConstants.userViewing(userId));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: removeViewer failed for roomId={}, userId={}", roomId, userId);
        }
    }

    /** Atomic Lua script: GET viewing room → SREM from viewers → DEL viewing key */
    private static final DefaultRedisScript<String> CLEANUP_VIEWER_SCRIPT;
    static {
        CLEANUP_VIEWER_SCRIPT = new DefaultRedisScript<>();
        CLEANUP_VIEWER_SCRIPT.setScriptText(
            "local roomId = redis.call('GET', KEYS[1]); " +
            "if roomId then " +
            "  redis.call('SREM', 'room:' .. roomId .. ':viewers', ARGV[1]); " +
            "  redis.call('DEL', KEYS[1]); " +
            "end; " +
            "return roomId"
        );
        CLEANUP_VIEWER_SCRIPT.setResultType(String.class);
    }

    /**
     * Cleans up viewer state on WebSocket disconnect.
     * Uses a Lua script to atomically read the viewing room, remove from viewers, and delete the key.
     */
    public void cleanupViewerOnDisconnect(String userId) {
        try {
            String viewingKey = RedisKeyConstants.userViewing(userId);
            String roomId = redisTemplate.execute(
                CLEANUP_VIEWER_SCRIPT,
                java.util.List.of(viewingKey),
                userId
            );
            if (roomId != null) {
                log.debug("Cleaned up viewer on disconnect: userId={}, roomId={}", userId, roomId);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: cleanupViewerOnDisconnect failed for userId={}", userId);
        }
    }

    public Set<String> getViewers(String roomId) {
        try {
            Set<String> viewers = redisTemplate.opsForSet().members(RedisKeyConstants.roomViewers(roomId));
            return viewers != null ? viewers : Collections.emptySet();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: getViewers failed for roomId={}", roomId);
            return Collections.emptySet();
        }
    }

    // ===== Pub/Sub =====

    public void publish(String channel, String message) {
        try {
            redisTemplate.convertAndSend(channel, message);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable: publish failed to channel={}", channel);
        }
    }
}
