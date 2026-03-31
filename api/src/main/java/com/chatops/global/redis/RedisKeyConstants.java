package com.chatops.global.redis;

public final class RedisKeyConstants {
    private RedisKeyConstants() {}

    public static String userStatus(String userId) { return "user:" + userId + ":status"; }
    public static String unreadCount(String userId, String roomId) { return "unread:" + userId + ":" + roomId; }
    public static String roomMessages(String roomId) { return "room:" + roomId + ":messages"; }
    public static String roomTyping(String roomId) { return "room:" + roomId + ":typing"; }
    public static String roomViewers(String roomId) { return "room:" + roomId + ":viewers"; }
    public static String userViewing(String userId) { return "user:" + userId + ":viewing"; }
    public static String chatChannel(String roomId) { return "chat:" + roomId; }
    public static String typingChannel(String roomId) { return "chat:" + roomId + ":typing"; }
    public static final String PRESENCE_CHANNEL = "presence";

    public static final int USER_STATUS_TTL = 300;
    public static final int ROOM_MESSAGES_TTL = 3600;
    public static final int ROOM_TYPING_TTL = 5;
    public static final int ROOM_VIEWERS_TTL = 600;
}
