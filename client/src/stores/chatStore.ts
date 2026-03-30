import { create } from 'zustand';
import type { ChatRoom, Message } from '../types/chat';

const LAST_ROOM_KEY_PREFIX = 'chatops_last_room:';

export function saveLastRoomId(userId: string, roomId: string) {
  try {
    localStorage.setItem(`${LAST_ROOM_KEY_PREFIX}${userId}`, roomId);
  } catch {
    // localStorage unavailable (e.g. private browsing)
  }
}

export function getLastRoomId(userId: string): string | null {
  try {
    return localStorage.getItem(`${LAST_ROOM_KEY_PREFIX}${userId}`);
  } catch {
    return null;
  }
}

export function clearLastRoomId(userId: string) {
  try {
    localStorage.removeItem(`${LAST_ROOM_KEY_PREFIX}${userId}`);
  } catch {
    // ignore
  }
}

interface ChatState {
  rooms: ChatRoom[];
  currentRoom: ChatRoom | null;
  messages: Message[];
  currentPage: number;
  totalPages: number;
  hasMore: boolean;
  isLoadingMessages: boolean;
  typingUsers: Record<string, string>; // userId -> nickname
  onlineUsers: Record<string, boolean>; // userId -> online
  setRooms: (rooms: ChatRoom[]) => void;
  setCurrentRoom: (room: ChatRoom | null) => void;
  setMessages: (messages: Message[]) => void;
  addMessage: (message: Message) => void;
  prependMessages: (messages: Message[]) => void;
  resetMessages: () => void;
  setCurrentPage: (page: number) => void;
  setTotalPages: (total: number) => void;
  setHasMore: (hasMore: boolean) => void;
  setIsLoadingMessages: (loading: boolean) => void;
  setTypingUser: (userId: string, nickname: string, isTyping: boolean) => void;
  clearTypingUsers: () => void;
  setOnlineUser: (userId: string, online: boolean) => void;
  setOnlineUsers: (statuses: Record<string, boolean>) => void;
  updateRoomWithNewMessage: (roomId: string, message: Message) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  rooms: [],
  currentRoom: null,
  messages: [],
  currentPage: 1,
  totalPages: 0,
  hasMore: false,
  isLoadingMessages: false,
  typingUsers: {},
  onlineUsers: {},
  setRooms: (rooms) => set({ rooms }),
  setCurrentRoom: (room) =>
    set((state) => ({
      currentRoom: room,
      rooms: room
        ? state.rooms.map((r) =>
            r.id === room.id ? { ...r, unreadCount: 0 } : r
          )
        : state.rooms,
    })),
  setMessages: (messages) => set({ messages }),
  addMessage: (message) => set((state) => ({ messages: [...state.messages, message] })),
  prependMessages: (messages) =>
    set((state) => ({ messages: [...messages, ...state.messages] })),
  resetMessages: () =>
    set({ messages: [], currentPage: 1, totalPages: 0, hasMore: false }),
  setCurrentPage: (currentPage) => set({ currentPage }),
  setTotalPages: (totalPages) => set({ totalPages }),
  setHasMore: (hasMore) => set({ hasMore }),
  setIsLoadingMessages: (isLoadingMessages) => set({ isLoadingMessages }),
  setTypingUser: (userId, nickname, isTyping) =>
    set((state) => {
      const next = { ...state.typingUsers };
      if (isTyping) {
        next[userId] = nickname;
      } else {
        delete next[userId];
      }
      return { typingUsers: next };
    }),
  clearTypingUsers: () => set({ typingUsers: {} }),
  setOnlineUser: (userId, online) =>
    set((state) => ({
      onlineUsers: { ...state.onlineUsers, [userId]: online },
    })),
  setOnlineUsers: (statuses) =>
    set((state) => ({
      onlineUsers: { ...state.onlineUsers, ...statuses },
    })),
  updateRoomWithNewMessage: (roomId, message) =>
    set((state) => {
      const isCurrentRoom = state.currentRoom?.id === roomId;
      return {
        rooms: state.rooms.map((r) =>
          r.id === roomId
            ? {
                ...r,
                messages: [message],
                updatedAt: message.createdAt,
                unreadCount: isCurrentRoom
                  ? (r.unreadCount ?? 0)
                  : (r.unreadCount ?? 0) + 1,
              }
            : r,
        ),
      };
    }),
}));
