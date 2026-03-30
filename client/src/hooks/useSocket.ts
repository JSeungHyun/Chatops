import { useEffect, useRef, useCallback } from 'react';
import { StompSubscription } from '@stomp/stompjs';
import toast from 'react-hot-toast';
import {
  connectStomp,
  disconnectStomp,
  getStompClient,
  subscribe as stompSubscribe,
  subscribeToRoom,
} from '../socket/socket';
import { useAuthStore } from '../stores/authStore';
import { useChatStore } from '../stores/chatStore';

interface UseSocketOptions {
  roomId?: string;
  onMessage?: (data: unknown) => void;
  onTyping?: (data: unknown) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

export function useSocket({
  roomId,
  onMessage,
  onTyping,
  onConnected,
  onDisconnected,
}: UseSocketOptions = {}) {
  const token = useAuthStore((s) => s.token);
  const subscriptionRef = useRef<StompSubscription | null>(null);
  const typingSubRef = useRef<StompSubscription | null>(null);
  const errorSubRef = useRef<StompSubscription | null>(null);
  const presenceSubRef = useRef<StompSubscription | null>(null);
  const userMsgSubRef = useRef<StompSubscription | null>(null);
  const connectedRef = useRef(false);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Stabilize callbacks with refs to prevent unnecessary reconnections
  const onMessageRef = useRef(onMessage);
  const onTypingRef = useRef(onTyping);
  const onConnectedRef = useRef(onConnected);
  const onDisconnectedRef = useRef(onDisconnected);
  const roomIdRef = useRef(roomId);

  useEffect(() => { onMessageRef.current = onMessage; }, [onMessage]);
  useEffect(() => { onTypingRef.current = onTyping; }, [onTyping]);
  useEffect(() => { onConnectedRef.current = onConnected; }, [onConnected]);
  useEffect(() => { onDisconnectedRef.current = onDisconnected; }, [onDisconnected]);
  useEffect(() => { roomIdRef.current = roomId; }, [roomId]);

  const doSubscribeRoom = useCallback(() => {
    const rid = roomIdRef.current;
    if (!rid) return;
    subscriptionRef.current?.unsubscribe();
    typingSubRef.current?.unsubscribe();
    if (onMessageRef.current) {
      subscriptionRef.current = subscribeToRoom(rid, onMessageRef.current);
    }
    if (onTypingRef.current) {
      const client = getStompClient();
      if (client.connected) {
        const handler = onTypingRef.current;
        typingSubRef.current = client.subscribe(
          `/topic/room/${rid}/typing`,
          (frame) => {
            try {
              const data = JSON.parse(frame.body);
              handler(data);
            } catch {
              // ignore malformed frames
            }
          },
        );
      }
    }
  }, []);

  // Main connection effect — only re-runs when token changes
  useEffect(() => {
    if (!token) return;

    const client = connectStomp(token);

    client.onConnect = () => {
      connectedRef.current = true;
      onConnectedRef.current?.();
      doSubscribeRoom();
      // Subscribe to server-side personal error queue
      errorSubRef.current = stompSubscribe('/user/queue/errors', (frame) => {
        try {
          const err = JSON.parse(frame.body) as { message?: string };
          toast.error(err.message ?? '서버 오류가 발생했습니다.');
        } catch {
          toast.error('서버 오류가 발생했습니다.');
        }
      });
      // Start heartbeat interval (every 3 minutes)
      heartbeatRef.current = setInterval(() => {
        const c = getStompClient();
        if (c.connected) {
          c.publish({ destination: '/app/heartbeat', body: '{}' });
        }
      }, 3 * 60 * 1000);

      // Subscribe to personal message queue for cross-room notifications
      userMsgSubRef.current = stompSubscribe('/user/queue/messages', (frame) => {
        try {
          const msg = JSON.parse(frame.body) as { roomId: string; userId: string; content: string; createdAt: string; id: string; user: unknown };
          const store = useChatStore.getState();
          // Only update room list — current room messages are handled by room subscription
          if (msg.roomId !== store.currentRoom?.id) {
            store.updateRoomWithNewMessage(msg.roomId, msg as never);
          }
        } catch {
          // ignore malformed frames
        }
      });

      // Subscribe to presence updates
      presenceSubRef.current = stompSubscribe('/topic/presence', (frame) => {
        try {
          const data = JSON.parse(frame.body) as { userId: string; online: boolean };
          useChatStore.getState().setOnlineUser(data.userId, data.online);
        } catch {
          // ignore malformed frames
        }
      });
    };

    client.onDisconnect = () => {
      connectedRef.current = false;
      onDisconnectedRef.current?.();
    };

    client.onStompError = (frame) => {
      const msg = frame.headers['message'] ?? 'WebSocket connection error';
      toast.error(msg);
    };

    // If already connected (e.g. client re-used), subscribe immediately
    if (client.connected) {
      connectedRef.current = true;
      doSubscribeRoom();
    }

    return () => {
      subscriptionRef.current?.unsubscribe();
      subscriptionRef.current = null;
      typingSubRef.current?.unsubscribe();
      typingSubRef.current = null;
      errorSubRef.current?.unsubscribe();
      errorSubRef.current = null;
      presenceSubRef.current?.unsubscribe();
      presenceSubRef.current = null;
      userMsgSubRef.current?.unsubscribe();
      userMsgSubRef.current = null;
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
        heartbeatRef.current = null;
      }
      connectedRef.current = false;
      disconnectStomp();
    };
  }, [token, doSubscribeRoom]);

  // Re-subscribe when roomId changes and client is already connected
  useEffect(() => {
    if (!roomId) return;
    if (connectedRef.current) {
      doSubscribeRoom();
    }
  }, [roomId, doSubscribeRoom]);

  const disconnect = useCallback(() => {
    subscriptionRef.current?.unsubscribe();
    subscriptionRef.current = null;
    typingSubRef.current?.unsubscribe();
    typingSubRef.current = null;
    errorSubRef.current?.unsubscribe();
    errorSubRef.current = null;
    presenceSubRef.current?.unsubscribe();
    presenceSubRef.current = null;
    userMsgSubRef.current?.unsubscribe();
    userMsgSubRef.current = null;
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
    }
    disconnectStomp();
  }, []);

  return { disconnect };
}

export default useSocket;
