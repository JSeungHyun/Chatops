export type MediaKind = 'image' | 'video' | 'file';

const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp']);
const VIDEO_EXT = new Set(['mp4', 'webm', 'mov']);

export function getMediaKind(fileName: string | null | undefined): MediaKind {
  if (!fileName) return 'file';
  const dot = fileName.lastIndexOf('.');
  if (dot < 0) return 'file';
  const ext = fileName.slice(dot + 1).toLowerCase();
  if (IMAGE_EXT.has(ext)) return 'image';
  if (VIDEO_EXT.has(ext)) return 'video';
  return 'file';
}
