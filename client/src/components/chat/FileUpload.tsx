import { Paperclip, Loader2 } from 'lucide-react';
import { useRef, useState } from 'react';
import api from '@/api/axios';
import clsx from 'clsx';

const ACCEPT_TYPES = 'image/jpeg,image/png,image/gif,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,application/zip';
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

interface FileUploadProps {
  onFileUploaded: (result: { fileUrl: string; fileName: string; contentType: string }) => void;
  disabled?: boolean;
}

export function FileUpload({ onFileUploaded, disabled }: FileUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const handleClick = () => {
    if (!uploading && !disabled) {
      fileInputRef.current?.click();
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Reset input so same file can be selected again
    e.target.value = '';

    // Client-side validation
    if (file.size > MAX_FILE_SIZE) {
      setError('파일 크기는 10MB를 초과할 수 없습니다');
      setTimeout(() => setError(null), 3000);
      return;
    }

    setUploading(true);
    setProgress(0);
    setError(null);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const res = await api.post<{
        fileUrl: string;
        fileName: string;
        fileSize: number;
        contentType: string;
      }>('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          if (e.total) {
            setProgress(Math.round((e.loaded / e.total) * 100));
          }
        },
      });

      onFileUploaded({
        fileUrl: res.data.fileUrl,
        fileName: res.data.fileName,
        contentType: res.data.contentType,
      });
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      const message = axiosErr?.response?.data?.message
        || (err instanceof Error ? err.message : '파일 업로드에 실패했습니다');
      setError(message);
      setTimeout(() => setError(null), 3000);
    } finally {
      setUploading(false);
      setProgress(0);
    }
  };

  return (
    <div className="relative">
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPT_TYPES}
        onChange={handleFileChange}
        className="hidden"
      />
      <button
        type="button"
        onClick={handleClick}
        disabled={uploading || disabled}
        className={clsx(
          'rounded-lg p-2 transition-colors',
          uploading || disabled
            ? 'cursor-not-allowed text-slate-300'
            : 'text-slate-400 hover:bg-slate-100 hover:text-slate-600',
        )}
        aria-label="Attach file"
      >
        {uploading ? (
          <Loader2 className="h-5 w-5 animate-spin" />
        ) : (
          <Paperclip className="h-5 w-5" />
        )}
      </button>

      {/* Upload progress */}
      {uploading && (
        <div className="absolute bottom-full left-1/2 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-slate-800 px-3 py-1.5 text-xs text-white shadow-lg">
          업로드 중... {progress}%
          <div className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-slate-800" />
        </div>
      )}

      {/* Error tooltip */}
      {error && (
        <div className="absolute bottom-full left-1/2 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-red-600 px-3 py-1.5 text-xs text-white shadow-lg">
          {error}
          <div className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-red-600" />
        </div>
      )}
    </div>
  );
}
