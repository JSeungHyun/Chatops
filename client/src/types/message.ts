import type { User } from './user';

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE';

export interface Message {
  id: string;
  content: string;
  type: MessageType;
  fileUrl?: string;
  thumbnailUrl?: string;
  width?: number;
  height?: number;
  mimeType?: string;
  userId: string;
  roomId: string;
  createdAt: string;
  user: Pick<User, 'id' | 'email' | 'nickname' | 'avatar'>;
}

export interface MessageUpdatedEvent {
  id: string;
  roomId: string;
  thumbnailUrl?: string;
  width?: number;
  height?: number;
  mimeType?: string;
  metadataReady: true;
}

export interface ReadReceiptUpdate {
  roomId: string;
  userId: string;
  messageIds: string[];
}
