import { LocalMediaItem, LocalAudioItem, LocalTextItem, SubtitleSegment } from './editorState';

export interface ClipInterval {
  clip: LocalMediaItem;
  index: number;
  startGlobalMs: number;
  endGlobalMs: number;
  durationMs: number;
}

export interface GlobalToLocalResult {
  activeClip: LocalMediaItem | null;
  activeClipIndex: number;
  localVideoTimeMs: number;
  clipStartGlobalMs: number;
  clipEndGlobalMs: number;
  clipElapsedTimelineMs: number;
}

/**
 * Computes global timeline mapping for sequential clips.
 */
export function getClipIntervals(clips: LocalMediaItem[] = []): ClipInterval[] {
  const sequentialClips = clips.filter(c => !c.isOverlay);
  const intervals: ClipInterval[] = [];
  let currentStart = 0;

  sequentialClips.forEach((clip, index) => {
    let dur = clip.duration || 3000;
    // TODAS as transições causam sobreposição no CapCut, então subtraímos de qualquer tipo que não seja 'none'
    if (index < sequentialClips.length - 1 && clip.nextTransition && clip.nextTransition.type !== 'none') {
      dur -= (clip.nextTransition.duration || 0);
    }
    dur = Math.max(100, dur);

    const end = currentStart + dur;
    intervals.push({
      clip,
      index,
      startGlobalMs: currentStart,
      endGlobalMs: end,
      durationMs: dur,
    });
    currentStart = end;
  });

  return intervals;
}

/**
 * Calculates total global project duration in ms.
 */
export function calculateTotalDurationMs(
  clips: LocalMediaItem[] = [],
  audios: LocalAudioItem[] = [],
  texts: LocalTextItem[] = [],
  subtitles: SubtitleSegment[] = []
): number {
  const intervals = getClipIntervals(clips);
  let total = intervals.length > 0 ? intervals[intervals.length - 1].endGlobalMs : 0;

  // Include overlays
  clips.filter(c => c.isOverlay).forEach(c => {
    total = Math.max(total, (c.startTime || 0) + (c.duration || 0));
  });

  // Include audios
  audios.forEach(a => {
    total = Math.max(total, (a.startTime || 0) + (a.duration || 0));
  });

  // Include texts
  texts.forEach(t => {
    total = Math.max(total, (t.startTime || 0) + (t.duration || 0));
  });

  // Include subtitles
  subtitles.forEach(s => {
    total = Math.max(total, s.endTime || 0);
  });

  return Math.max(0, total);
}

/**
 * Converts global time in milliseconds to local video position inside the active clip.
 */
export function globalToLocalTime(
  globalMs: number,
  clips: LocalMediaItem[] = []
): GlobalToLocalResult {
  const intervals = getClipIntervals(clips);
  if (intervals.length === 0) {
    return {
      activeClip: null,
      activeClipIndex: -1,
      localVideoTimeMs: 0,
      clipStartGlobalMs: 0,
      clipEndGlobalMs: 0,
      clipElapsedTimelineMs: 0,
    };
  }

  const totalDuration = intervals[intervals.length - 1].endGlobalMs;
  const clampedGlobal = Math.max(0, Math.min(globalMs, totalDuration));

  let targetInterval = intervals.find(
    i => clampedGlobal >= i.startGlobalMs && clampedGlobal < i.endGlobalMs
  );

  if (!targetInterval) {
    targetInterval = intervals[intervals.length - 1];
  }

  const clip = targetInterval.clip;
  const clipElapsedTimelineMs = Math.max(0, clampedGlobal - targetInterval.startGlobalMs);
  const trimStart = clip.trimStart || 0;
  const speed = clip.speed || 1.0;
  const localVideoTimeMs = trimStart + clipElapsedTimelineMs * speed;

  return {
    activeClip: clip,
    activeClipIndex: targetInterval.index,
    localVideoTimeMs,
    clipStartGlobalMs: targetInterval.startGlobalMs,
    clipEndGlobalMs: targetInterval.endGlobalMs,
    clipElapsedTimelineMs,
  };
}
