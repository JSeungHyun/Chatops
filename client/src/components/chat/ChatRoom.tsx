import { useEffect, useRef, useCallback } from 'react';
import api from '@/api/axios';
import { getStompClient } from '@/socket/socket';
import { useChatStore, getLastRoomId } from '@/stores/chatStore';
import { useAuthStore } from '@/stores/authStore';
import { useMessages } from '@/hooks/useMessages';
import { useSocket } from '@/hooks/useSocket';
import { Header } from '@/components/layout/Header';
import { MessageBubble } from './MessageBubble';
import { MessageInput } from './MessageInput';
import { TypingIndicator } from './TypingIndicator';
import { DateSeparator } from './DateSeparator';
import { EmptyChat } from './EmptyChat';
import { Spinner } from '@/components/common/Spinner';
import type { Message } from '@/types/message';
import type { TypingEvent } from '@/types/chat';

function isSameDay(a: string, b: string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  return (
    da.getFullYear() === db.getFullYear() &&
    da.getMonth() === db.getMonth() &&
    da.getDate() === db.getDate()
  );
}

export function ChatRoom() {
  const currentRoom = useChatStore((s) => s.currentRoom);
  const addMessage = useChatStore((s) => s.addMessage);
  const typingUsers = useChatStore((s) => s.typingUsers);
  const setTypingUser = useChatStore((s) => s.setTypingUser);
  const clearTypingUsers = useChatStore((s) => s.clearTypingUsers);
  const readReceipts = useChatStore((s) => s.readReceipts);
  const user = useAuthStore((s) => s.user);

  const { messages, isLoading, hasMore, loadMore } = useMessages(
    currentRoom?.id ?? null,
  );

  const handleWsMessage = useCallback(
    (data: unknown) => {
      const msg = data as Message;
      // Skip messages sent by the current user (already added via REST response)
      if (msg.userId === user?.id) return;
      addMessage(msg);
      // Auto-scroll to bottom when receiving new message
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
      }, 50);
    },
    [user?.id, addMessage],
  );

  const handleTyping = useCallback(
    (data: unknown) => {
      const evt = data as TypingEvent;
      if (evt.userId !== user?.id) {
        setTypingUser(evt.userId, evt.nickname, evt.isTyping);
      }
    },
    [user?.id, setTypingUser],
  );

  useSocket({
    roomId: currentRoom?.id,
    onMessage: handleWsMessage,
    onTyping: handleTyping,
  });

  const clearReadReceipts = useChatStore((s) => s.clearReadReceipts);

  // Clear typing users and read receipts when changing rooms
  useEffect(() => {
    clearTypingUsers();
    clearReadReceipts();
  }, [currentRoom?.id, clearTypingUsers, clearReadReceipts]);

  // Notify server when leaving a room (unmount or room change)
  useEffect(() => {
    if (!currentRoom?.id) return;
    return () => {
      const client = getStompClient();
      if (client.connected) {
        client.publish({
          destination: `/app/chat/${currentRoom.id}/leave`,
          body: '{}',
        });
      }
    };
  }, [currentRoom?.id]);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const isInitialLoad = useRef(true);

  // Auto-scroll to bottom on initial load and new messages
  useEffect(() => {
    if (messages.length > 0 && isInitialLoad.current) {
      messagesEndRef.current?.scrollIntoView();
      isInitialLoad.current = false;
    }
  }, [messages]);

  // Reset initial load flag when room changes
  useEffect(() => {
    isInitialLoad.current = true;
  }, [currentRoom?.id]);

  // Scroll to bottom when user sends a new message
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  // Infinite scroll: observe sentinel for loading older messages
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasMore) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore) {
          const container = messagesContainerRef.current;
          const prevHeight = container?.scrollHeight ?? 0;

          loadMore();

          // Preserve scroll position after prepending
          requestAnimationFrame(() => {
            if (container) {
              const newHeight = container.scrollHeight;
              container.scrollTop += newHeight - prevHeight;
            }
          });
        }
      },
      { threshold: 0.1 },
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadMore]);

  const handleSend = async (content: string) => {
    if (!currentRoom || !user) return;

    try {
      const res = await api.post<Message>(
        `/chats/${currentRoom.id}/messages`,
        { content, type: 'TEXT' },
      );
      addMessage(res.data);
      scrollToBottom();
    } catch {
      // Send error — could add toast here
    }
  };

  const handleFileSend = async (fileUrl: string, fileName: string, contentType: string) => {
    if (!currentRoom || !user) return;

    const isImage = contentType.startsWith('image/');
    try {
      const res = await api.post<Message>(
        `/chats/${currentRoom.id}/messages`,
        {
          content: fileName,
          type: isImage ? 'IMAGE' : 'FILE',
          fileUrl,
        },
      );
      addMessage(res.data);
      scrollToBottom();
    } catch {
      // Send error — could add toast here
    }
  };

  const rooms = useChatStore((s) => s.rooms);
  const isRestoring = !currentRoom && rooms.length === 0 && user && !!getLastRoomId(user.id);

  if (!currentRoom) {
    if (isRestoring) {
      return (
        <div className="flex h-full flex-col">
          {/* Skeleton header */}
          <div className="flex items-center gap-3 border-b border-slate-200 px-6 py-4">
            <div className="h-10 w-10 animate-pulse rounded-full bg-slate-200" />
            <div className="flex flex-col gap-1.5">
              <div className="h-4 w-28 animate-pulse rounded bg-slate-200" />
              <div className="h-3 w-16 animate-pulse rounded bg-slate-200" />
            </div>
          </div>
          {/* Skeleton messages */}
          <div className="flex flex-1 flex-col gap-3 px-4 py-6">
            <div className="flex gap-2">
              <div className="h-8 w-8 animate-pulse rounded-full bg-slate-200" />
              <div className="h-10 w-48 animate-pulse rounded-xl bg-slate-200" />
            </div>
            <div className="flex justify-end">
              <div className="h-10 w-36 animate-pulse rounded-xl bg-primary-100" />
            </div>
            <div className="flex gap-2">
              <div className="h-8 w-8 animate-pulse rounded-full bg-slate-200" />
              <div className="h-10 w-56 animate-pulse rounded-xl bg-slate-200" />
            </div>
          </div>
        </div>
      );
    }
    return <EmptyChat />;
  }

  // Derive room display info
  const otherMembers = currentRoom.members.filter(
    (m) => m.userId !== user?.id,
  );
  const roomName =
    currentRoom.type === 'DIRECT'
      ? otherMembers[0]?.user.nickname ?? '채팅'
      : currentRoom.name ?? '그룹 채팅';
  const memberCount = currentRoom.members.length;

  return (
    <div className="flex h-full flex-col">
      <Header
        title={roomName}
        subtitle={`${memberCount}명 참여 중`}
      />

      {/* Messages area */}
      <div
        ref={messagesContainerRef}
        className="scrollbar-thin flex-1 overflow-y-auto px-4 py-2"
      >
        {/* Sentinel for infinite scroll */}
        {hasMore && (
          <div ref={sentinelRef} className="flex justify-center py-4">
            {isLoading && <Spinner size="sm" />}
          </div>
        )}

        {/* Initial loading */}
        {isLoading && messages.length === 0 && (
          <div className="flex h-full items-center justify-center">
            <Spinner size="lg" />
          </div>
        )}

        {/* Message list */}
        {messages.map((msg, idx) => {
          const prevMsg = messages[idx - 1] as Message | undefined;
          const nextMsg = messages[idx + 1] as Message | undefined;

          const showDate =
            !prevMsg || !isSameDay(prevMsg.createdAt, msg.createdAt);
          const isOwn = msg.userId === user?.id;
          const showAvatar =
            !prevMsg ||
            prevMsg.userId !== msg.userId ||
            !isSameDay(prevMsg.createdAt, msg.createdAt);
          const showTimestamp =
            !nextMsg ||
            nextMsg.userId !== msg.userId ||
            !isSameDay(nextMsg.createdAt, msg.createdAt);

          return (
            <div key={msg.id}>
              {showDate && <DateSeparator date={msg.createdAt} />}
              <div className={showAvatar ? 'mt-3' : 'mt-0.5'}>
                <MessageBubble
                  message={msg}
                  isOwn={isOwn}
                  showAvatar={showAvatar}
                  showTimestamp={showTimestamp}
                  readByCount={readReceipts[msg.id]?.length ?? 0}
                  roomType={currentRoom.type}
                />
              </div>
            </div>
          );
        })}

        <TypingIndicator users={Object.values(typingUsers)} />

        <div ref={messagesEndRef} />
      </div>

      <MessageInput onSend={handleSend} onFileSend={handleFileSend} roomId={currentRoom?.id} />
    </div>
  );
}
