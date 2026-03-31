import { memo } from 'react';
import clsx from 'clsx';
import { FileText, Download } from 'lucide-react';
import { Avatar } from '@/components/common/Avatar';
import { formatDate } from '@/utils/format';
import type { Message } from '@/types/message';

interface MessageBubbleProps {
  message: Message;
  isOwn: boolean;
  showAvatar: boolean;
  showTimestamp: boolean;
}

export const MessageBubble = memo(function MessageBubble({
  message,
  isOwn,
  showAvatar,
  showTimestamp,
}: MessageBubbleProps) {
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
            <img
              src={message.fileUrl}
              alt={message.content || 'Image'}
              className="max-h-64 max-w-full rounded-lg"
              loading="lazy"
            />
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
              <span className="min-w-0 flex-1 truncate text-sm">
                {message.content || 'File'}
              </span>
              <Download className="h-4 w-4 shrink-0" />
            </a>
          )}
        </div>

        {/* Timestamp */}
        {showTimestamp && (
          <span className="mt-0.5 text-[11px] text-slate-400">
            {formatDate(message.createdAt)}
          </span>
        )}
      </div>
    </div>
  );
});
