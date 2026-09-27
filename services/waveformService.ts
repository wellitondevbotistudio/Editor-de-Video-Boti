import { FFmpegKit, ReturnCode } from 'ffmpeg-kit-react-native';
import * as FileSystem from 'expo-file-system';
import { Platform } from 'react-native';

export interface WaveformData {
  samples: number[];
  durationMs: number;
}

const CACHE_DIR = `${FileSystem.cacheDirectory}waveforms/`;

// Base64 to Uint8Array polyfill for React Native
const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const lookup = new Uint8Array(256);
for (let i = 0; i < chars.length; i++) lookup[chars.charCodeAt(i)] = i;

function decodeBase64(base64: string): Uint8Array {
  let len = base64.length;
  let bufferLength = len * 0.75;
  if (base64[len - 1] === '=') {
    bufferLength--;
    if (base64[len - 2] === '=') bufferLength--;
  }
  const bytes = new Uint8Array(bufferLength);
  let p = 0;
  for (let i = 0; i < len; i += 4) {
    const encoded1 = lookup[base64.charCodeAt(i)];
    const encoded2 = lookup[base64.charCodeAt(i + 1)];
    const encoded3 = lookup[base64.charCodeAt(i + 2)];
    const encoded4 = lookup[base64.charCodeAt(i + 3)];
    bytes[p++] = (encoded1 << 2) | (encoded2 >> 4);
    if (p < bufferLength) bytes[p++] = ((encoded2 & 15) << 4) | (encoded3 >> 2);
    if (p < bufferLength) bytes[p++] = ((encoded3 & 3) << 6) | (encoded4 & 63);
  }
  return bytes;
}

export async function getWaveform(uri: string, durationMs: number): Promise<WaveformData | null> {
  if (Platform.OS === 'web') {
      // Simplified waveform for web to avoid crashing with FFmpeg
      return { samples: Array(100).fill(0.5), durationMs };
  }

  try {
    await ensureCacheDir();
    const hash = await generateHash(uri);
    const cachePath = `${CACHE_DIR}${hash}.json`;

    const info = await FileSystem.getInfoAsync(cachePath);
    if (info.exists) {
      const content = await FileSystem.readAsStringAsync(cachePath);
      return JSON.parse(content);
    }

    // Basic integrity check before running FFmpeg
    const fileInfo = await FileSystem.getInfoAsync(uri);
    if (!fileInfo.exists || fileInfo.size === 0) return null;

    // We want a manageable number of samples.
    // 10 samples per second is enough for visual representation.
    const sampleRate = 10;
    const pcmPath = `${FileSystem.cacheDirectory}temp_wv_${Date.now()}.pcm`;

    const command = `-i "${uri}" -ac 1 -filter:a aresample=${sampleRate} -f s16le -acodec pcm_s16le -y "${pcmPath}"`;
    const session = await FFmpegKit.execute(command);
    const returnCode = await session.getReturnCode();

    if (ReturnCode.isSuccess(returnCode)) {
      const base64 = await FileSystem.readAsStringAsync(pcmPath, { encoding: FileSystem.EncodingType.Base64 });
      if (!base64) return null;
      const bytes = decodeBase64(base64);

      const samples: number[] = [];
      const view = new DataView(bytes.buffer);
      for (let i = 0; i < bytes.length; i += 2) {
        if (i + 1 < bytes.length) {
          let val = view.getInt16(i, true); // Little Endian
          samples.push(Math.abs(val) / 32768);
        }
      }

      const data: WaveformData = { samples, durationMs };
      await FileSystem.writeAsStringAsync(cachePath, JSON.stringify(data));

      // Cleanup temp file
      await FileSystem.deleteAsync(pcmPath, { idempotent: true });

      return data;
    }

    return null;
  } catch (e) {
    console.error('Waveform generation error:', e);
    return null;
  }
}

async function ensureCacheDir() {
  const info = await FileSystem.getInfoAsync(CACHE_DIR);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(CACHE_DIR, { intermediates: true });
  }
}

async function generateHash(uri: string) {
  try {
      const info = await FileSystem.getInfoAsync(uri, { size: true });
      const size = (info as any).size || 0;
      const str = `${uri}_${size}`;
      let hash = 0;
      for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash;
      }
      return Math.abs(hash).toString(36);
  } catch (e) {
      return uri.split('/').pop()?.replace(/[^a-zA-Z0-9]/g, '') || Date.now().toString(36);
  }
}
