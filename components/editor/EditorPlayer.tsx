import React, { useRef, useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import { LocalMediaItem } from '@/services/editorState';

interface EditorPlayerProps {
  activeClip: LocalMediaItem | null;
  localVideoTimeMs: number;
  isPlaying: boolean;
  isSeeking: boolean;
  onPlaybackStatusUpdate: (status: AVPlaybackStatus) => void;
}

const DEFAULT_VIDEO_URI = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';

export function EditorPlayer({
  activeClip,
  localVideoTimeMs,
  isPlaying,
  isSeeking,
  onPlaybackStatusUpdate,
}: EditorPlayerProps) {
  const videoRef = useRef<Video>(null);
  const lastUriRef = useRef<string | null>(null);
  const lastIsPlayingRef = useRef<boolean>(false);
  const isUpdatingPosition = useRef(false);
  const pendingPosition = useRef<number | null>(null);

  const currentUri = activeClip?.uri || DEFAULT_VIDEO_URI;
  const speed = activeClip?.speed || 1.0;
  const isVisible = activeClip ? activeClip.visible : true;

  // Compila os transforms em um array válido para o React Native
  const transforms: any[] = [];
  if (activeClip?.transform?.flipH) transforms.push({ scaleX: -1 });
  if (activeClip?.transform?.flipV) transforms.push({ scaleY: -1 });

  // A MATEMÁTICA DE SINCRONIZAÇÃO PERFEITA E OTIMIZADA PARA SCRUBBING
  useEffect(() => {
    // Se o clip trocou, força um reset de reprodução para aquele clip
    if (currentUri !== lastUriRef.current) {
      lastUriRef.current = currentUri;
      lastIsPlayingRef.current = false;
    }

    if (!videoRef.current) return;

    if (isPlaying) {
      // Se acabou de dar play (estava pausado), injeta a posição exata de inicio
      const justStarted = !lastIsPlayingRef.current;
      lastIsPlayingRef.current = true;
      videoRef.current.setStatusAsync({
        ...(justStarted ? { positionMillis: localVideoTimeMs } : {}),
        shouldPlay: isVisible,
        rate: speed,
        shouldCorrectPitch: true,
      }).catch(() => {});
    } else {
      // Está pausado.
      if (lastIsPlayingRef.current) {
         // Acabou de pausar
         lastIsPlayingRef.current = false;
         videoRef.current.setStatusAsync({ shouldPlay: false }).catch(() => {});
      } else {
         // Já estava pausado, então isso é o usuário arrastando a timeline (Scrubbing / Seek)
         // Usamos um sistema de queue (fila) pra não sobrecarregar o Expo AV e travar o app
         const updatePos = async (pos: number) => {
           if (isUpdatingPosition.current) {
             pendingPosition.current = pos;
             return;
           }
           isUpdatingPosition.current = true;
           try {
             await videoRef.current?.setPositionAsync(pos);
           } catch(e) {}
           isUpdatingPosition.current = false;

           if (pendingPosition.current !== null) {
             const nextPos = pendingPosition.current;
             pendingPosition.current = null;
             updatePos(nextPos);
           }
         };
         updatePos(localVideoTimeMs);
      }
    }
  }, [isPlaying, localVideoTimeMs, isVisible, speed, currentUri]);

  return (
    <Video
      ref={videoRef}
      style={[
        styles.video,
        !isVisible && styles.hidden,
        transforms.length > 0 && { transform: transforms },
      ]}
      source={{ uri: currentUri }}
      useNativeControls={false}
      resizeMode={ResizeMode.CONTAIN}
      shouldPlay={isPlaying && isVisible}
      onPlaybackStatusUpdate={onPlaybackStatusUpdate}
    />
  );
}

const styles = StyleSheet.create({
  video: {
    width: '100%',
    height: '100%',
  },
  hidden: {
    opacity: 0,
  },
});
