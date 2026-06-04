import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MessageBubble } from './MessageBubble';
import type { Message } from '@/types/message';

// 기본 메시지 팩토리
function createMessage(overrides: Partial<Message> = {}): Message {
  return {
    id: 'msg-1',
    content: 'Hello world',
    type: 'TEXT',
    userId: 'user-1',
    roomId: 'room-1',
    createdAt: '2025-05-05T10:00:00Z',
    user: { id: 'user-1', email: 'test@test.com', nickname: 'TestUser', avatar: null },
    ...overrides,
  };
}

describe('MessageBubble - 파일/이미지 분기 렌더링', () => {
  const defaultProps = {
    isOwn: false,
    showAvatar: true,
    showTimestamp: true,
    readByCount: 0,
    roomType: 'DIRECT' as const,
  };

  // ========== 이미지 메시지 렌더링 ==========

  describe('type=IMAGE 메시지', () => {
    it('thumbnailUrl이 있으면 <img> 태그로 썸네일을 렌더링한다', () => {
      const message = createMessage({
        type: 'IMAGE',
        content: 'photo.jpg|1024000',
        fileUrl: '/files/download/rooms/room-1/uuid/photo.jpg',
        thumbnailUrl: '/files/download/rooms/room-1/uuid/photo.jpg-thumb.jpg',
        width: 800,
        height: 600,
        mimeType: 'image/jpeg',
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      const img = screen.getByRole('img');
      expect(img).toBeInTheDocument();
      expect(img).toHaveAttribute('src', message.thumbnailUrl);
    });

    it('thumbnailUrl이 없으면 로딩 placeholder를 표시한다', () => {
      const message = createMessage({
        type: 'IMAGE',
        content: 'photo.jpg|1024000',
        fileUrl: '/files/download/rooms/room-1/uuid/photo.jpg',
        // thumbnailUrl 없음 — 아직 처리 중
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      // img 태그가 없어야 하고, 로딩 상태가 표시되어야 함
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
      // 로딩 스피너 또는 placeholder 존재
      const placeholder = document.querySelector('[class*="animate"]');
      expect(placeholder).toBeInTheDocument();
    });

    it('width/height가 있으면 올바른 aspect ratio 스타일을 적용한다', () => {
      const message = createMessage({
        type: 'IMAGE',
        content: 'photo.jpg|1024000',
        fileUrl: '/files/download/rooms/room-1/uuid/photo.jpg',
        thumbnailUrl: '/files/download/rooms/room-1/uuid/photo.jpg-thumb.jpg',
        width: 1920,
        height: 1080,
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      // 이미지 컨테이너에 aspect ratio가 적용되어야 함
      const container = screen.getByRole('img').closest('div');
      expect(container).toHaveStyle({ aspectRatio: '1920 / 1080' });
    });

    it('이미지 최대 너비는 300px로 제한된다', () => {
      const message = createMessage({
        type: 'IMAGE',
        content: 'wide.jpg|2048000',
        fileUrl: '/files/download/rooms/room-1/uuid/wide.jpg',
        thumbnailUrl: '/files/download/rooms/room-1/uuid/wide.jpg-thumb.jpg',
        width: 4000,
        height: 3000,
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      const container = screen.getByRole('img').closest('div');
      expect(container).toHaveStyle({ maxWidth: '300px' });
    });

    it('이미지 클릭 시 onOpenMedia 콜백이 호출된다', async () => {
      const onOpenMedia = vi.fn();
      const message = createMessage({
        type: 'IMAGE',
        content: 'photo.jpg|1024000',
        fileUrl: '/files/download/rooms/room-1/uuid/photo.jpg',
        thumbnailUrl: '/files/download/rooms/room-1/uuid/photo.jpg-thumb.jpg',
        width: 800,
        height: 600,
      });

      render(
        <MessageBubble message={message} {...defaultProps} onOpenMedia={onOpenMedia} />
      );

      const img = screen.getByRole('img');
      img.click();

      expect(onOpenMedia).toHaveBeenCalledWith(message);
    });
  });

  // ========== 파일 메시지 렌더링 ==========

  describe('type=FILE 메시지', () => {
    it('파일명과 다운로드 버튼을 렌더링한다', () => {
      const message = createMessage({
        type: 'FILE',
        content: 'report.pdf|2048576',
        fileUrl: '/files/download/rooms/room-1/uuid/report.pdf',
        mimeType: 'application/pdf',
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      // 파일명이 표시되어야 함
      expect(screen.getByText('report.pdf')).toBeInTheDocument();
      // 다운로드 버튼/링크가 존재해야 함
      const downloadLink = screen.getByRole('link');
      expect(downloadLink).toHaveAttribute('href', message.fileUrl);
      expect(downloadLink).toHaveAttribute('download');
    });

    it('파일 크기를 포맷하여 표시한다', () => {
      const message = createMessage({
        type: 'FILE',
        content: 'data.xlsx|5242880',
        fileUrl: '/files/download/rooms/room-1/uuid/data.xlsx',
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      // 5MB로 표시
      expect(screen.getByText(/5.*MB/i)).toBeInTheDocument();
    });

    it('파일 아이콘이 렌더링된다', () => {
      const message = createMessage({
        type: 'FILE',
        content: 'readme.txt|1024',
        fileUrl: '/files/download/rooms/room-1/uuid/readme.txt',
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      // FileText 아이콘 (lucide-react SVG) 존재 확인
      const svg = document.querySelector('svg');
      expect(svg).toBeInTheDocument();
    });
  });

  // ========== Fallback 처리 ==========

  describe('알 수 없는 메시지 타입', () => {
    it('TEXT 타입은 일반 텍스트로 렌더링한다', () => {
      const message = createMessage({
        type: 'TEXT',
        content: '안녕하세요!',
      });

      render(<MessageBubble message={message} {...defaultProps} />);

      expect(screen.getByText('안녕하세요!')).toBeInTheDocument();
      // img 태그 없음
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    it('fileUrl 없는 IMAGE 타입은 graceful fallback (에러 없이 렌더링)', () => {
      const message = createMessage({
        type: 'IMAGE',
        content: 'broken.jpg|0',
        // fileUrl 없음
      });

      // 에러 없이 렌더링되어야 함
      expect(() => {
        render(<MessageBubble message={message} {...defaultProps} />);
      }).not.toThrow();
    });
  });
});
