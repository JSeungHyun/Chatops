import { Paperclip, Loader2, X, Send, FileText } from 'lucide-react';
import { useRef, useState, useCallback } from 'react';
import api from '@/api/axios';
import clsx from 'clsx';
import { formatFileSize } from '@/utils/format';

const ACCEPT_TYPES = 'image/jpeg,image/png,image/gif,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,application/zip';
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

interface FileUploadProps {
  onFileUploaded: (result: { fileUrl: string; fileName: string; fileSize: number; contentType: string }) => void;
  disabled?: boolean;
  roomId?: string;
}

export function FileUpload({ onFileUploaded, disabled, roomId }: FileUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // Preview state
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const handleClick = () => {
    if (!uploading && !disabled && !selectedFile) {
      fileInputRef.current?.click();
    }
  };

  const clearPreview = useCallback(() => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setSelectedFile(null);
    setPreviewUrl(null);
    setError(null);
  }, [previewUrl]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';

    if (file.size > MAX_FILE_SIZE) {
      setError('파일 크기는 10MB를 초과할 수 없습니다');
      setTimeout(() => setError(null), 3000);
      return;
    }

    setSelectedFile(file);
    setError(null);

    // Generate preview for images
    if (file.type.startsWith('image/')) {
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleConfirmUpload = async () => {
    if (!selectedFile) return;

    setUploading(true);
    setProgress(0);

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      if (roomId) {
        formData.append('roomId', roomId);
      }

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
        fileSize: res.data.fileSize,
        contentType: res.data.contentType,
      });
      clearPreview();
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

  const isImage = selectedFile?.type.startsWith('image/') ?? false;

  return (
    <>
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
        {error && !selectedFile && (
          <div className="absolute bottom-full left-1/2 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-red-600 px-3 py-1.5 text-xs text-white shadow-lg">
            {error}
            <div className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-red-600" />
          </div>
        )}
      </div>

      {/* Preview overlay */}
      {selectedFile && !uploading && (
        <div className="absolute bottom-full left-0 right-0 mb-2 rounded-xl border border-slate-200 bg-white p-3 shadow-lg">
          <div className="flex items-start gap-3">
            {/* Preview content */}
            {isImage && previewUrl ? (
              <img
                src={previewUrl}
                alt={selectedFile.name}
                className="h-20 w-20 shrink-0 rounded-lg object-cover"
              />
            ) : (
              <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-lg bg-slate-100">
                <FileText className="h-8 w-8 text-slate-400" />
              </div>
            )}

            {/* File info */}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-900">
                {selectedFile.name}
              </p>
              <p className="text-xs text-slate-500">
                {formatFileSize(selectedFile.size)}
              </p>
              {error && (
                <p className="mt-1 text-xs text-red-500">{error}</p>
              )}
            </div>

            {/* Actions */}
            <div className="flex shrink-0 gap-1">
              <button
                type="button"
                onClick={clearPreview}
                className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                aria-label="Cancel"
              >
                <X className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={handleConfirmUpload}
                className="rounded-lg bg-primary-600 p-1.5 text-white transition-colors hover:bg-primary-700"
                aria-label="Send file"
              >
                <Send className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
