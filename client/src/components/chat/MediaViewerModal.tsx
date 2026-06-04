import { useEffect } from 'react';
import { X, Download, Trash2 } from 'lucide-react';
import clsx from 'clsx';
import { getMediaKind } from '@/utils/media';

interface MediaViewerModalProps {
  open: boolean;
  fileUrl: string;
  fileName: string;
  canDelete: boolean;
  onClose: () => void;
  onDelete: () => void;
}

export function MediaViewerModal({
  open,
  fileUrl,
  fileName,
  canDelete,
  onClose,
  onDelete,
}: MediaViewerModalProps) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  if (!open) return null;

  const kind = getMediaKind(fileName);

  const handleDelete = () => {
    if (!confirm('이 메시지를 삭제하시겠습니까?')) return;
    onDelete();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      {/* Top toolbar */}
      <div
        className="absolute left-0 right-0 top-0 flex items-center justify-between px-4 py-3"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="max-w-[60%] truncate text-sm text-white/90">{fileName}</p>
        <div className="flex items-center gap-2">
          <a
            href={fileUrl}
            download={fileName}
            className="rounded-lg p-2 text-white/90 transition-colors hover:bg-white/10"
            aria-label="Download"
          >
            <Download className="h-5 w-5" />
          </a>
          {canDelete && (
            <button
              type="button"
              onClick={handleDelete}
              className="rounded-lg p-2 text-white/90 transition-colors hover:bg-red-500/30 hover:text-red-300"
              aria-label="Delete"
            >
              <Trash2 className="h-5 w-5" />
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-2 text-white/90 transition-colors hover:bg-white/10"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      </div>

      {/* Media content */}
      <div
        className={clsx('flex max-h-[90vh] max-w-[92vw] items-center justify-center')}
        onClick={(e) => e.stopPropagation()}
      >
        {kind === 'image' && (
          <img
            src={fileUrl}
            alt={fileName}
            className="max-h-[90vh] max-w-[92vw] rounded-lg object-contain"
          />
        )}
        {kind === 'video' && (
          <video
            src={fileUrl}
            controls
            autoPlay
            className="max-h-[90vh] max-w-[92vw] rounded-lg"
          />
        )}
      </div>
    </div>
  );
}
