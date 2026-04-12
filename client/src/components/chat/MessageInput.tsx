import { useState, useRef, useCallback, useEffect } from 'react';
import { Send } from 'lucide-react';
import { FileUpload } from './FileUpload';
import clsx from 'clsx';
import { sendStompMessage } from '@/socket/socket';

interface MessageInputProps {
  onSend: (content: string) => void;
  onFileSend?: (fileUrl: string, fileName: string, fileSize: number, contentType: string) => void;
  disabled?: boolean;
  roomId?: string;
}

const MAX_LENGTH = 5000;
const TYPING_DEBOUNCE_MS = 1500;

export function MessageInput({ onSend, onFileSend, disabled, roomId }: MessageInputProps) {
  const [content, setContent] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isTypingRef = useRef(false);

  const sendTyping = useCallback((isTyping: boolean) => {
    if (!roomId) return;
    sendStompMessage(`/app/chat/${roomId}/typing`, { typing: isTyping });
  }, [roomId]);

  const stopTyping = useCallback(() => {
    if (isTypingRef.current) {
      isTypingRef.current = false;
      sendTyping(false);
    }
  }, [sendTyping]);

  // Clear typing timer on unmount
  useEffect(() => {
    return () => {
      if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
      stopTyping();
    };
  }, [stopTyping]);

  const adjustHeight = useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 128)}px`; // max-h-32 = 128px
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    if (e.target.value.length <= MAX_LENGTH) {
      setContent(e.target.value);
      adjustHeight();

      // Typing indicator
      if (!isTypingRef.current) {
        isTypingRef.current = true;
        sendTyping(true);
      }
      if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
      typingTimerRef.current = setTimeout(() => {
        stopTyping();
      }, TYPING_DEBOUNCE_MS);
    }
  };

  const handleSend = () => {
    const trimmed = content.trim();
    if (!trimmed || disabled) return;
    if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
    stopTyping();
    onSend(trimmed);
    setContent('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileUploaded = useCallback((result: { fileUrl: string; fileName: string; fileSize: number; contentType: string }) => {
    onFileSend?.(result.fileUrl, result.fileName, result.fileSize, result.contentType);
  }, [onFileSend]);

  const nearLimit = content.length > MAX_LENGTH * 0.9;
  const canSend = content.trim().length > 0 && !disabled;

  return (
    <div className="relative border-t border-slate-200 bg-white p-3">
      <div className="flex items-end gap-2">
        <FileUpload onFileUploaded={handleFileUploaded} disabled={disabled} roomId={roomId} />

        <div className="relative min-w-0 flex-1">
          <textarea
            ref={textareaRef}
            value={content}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder="메시지를 입력하세요..."
            disabled={disabled}
            rows={1}
            className="block w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-primary-300 focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          />
          {nearLimit && (
            <span className="absolute bottom-1 right-3 text-[10px] text-slate-400">
              {content.length}/{MAX_LENGTH}
            </span>
          )}
        </div>

        <button
          onClick={handleSend}
          disabled={!canSend}
          className={clsx(
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl transition-colors',
            canSend
              ? 'bg-primary-600 text-white hover:bg-primary-700'
              : 'bg-slate-100 text-slate-300',
          )}
          aria-label="Send message"
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
