import React, { useMemo } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { AVPlaybackStatus } from 'expo-av';
import { EditorPlayer } from './EditorPlayer';
import { colors, spacing } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { ProjectCanvas, LocalMediaItem, LocalTextItem, SubtitleSegment, SubtitleStyle } from '@/services/editorState';
import { GlobalToLocalResult } from '@/services/timelineService';
import { getInterpolatedTransform, getTextPartsState } from '@/services/animationUtils';

interface EditorCanvasProps {
  canvas: ProjectCanvas;
  activeClipInfo: GlobalToLocalResult;
  currentTime: number;
  duration: number;
  isPlaying: boolean;
  isSeeking: boolean;
  clips: LocalMediaItem[];
  texts: LocalTextItem[];
  subtitles: SubtitleSegment[];
  subtitleStyle: SubtitleStyle;
  historyIndex: number;
  historyLength: number;
  onTogglePlay: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onPlaybackStatusUpdate: (status: AVPlaybackStatus) => void;
}

function hexToRgb(hex: string): string {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? `${parseInt(result[1], 16)}, ${parseInt(result[2], 16)}, ${parseInt(result[3], 16)}` : '0, 0, 0';
}

function formatTime(millis: number) {
  const totalSeconds = Math.floor(Math.max(0, millis) / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export function EditorCanvas({
  canvas,
  activeClipInfo,
  currentTime,
  duration,
  isPlaying,
  isSeeking,
  clips,
  texts,
  subtitles,
  subtitleStyle,
  historyIndex,
  historyLength,
  onTogglePlay,
  onUndo,
  onRedo,
  onPlaybackStatusUpdate,
}: EditorCanvasProps) {
  const canvasAspectRatio = (canvas?.width || 1920) / (canvas?.height || 1080);
  const activeClip = activeClipInfo.activeClip;

  const visualLayers = useMemo(() => {
    const layers = [
      ...clips
        .filter(c => c.isOverlay && c.visible && currentTime >= c.startTime && currentTime < c.startTime + c.duration)
        .map(o => ({
          ...o,
          layerType: 'overlay' as const,
          renderTransform: getInterpolatedTransform(o.keyframes, o.transform!, o.startTime, currentTime),
        })),
      ...texts
        .filter(t => t.visible && currentTime >= t.startTime && currentTime < t.startTime + t.duration)
        .map(t => ({
          ...t,
          layerType: 'text' as const,
          renderTransform: getInterpolatedTransform(t.keyframes, t.transform!, t.startTime, currentTime),
        })),
    ];
    return layers.sort((a, b) => a.zIndex - b.zIndex);
  }, [clips, texts, currentTime]);

  return (
    <View style={styles.preview}>
      <View style={[styles.canvasContainer, { aspectRatio: canvasAspectRatio }]}>
        <EditorPlayer
          activeClip={activeClip}
          localVideoTimeMs={activeClipInfo.localVideoTimeMs}
          isPlaying={isPlaying}
          isSeeking={isSeeking}
          onPlaybackStatusUpdate={onPlaybackStatusUpdate}
        />

        {visualLayers.map((layer: any) => {
          const rt = layer.renderTransform || layer.transform;
          if (layer.layerType === 'text') {
            const parts = getTextPartsState(layer, currentTime);
            const style = layer.style;
            return (
              <View
                key={layer.id}
                style={[
                  styles.textOverlay,
                  {
                    top: `${layer.position.y}%`,
                    left: `${layer.position.x}%`,
                    opacity: rt.opacity,
                    zIndex: layer.zIndex,
                    flexDirection: 'row',
                    flexWrap: 'wrap',
                    justifyContent:
                      style.textAlign === 'center'
                        ? 'center'
                        : style.textAlign === 'right'
                        ? 'flex-end'
                        : 'flex-start',
                    backgroundColor: style.backgroundEnabled
                      ? `rgba(${hexToRgb(style.backgroundColor)}, ${style.backgroundOpacity})`
                      : 'transparent',
                    padding: style.backgroundEnabled ? 8 : 0,
                    borderRadius: 4,
                  },
                ]}
              >
                {parts.map((p, pi) => (
                  <View
                    key={pi}
                    style={{ transform: [{ translateY: p.offsetY }, { scale: p.scale }], opacity: p.opacity }}
                  >
                    <Text
                      style={{
                        color: style.color,
                        fontSize: style.fontSize / 2,
                        fontFamily: style.fontFamily === 'System' ? undefined : style.fontFamily,
                        fontWeight: style.fontWeight as any,
                        letterSpacing: style.letterSpacing,
                        textShadowColor: style.shadowEnabled ? style.shadowColor : 'transparent',
                        textShadowOffset: style.shadowEnabled ? { width: 1, height: 1 } : { width: 0, height: 0 },
                        textShadowRadius: style.shadowEnabled ? style.shadowBlur : 0,
                      }}
                    >
                      {p.text}
                    </Text>
                  </View>
                ))}
              </View>
            );
          }

          if (layer.layerType === 'overlay') {
            return (
              <View
                key={layer.id}
                style={[
                  styles.overlayContainer,
                  {
                    top: `${rt.y}%`,
                    left: `${rt.x}%`,
                    opacity: rt.opacity,
                    zIndex: layer.zIndex,
                    transform: [
                      { translateX: -50 },
                      { translateY: -50 },
                      { scale: rt.scale },
                      { rotate: `${rt.rotation}deg` },
                      { scaleX: rt.flipH ? -1 : 1 },
                      { scaleY: rt.flipV ? -1 : 1 },
                    ],
                  },
                ]}
              >
                <Image source={{ uri: layer.uri }} style={{ width: 200, height: 200 }} contentFit="contain" />
              </View>
            );
          }
          return null;
        })}

        {subtitles.map(s => {
          if (!s.enabled) return null;
          const isVisible = currentTime >= s.startTime && currentTime < s.endTime;
          if (!isVisible) return null;
          return (
            <View
              key={s.id}
              style={[
                styles.subtitleOverlay,
                { top: `${subtitleStyle.positionY}%` },
                subtitleStyle.backgroundEnabled && {
                  backgroundColor: subtitleStyle.backgroundColor,
                  opacity: subtitleStyle.backgroundOpacity,
                  paddingHorizontal: 8,
                  borderRadius: 4,
                },
              ]}
            >
              <Text
                style={{
                  color: subtitleStyle.color,
                  fontSize: subtitleStyle.fontSize / 2.5,
                  textAlign: 'center',
                  fontWeight: 'bold',
                  textShadowColor: subtitleStyle.strokeColor,
                  textShadowRadius: subtitleStyle.strokeWidth,
                }}
              >
                {s.text}
              </Text>
            </View>
          );
        })}
      </View>

      <View style={styles.previewTopRow}>
        <View style={styles.historyBtns}>
          <Pressable
            hitSlop={8}
            onPress={onUndo}
            disabled={historyIndex <= 0}
            style={[styles.roundBtn, historyIndex <= 0 && { opacity: 0.3 }]}
          >
            <Ionicons name="arrow-undo" size={16} color={colors.textPrimary} />
          </Pressable>
          <Pressable
            hitSlop={8}
            onPress={onRedo}
            disabled={historyIndex >= historyLength - 1}
            style={[styles.roundBtn, historyIndex >= historyLength - 1 && { opacity: 0.3 }]}
          >
            <Ionicons name="arrow-redo" size={16} color={colors.textPrimary} />
          </Pressable>
        </View>
      </View>

      <Pressable onPress={onTogglePlay} style={styles.playCenter}>
        <Ionicons
          name={isPlaying ? 'pause' : 'play'}
          size={26}
          color={colors.onPrimary}
          style={!isPlaying && { marginLeft: 3 }}
        />
      </Pressable>

      <View style={styles.timeRow}>
        <Text style={styles.timeText}>
          {formatTime(currentTime)} / {formatTime(duration)}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  preview: {
    flex: 1,
    backgroundColor: '#000000',
    justify: 'center',
    alignItems: 'center',
    position: 'relative',
    padding: 20,
  },
  canvasContainer: {
    width: '100%',
    maxHeight: '100%',
    backgroundColor: '#111',
    justifyContent: 'center',
    alignItems: 'center',
    overflow: 'hidden',
  },
  textOverlay: {
    position: 'absolute',
    transform: [{ translateX: -50 }, { translateY: -50 }],
    alignItems: 'center',
    justifyContent: 'center',
  },
  overlayContainer: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  subtitleOverlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 15,
  },
  previewTopRow: {
    position: 'absolute',
    top: spacing.md,
    left: spacing.lg,
    right: spacing.lg,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    zIndex: 10,
  },
  historyBtns: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  roundBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playCenter: {
    position: 'absolute',
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
  },
  timeRow: {
    position: 'absolute',
    bottom: spacing.md,
    right: spacing.lg,
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  timeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontVariant: ['tabular-nums'],
    ...androidFont,
  },
});
