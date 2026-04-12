import { memo } from 'react';
import clsx from 'clsx';
import { FileText, Download } from 'lucide-react';
import { Avatar } from '@/components/common/Avatar';
import { formatDate, formatFileSize } from '@/utils/format';
import type { Message } from '@/types/message';
import type { RoomType } from '@/types/chat';

interface MessageBubbleProps {
  message: Message;
  isOwn: boolean;
  showAvatar: boolean;
  showTimestamp: boolean;
  readByCount?: number;
  roomType?: RoomType;
}

export const MessageBubble = memo(function MessageBubble({
  message,
  isOwn,
  showAvatar,
  showTimestamp,
  readByCount = 0,
  roomType = 'DIRECT',
}: MessageBubbleProps) {
  // Parse file content: "filename|size" or just "filename"
  const parseFileContent = (content: string) => {
    const parts = content.split('|');
    return {
      fileName: parts[0] || 'File',
      fileSize: parts[1] ? parseInt(parts[1], 10) : null,
    };
  };

  const fileInfo = (message.type === 'IMAGE' || message.type === 'FILE')
    ? parseFileContent(message.content)
    : null;

  return (
    <div
      className={clsx('flex gap-2', isOwn ? 'flex-row-reverse' : 'flex-row')}
    >
      {/* Avatar space */}
      <div className="w-8 shrink-0">
        {showAvatar && !isOwn && (
          <Avatar
            src={message.user.avatar}
            name={message.user.nickname}
            size="sm"
          />
        )}
      </div>

      <div
        className={clsx(
          'flex max-w-[70%] flex-col',
          isOwn ? 'items-end' : 'items-start',
        )}
      >
        {/* Sender name */}
        {showAvatar && !isOwn && (
          <span className="mb-1 text-xs font-medium text-slate-600">
            {message.user.nickname}
          </span>
        )}

        {/* Bubble */}
        <div
          className={clsx(
            'rounded-2xl px-3.5 py-2 text-sm leading-relaxed',
            isOwn
              ? 'rounded-tr-md bg-primary-600 text-white'
              : 'rounded-tl-md bg-slate-100 text-slate-900',
          )}
        >
          {message.type === 'TEXT' && (
            <p className="whitespace-pre-wrap break-words">{message.content}</p>
          )}

          {message.type === 'IMAGE' && message.fileUrl && (
            <div>
              <a href={message.fileUrl} target="_blank" rel="noopener noreferrer">
                <img
                  src={message.fileUrl}
                  alt={fileInfo?.fileName || 'Image'}
                  className="max-h-64 max-w-full cursor-pointer rounded-lg transition-opacity hover:opacity-90"
                  loading="lazy"
                  onError={(e) => {
                    const target = e.currentTarget;
                    target.style.display = 'none';
                    const fallback = document.createElement('span');
                    fallback.textContent = fileInfo?.fileName || '이미지를 불러올 수 없습니다';
                    fallback.className = 'text-sm opacity-60';
                    target.parentElement?.appendChild(fallback);
                  }}
                />
              </a>
              {fileInfo?.fileSize && (
                <p className={clsx('mt-1 text-[11px]', isOwn ? 'text-primary-200' : 'text-slate-400')}>
                  {fileInfo.fileName} · {formatFileSize(fileInfo.fileSize)}
                </p>
              )}
            </div>
          )}

          {message.type === 'FILE' && message.fileUrl && (
            <a
              href={message.fileUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={clsx(
                'flex items-center gap-2 rounded-lg p-2 transition-colors',
                isOwn
                  ? 'bg-primary-700/50 hover:bg-primary-700/70'
                  : 'bg-slate-200/50 hover:bg-slate-200',
              )}
            >
              <FileText className="h-5 w-5 shrink-0" />
              <div className="min-w-0 flex-1">
                <span className="block truncate text-sm">
                  {fileInfo?.fileName || 'File'}
                </span>
                {fileInfo?.fileSize && (
                  <span className={clsx('text-[11px]', isOwn ? 'text-primary-200' : 'text-slate-400')}>
                    {formatFileSize(fileInfo.fileSize)}
                  </span>
                )}
              </div>
              <Download className="h-4 w-4 shrink-0" />
            </a>
          )}
        </div>

        {/* Timestamp + Read receipt */}
        {showTimestamp && (
          <div className={clsx('mt-0.5 flex items-center gap-1.5', isOwn ? 'flex-row-reverse' : 'flex-row')}>
            <span className="text-[11px] text-slate-400">
              {formatDate(message.createdAt)}
            </span>
            {isOwn && readByCount > 0 && (
              <span className="text-[11px] font-medium text-primary-500">
                {roomType === 'DIRECT' ? '읽음' : `${readByCount}명 읽음`}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
});
