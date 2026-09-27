import { useState, useRef, useCallback, useEffect, useMemo } from 'react';
import { useEditorStore } from '@/services/editorState';
import { globalToLocalTime, calculateTotalDurationMs, GlobalToLocalResult } from '@/services/timelineService';

export interface UseEditorPlaybackReturn {
  currentTime: number;
  isPlaying: boolean;
  isSeeking: boolean;
  duration: number;
  activeClipInfo: GlobalToLocalResult;
  play: () => void;
  pause: () => void;
  togglePlay: () => void;
  seek: (targetGlobalMs: number) => void;
  seekRelative: (deltaMs: number) => void;
  onPlayerStatusUpdate: (status: any) => void;
  setIsSeeking: (seeking: boolean) => void;
}

export function useEditorPlayback(): UseEditorPlaybackReturn {
  const config = useEditorStore(state => state.config);
  const clips = config.clips || [];
  const audios = config.audios || [];
  const texts = config.texts || [];
  const subtitles = config.subtitles || [];

  const [currentTime, setCurrentTime] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isSeekingState, setIsSeekingState] = useState(false);

  const currentTimeRef = useRef(0);
  const isPlayingRef = useRef(false);
  const isSeekingRef = useRef(false);

  // PREVINE O BUG DO "PULO NO TEMPO" (Race condition do Expo AV)
  const ignoreUpdatesUntil = useRef(0);

  useEffect(() => {
    currentTimeRef.current = currentTime;
  }, [currentTime]);

  useEffect(() => {
    isPlayingRef.current = isPlaying;
  }, [isPlaying]);

  useEffect(() => {
    isSeekingRef.current = isSeekingState;
  }, [isSeekingState]);

  const duration = useMemo(() => {
    return calculateTotalDurationMs(clips, audios, texts, subtitles);
  }, [clips, audios, texts, subtitles]);

  const activeClipInfo = useMemo(() => {
    return globalToLocalTime(currentTime, clips);
  }, [currentTime, clips]);

  const play = useCallback(() => {
    if (currentTimeRef.current >= duration && duration > 0) {
      setCurrentTime(0);
      currentTimeRef.current = 0;
    }
    ignoreUpdatesUntil.current = Date.now() + 400; // Ignora os ecos do player por 400ms
    setIsPlaying(true);
  }, [duration]);

  const pause = useCallback(() => {
    setIsPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (isPlayingRef.current) {
      pause();
    } else {
      play();
    }
  }, [play, pause]);

  const seek = useCallback((targetGlobalMs: number) => {
    const clamped = Math.max(0, Math.min(targetGlobalMs, duration));
    currentTimeRef.current = clamped;
    setCurrentTime(clamped);
    ignoreUpdatesUntil.current = Date.now() + 400; // Ignora ecos após o seek manual
  }, [duration]);

  const seekRelative = useCallback((deltaMs: number) => {
    seek(currentTimeRef.current + deltaMs);
  }, [seek]);

  const setIsSeeking = useCallback((seeking: boolean) => {
    isSeekingRef.current = seeking;
    setIsSeekingState(seeking);
  }, []);

  const onPlayerStatusUpdate = useCallback((status: any) => {
    if (!status || !status.isLoaded || isSeekingRef.current) return;
    if (!isPlayingRef.current) return;
    if (Date.now() < ignoreUpdatesUntil.current) return; // Proteção contra posição antiga

    const currentGlobal = currentTimeRef.current;
    if (currentGlobal >= duration && duration > 0) {
      setIsPlaying(false);
      setCurrentTime(duration);
      return;
    }

    const clipInfo = globalToLocalTime(currentGlobal, clips);
    const clip = clipInfo.activeClip;

    if (!clip) {
      setIsPlaying(false);
      return;
    }

    const trimStart = clip.trimStart || 0;
    const speed = clip.speed || 1.0;
    const posMillis = status.positionMillis ?? 0;

    const sourceElapsed = posMillis - trimStart;
    const timelineElapsed = sourceElapsed / speed;
    const computedGlobalPos = clipInfo.clipStartGlobalMs + timelineElapsed;

    const endSourceTime = clip.trimEnd || clip.originalDuration || clip.duration;

    if (posMillis >= endSourceTime - 30 || computedGlobalPos >= clipInfo.clipEndGlobalMs) {
      const nextGlobal = clipInfo.clipEndGlobalMs;
      if (nextGlobal >= duration && duration > 0) {
        setIsPlaying(false);
        setCurrentTime(duration);
      } else {
        setCurrentTime(nextGlobal + 1);
        currentTimeRef.current = nextGlobal + 1;
      }
    } else if (Math.abs(computedGlobalPos - currentGlobal) > 16) {
      setCurrentTime(computedGlobalPos);
      currentTimeRef.current = computedGlobalPos;
    }
  }, [clips, duration]);

  return {
    currentTime,
    isPlaying,
    isSeeking: isSeekingState,
    duration,
    activeClipInfo,
    play,
    pause,
    togglePlay,
    seek,
    seekRelative,
    onPlayerStatusUpdate,
    setIsSeeking,
  };
}
