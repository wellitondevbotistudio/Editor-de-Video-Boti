import * as FileSystem from 'expo-file-system';
import { Platform } from 'react-native';
import { FFprobeKit } from 'ffmpeg-kit-react-native';

export const VALIDATION_LIMITS = {
  MAX_FILE_SIZE: 500 * 1024 * 1024, // 500 MB
  MAX_VIDEO_DURATION_MS: 10 * 60 * 1000, // 10 minutes
  MAX_AUDIO_DURATION_MS: 20 * 60 * 1000, // 20 minutes
  MAX_IMAGE_DIMENSION: 8192,
  MIN_DURATION_MS: 100,
};

export const SUPPORTED_FORMATS = {
  video: ['mp4', 'mov', 'webm', 'm4v', 'avi', 'mkv'],
  image: ['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif'],
  gif: ['gif'],
  audio: ['mp3', 'wav', 'aac', 'm4a', 'ogg', 'opus'],
  font: ['ttf', 'otf'],
};

export type FileValidationError =
  | 'FILE_NOT_FOUND'
  | 'FILE_TOO_LARGE'
  | 'UNSUPPORTED_FORMAT'
  | 'CORRUPTED_OR_INVALID'
  | 'INVALID_DIMENSIONS'
  | 'INVALID_DURATION'
  | 'MISSING_STREAM'
  | 'COPY_FAILED';

export interface FileMetadata {
  uri: string;
  name: string;
  type: 'video' | 'photo' | 'audio' | 'gif';
  size: number;
  durationMs: number;
  width?: number;
  height?: number;
  extension: string;
}

export interface ValidationResult {
  isValid: boolean;
  error?: FileValidationError;
  metadata?: FileMetadata;
}

export function sanitizeFileName(name: string): string {
  const parts = name.split('.');
  const ext = parts.length > 1 ? parts.pop() || '' : '';
  const base = parts.join('.') || name;
  const normalized = base.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const safe = normalized.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 100);
  return ext ? `${safe}.${ext.toLowerCase()}` : safe;
}

/**
 * Validates a file and extracts its metadata.
 * On Web, uses Browser APIs to avoid crashing with FFprobe.
 */
export async function validateFile(
    uri: string,
    expectedCategory?: 'video' | 'image' | 'audio' | 'gif' | 'font',
    extra?: { mimeType?: string; filename?: string; size?: number }
): Promise<ValidationResult> {
  try {
    const isWeb = Platform.OS === 'web';

    // 1. Basic check
    let fileSize = extra?.size || 0;
    if (!isWeb && fileSize === 0) {
        try {
            const info = await FileSystem.getInfoAsync(uri, { size: true });
            if (info.exists) fileSize = (info as any).size || 0;
        } catch (e) {}
    }

    if (fileSize > VALIDATION_LIMITS.MAX_FILE_SIZE) {
      return { isValid: false, error: 'FILE_TOO_LARGE' };
    }

    // 2. Determine extension and category
    const mime = extra?.mimeType?.toLowerCase() || '';
    let effectiveName = extra?.filename || uri.split('/').pop() || 'file';

    // Add extension if missing on web blobs
    if (isWeb && !effectiveName.includes('.') && mime) {
        const inferredExt = mime.split('/').pop();
        if (inferredExt) effectiveName += `.${inferredExt}`;
    }

    const nameParts = effectiveName.split('.');
    const ext = nameParts.length > 1 ? nameParts.pop()?.toLowerCase() || '' : '';

    let category: 'video' | 'photo' | 'audio' | 'gif' | 'font' | null = null;

    if (SUPPORTED_FORMATS.video.includes(ext) || mime.startsWith('video/')) category = 'video';
    else if (SUPPORTED_FORMATS.image.includes(ext) || mime.startsWith('image/')) {
        if (ext === 'gif' || mime === 'image/gif') category = 'gif';
        else category = 'photo';
    }
    else if (SUPPORTED_FORMATS.audio.includes(ext) || mime.startsWith('audio/')) category = 'audio';
    else if (SUPPORTED_FORMATS.font.includes(ext) || mime.includes('font') || mime.includes('opentype')) category = 'font';

    // Validation against expected category
    if (!category) return { isValid: false, error: 'UNSUPPORTED_FORMAT' };

    if (expectedCategory) {
        const mappedCategory = category === 'photo' ? 'image' : category;
        if (expectedCategory !== mappedCategory) return { isValid: false, error: 'UNSUPPORTED_FORMAT' };
    }

    if (category === 'font') {
        return { isValid: true, metadata: { uri, name: effectiveName, type: 'font' as any, size: fileSize, durationMs: 0, extension: ext } };
    }

    // 3. Metadata Extraction
    let duration = 0;
    let width = 0;
    let height = 0;

    if (isWeb) {
        if (category === 'video' || category === 'gif') {
            const video = document.createElement('video');
            video.src = uri;
            await new Promise((resolve) => {
                video.onloadedmetadata = resolve;
                video.onerror = resolve;
                setTimeout(resolve, 3000);
            });
            duration = video.duration * 1000 || 0;
            width = video.videoWidth || 0;
            height = video.videoHeight || 0;
        } else if (category === 'photo') {
            const img = new Image();
            img.src = uri;
            await new Promise((resolve) => {
                img.onload = resolve;
                img.onerror = resolve;
                setTimeout(resolve, 3000);
            });
            width = img.width;
            height = img.height;
        } else if (category === 'audio') {
            const audio = new Audio();
            audio.src = uri;
            await new Promise((resolve) => {
                audio.onloadedmetadata = resolve;
                audio.onerror = resolve;
                setTimeout(resolve, 3000);
            });
            duration = audio.duration * 1000 || 0;
        }
    } else {
        const session = await FFprobeKit.getMediaInformation(uri);
        const mediaInfo = await session.getMediaInformation();
        if (!mediaInfo) return { isValid: false, error: 'CORRUPTED_OR_INVALID' };

        const dStr = mediaInfo.getDuration();
        duration = (dStr ? parseFloat(String(dStr)) : 0) * 1000;

        const streams = mediaInfo.getStreams();
        const vStream = streams.find(s => s.getType() === 'video');
        width = vStream?.getWidth() || 0;
        height = vStream?.getHeight() || 0;

        if (category === 'video' && !vStream) return { isValid: false, error: 'MISSING_STREAM' };
        if (category === 'audio' && !streams.some(s => s.getType() === 'audio')) return { isValid: false, error: 'MISSING_STREAM' };
    }

    const result: ValidationResult = {
      isValid: true,
      metadata: { uri, name: effectiveName, type: category as any, size: fileSize, durationMs: duration, width, height, extension: ext }
    };

    console.log(`[Validation] Success: ${category} - ${effectiveName} (${duration}ms, ${width}x${height})`);
    return result;

  } catch (e) {
    console.error('File validation critical error:', e);
    return { isValid: false, error: 'CORRUPTED_OR_INVALID' };
  }
}

export async function safeCopyToInternal(metadata: FileMetadata): Promise<string | null> {
    if (Platform.OS === 'web') return metadata.uri;
    try {
        const sanitized = sanitizeFileName(metadata.name);
        const destUri = `${FileSystem.documentDirectory}${Date.now()}_${sanitized}`;
        await FileSystem.copyAsync({ from: metadata.uri, to: destUri });
        return destUri;
    } catch (e) {
        console.error('Safe copy failed:', e);
        return null;
    }
}
