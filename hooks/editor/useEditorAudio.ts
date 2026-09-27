import { useEffect, useRef } from 'react';
import { Audio } from 'expo-av';
import { useEditorStore } from '@/services/editorState';

interface UseEditorAudioProps {
  currentTime: number;
  isPlaying: boolean;
}

export function useEditorAudio({ currentTime, isPlaying }: UseEditorAudioProps) {
  const audios = useEditorStore(state => state.config.audios || []);
  const soundPool = useRef<Record<string, Audio.Sound>>({});
  const lastSyncedTimeRef = useRef<number>(-1);
  const lastPlayingStateRef = useRef<boolean>(false);

  useEffect(() => {
    let cancelled = false;

    const syncAudios = async () => {
      // 1. Remove deleted audios from pool
      for (const id in soundPool.current) {
        if (!audios.find(a => a.id === id)) {
          try {
            await soundPool.current[id].unloadAsync();
          } catch (e) {}
          delete soundPool.current[id];
        }
      }

      const stateChanged = isPlaying !== lastPlayingStateRef.current;
      const bigJump = Math.abs(currentTime - lastSyncedTimeRef.current) > 300;

      lastSyncedTimeRef.current = currentTime;
      lastPlayingStateRef.current = isPlaying;

      // 2. Process each audio item
      for (const audio of audios) {
        if (cancelled) break;

        let sound = soundPool.current[audio.id];
        if (!sound) {
          try {
            const { sound: newSound } = await Audio.Sound.createAsync({ uri: audio.uri });
            soundPool.current[audio.id] = newSound;
            sound = newSound;
          } catch (e) {
            console.error(`Failed to load audio: ${audio.uri}`, e);
            continue;
          }
        }

        const isActive =
          audio.visible &&
          currentTime >= audio.startTime &&
          currentTime < audio.startTime + audio.duration;

        if (isActive) {
          const targetRelativePos = currentTime - audio.startTime + (audio.trimStart || 0);

          let targetVolume = audio.volume ?? 1.0;
          const localPos = currentTime - audio.startTime;
          if (audio.fadeIn > 0 && localPos < audio.fadeIn) {
            targetVolume *= localPos / audio.fadeIn;
          } else if (audio.fadeOut > 0 && localPos > audio.duration - audio.fadeOut) {
            const fadeOutStart = audio.duration - audio.fadeOut;
            const fadePos = localPos - fadeOutStart;
            targetVolume *= Math.max(0, 1 - fadePos / audio.fadeOut);
          }
          if (audio.muted) targetVolume = 0;

          try {
            const currentStatus = await sound.getStatusAsync();
            if (currentStatus.isLoaded) {
              const drift = Math.abs(currentStatus.positionMillis - targetRelativePos);
              const volumeDiff = Math.abs((currentStatus.volume ?? 1) - targetVolume);

              const needsSync = stateChanged || bigJump || drift > 250 || volumeDiff > 0.05;

              if (needsSync) {
                await sound.setStatusAsync({
                  shouldPlay: isPlaying,
                  positionMillis: targetRelativePos,
                  volume: Math.max(0, Math.min(1, targetVolume)),
                });
              }
            }
          } catch (e) {}
        } else {
          try {
            const status = await sound.getStatusAsync();
            if (status.isLoaded && status.shouldPlay) {
              await sound.pauseAsync();
            }
          } catch (e) {}
        }
      }
    };

    syncAudios();

    return () => {
      cancelled = true;
    };
  }, [audios, currentTime, isPlaying]);

  useEffect(() => {
    return () => {
      Object.values(soundPool.current).forEach(async s => {
        try {
          await s.unloadAsync();
        } catch (e) {}
      });
      soundPool.current = {};
    };
  }, []);
}
