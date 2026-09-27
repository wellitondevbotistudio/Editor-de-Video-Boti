// Powered by OnSpace.AI
import React, { useRef, useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, PanResponder } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { LocalMediaItem, LocalTextItem, SubtitleSegment, Keyframe, LocalAudioItem, useEditorStore, VFXEffect } from '@/services/editorState';
import { getWaveform, WaveformData } from '@/services/waveformService';

const PIXELS_PER_SECOND = 40; // 40px per second scaling

interface TimelineProps {
  clips: LocalMediaItem[];
  texts?: LocalTextItem[];
  subtitles?: SubtitleSegment[];
  selectedClipId: string | null;
  selectedTextId?: string | null;
  selectedSubtitleId?: string | null;
  selectedAudioId?: string | null;
  onSelectClip: (id: string | null) => void;
  onSelectText?: (id: string | null) => void;
  onSelectSubtitle?: (id: string | null) => void;
  onSelectAudio?: (id: string | null) => void;
  onSelectTransition?: (id: string) => void;
  position?: number;
  onSeek?: (millis: number) => void;
  onSeekBegin?: () => void;
  onSeekEnd?: () => void;
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onAdd?: () => void;
  onMoveLeft?: (index: number) => void;
  onMoveRight?: (index: number) => void;
  onTrimClip?: (id: string, startMs: number, endMs: number) => void;
}

function AudioWaveform({ audio, pixelsPerSecond }: { audio: LocalAudioItem; pixelsPerSecond: number }) {
  const [data, setData] = React.useState<WaveformData | null>(null);

  React.useEffect(() => {
    getWaveform(audio.uri, audio.originalDuration).then(setData);
  }, [audio.uri, audio.originalDuration]);

  if (!data) return <View style={styles.waveformPlaceholder} />;

  const startIdx = Math.floor((audio.trimStart / audio.originalDuration) * data.samples.length);
  const endIdx = Math.ceil((audio.trimEnd / audio.originalDuration) * data.samples.length);
  const visibleSamples = data.samples.slice(startIdx, endIdx);

  const width = (audio.duration / 1000) * pixelsPerSecond;
  const numBars = Math.floor(width / 3);
  const step = visibleSamples.length / Math.max(1, numBars);

  const bars = [];
  for (let i = 0; i < numBars; i++) {
    const sampleIdx = Math.floor(i * step);
    const val = visibleSamples[sampleIdx] || 0;
    bars.push(
      <View
        key={i}
        style={[
          styles.waveBar,
          { height: Math.max(2, val * 24) }
        ]}
      />
    );
  }

  return (
    <View style={styles.waveformContainer}>
      {bars}
    </View>
  );
}

const MemoizedAudioWaveform = React.memo(AudioWaveform);

function TimelineComponent({
  clips = [],
  texts = [],
  subtitles = [],
  selectedClipId,
  selectedTextId,
  selectedSubtitleId,
  selectedAudioId,
  onSelectClip,
  onSelectText,
  onSelectSubtitle,
  onSelectAudio,
  onSelectTransition,
  position = 0,
  onSeek,
  onSeekBegin,
  onSeekEnd,
  onZoomIn,
  onZoomOut,
  onAdd,
  onMoveLeft,
  onMoveRight,
  onTrimClip,
}: TimelineProps) {
  const scrollRef = useRef<ScrollView>(null);
  const isScrolling = useRef(false);
  const momentumTimeout = useRef<any>(null);

  const sequentialClips = clips.filter(c => !c.isOverlay);
  const overlayClips = clips.filter(c => c.isOverlay);

  const totalDurationMs = sequentialClips.reduce((acc, c, idx) => {
    let dur = c.duration || 3000;
    if (idx < sequentialClips.length - 1 && c.nextTransition && c.nextTransition.type !== 'none') {
      dur -= c.nextTransition.duration;
    }
    return acc + Math.max(100, dur);
  }, 0);
  const totalDurationSec = Math.max(15, Math.ceil(totalDurationMs / 1000));

  const rulerMarkers = useMemo(() => {
    const markers: string[] = [];
    for (let i = 0; i <= totalDurationSec; i += 3) {
      const min = Math.floor(i / 60).toString().padStart(2, '0');
      const sec = (i % 60).toString().padStart(2, '0');
      markers.push(`${min}:${sec}`);
    }
    return markers;
  }, [totalDurationSec]);

  useEffect(() => {
    if (!isScrolling.current && scrollRef.current) {
      const positionInSec = position / 1000;
      scrollRef.current.scrollTo({ x: positionInSec * PIXELS_PER_SECOND, animated: false });
    }
  }, [position]);

  const handleScroll = (event: any) => {
    if (!isScrolling.current) return;
    const x = event.nativeEvent.contentOffset.x;
    const positionInMs = (x / PIXELS_PER_SECOND) * 1000;
    if (onSeek) onSeek(Math.max(0, positionInMs));
  };

  const createTrimResponder = (clip: LocalMediaItem, side: 'left' | 'right') => {
    const originalDur = clip.originalDuration || clip.duration;
    const currentStart = clip.trimStart ?? 0;
    const currentEnd = clip.trimEnd ?? clip.duration;

    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (_, gestureState) => {
        const deltaMs = (gestureState.dx / PIXELS_PER_SECOND) * 1000;
        if (side === 'left') {
          let newStart = Math.max(0, currentStart + deltaMs);
          if (newStart > currentEnd - 500) newStart = currentEnd - 500;
          if (onTrimClip) onTrimClip(clip.id, Math.round(newStart), currentEnd);
        } else {
          let newEnd = Math.min(originalDur, currentEnd + deltaMs);
          if (newEnd < currentStart + 500) newEnd = currentStart + 500;
          if (onTrimClip) onTrimClip(clip.id, currentStart, Math.round(newEnd));
        }
      }
    });
  };

  const renderKeyframes = (keyframes: Keyframe[] | undefined, effects?: VFXEffect[]) => {
    const transformTimes = (keyframes || []).map(k => k.time);
    const vfxTimes = (effects || []).flatMap(e => (e.keyframes || []).map((k: any) => k.time));

    const uniqueTimes = Array.from(new Set([...transformTimes, ...vfxTimes]));
    if (uniqueTimes.length === 0) return null;

    return uniqueTimes.map(t => (
      <View
        key={t}
        style={[styles.keyframePoint, { left: (t / 1000) * PIXELS_PER_SECOND }]}
      >
        <Ionicons name="diamond" size={8} color="#FFF" />
      </View>
    ));
  };

  return (
    <View style={styles.container}>
      <View style={styles.controls}>
        <Pressable onPress={onZoomOut} hitSlop={8} style={styles.zoomBtn}><Ionicons name="remove" size={16} color={colors.textSecondary} /></Pressable>
        <Pressable onPress={onZoomIn} hitSlop={8} style={styles.zoomBtn}><Ionicons name="add" size={16} color={colors.textSecondary} /></Pressable>
        <View style={styles.fill} />
        <Pressable onPress={onAdd} hitSlop={8} style={styles.addBtn}><Ionicons name="add" size={18} color={colors.onPrimary} /></Pressable>
      </View>

      <View style={styles.tracksWrap}>
        <View style={styles.playhead} pointerEvents="none" />
        <ScrollView 
          ref={scrollRef}
          horizontal 
          showsHorizontalScrollIndicator={false} 
          contentContainerStyle={styles.tracksContent}
          scrollEventThrottle={16}
          onScroll={handleScroll}
          onScrollBeginDrag={() => {
            isScrolling.current = true;
            if (onSeekBegin) onSeekBegin();
          }}
          onScrollEndDrag={() => {
            // Se o scroll momentum não iniciar rapidamente, o usuário parou o dedo.
            momentumTimeout.current = setTimeout(() => {
              isScrolling.current = false;
              if (onSeekEnd) onSeekEnd();
            }, 50);
          }}
          onMomentumScrollBegin={() => {
            clearTimeout(momentumTimeout.current);
            isScrolling.current = true;
          }}
          onMomentumScrollEnd={() => {
            isScrolling.current = false;
            if (onSeekEnd) onSeekEnd();
          }}
        >
          <View style={styles.tracksStack}>
            {/* Ruler */}
            <View style={styles.ruler}>
              {rulerMarkers.map((t, idx) => (
                <View key={idx} style={[styles.rulerMark, { left: idx * 3 * PIXELS_PER_SECOND }]}>
                  <Text style={styles.rulerText}>{t}</Text>
                  <View style={styles.rulerTick} />
                </View>
              ))}
            </View>

            {/* Video Track (Main Sequence) */}
            <View style={styles.videoTrack}>
              {sequentialClips.length === 0 ? (
                <Pressable onPress={onAdd} style={styles.emptyTrackPrompt}>
                  <Text style={styles.emptyTrackText}>Nenhuma mídia principal adicionada.</Text>
                </Pressable>
              ) : (
                sequentialClips.map((c, i) => {
                  const hasTransition = c.nextTransition && c.nextTransition.type !== 'none' && i < sequentialClips.length - 1;
                  const transitionWidth = hasTransition ? (c.nextTransition!.duration / 1000) * PIXELS_PER_SECOND : 0;
                  const clipWidth = ((c.duration || 3000) / 1000) * PIXELS_PER_SECOND - transitionWidth;
                  const isSelected = selectedClipId === c.id;
                  const isHidden = !c.visible;

                  return (
                    <View key={c.id} style={[styles.clipContainer, { width: Math.max(40, clipWidth) + (isSelected ? 24 : 0) }]}>
                      {isSelected && !c.locked && (
                        <View style={styles.trimHandleLeft} {...createTrimResponder(c, 'left').panHandlers}>
                          <View style={styles.trimBarIndicator} />
                        </View>
                      )}
                      <Pressable
                        onPress={() => onSelectClip(isSelected ? null : c.id)}
                        style={[
                          styles.clip,
                          { flex: 1 },
                          isSelected && styles.clipSelected,
                          isHidden && { opacity: 0.4 }
                        ]}
                      >
                        <Image source={{ uri: c.uri }} style={styles.clipImg} contentFit="cover" />
                        <View style={styles.clipStatusRow}>
                          {c.locked && <Ionicons name="lock-closed" size={10} color={colors.primaryVariant} />}
                          {!c.visible && <Ionicons name="eye-off" size={10} color={colors.textTertiary} />}
                          {c.effects && c.effects.length > 0 && <Ionicons name="flash" size={10} color={colors.primaryVariant} />}
                          {c.keyframes && c.keyframes.length > 0 && <Ionicons name="diamond" size={10} color="#FFF" />}
                        </View>

                        {isSelected && !c.locked && (
                          <View style={styles.reorderOverlay}>
                            {i > 0 && (
                              <Pressable style={styles.reorderBtn} onPress={() => onMoveLeft && onMoveLeft(i)}>
                                <Ionicons name="arrow-back" size={14} color="#FFF" />
                              </Pressable>
                            )}
                            {i < sequentialClips.length - 1 && (
                              <Pressable style={styles.reorderBtn} onPress={() => onMoveRight && onMoveRight(i)}>
                                <Ionicons name="arrow-forward" size={14} color="#FFF" />
                              </Pressable>
                            )}
                          </View>
                        )}

                        <View style={styles.clipOverlay}>
                          <Ionicons name={c.type === 'video' ? 'videocam' : 'image'} size={10} color={colors.onPrimary} />
                          <Text style={styles.clipDurationLabel} numberOfLines={1}>{((c.duration || 3000) / 1000).toFixed(1)}s</Text>
                        </View>

                        {isSelected && renderKeyframes(c.keyframes, c.effects)}
                      </Pressable>
                      {isSelected && !c.locked && (
                        <View style={styles.trimHandleRight} {...createTrimResponder(c, 'right').panHandlers}>
                          <View style={styles.trimBarIndicator} />
                        </View>
                      )}

                      {/* Transition Marker Absolute Overlay */}
                      {i < sequentialClips.length - 1 && (
                        <Pressable
                          onPress={() => onSelectTransition && onSelectTransition(c.id)}
                          style={[
                            styles.transitionMarker,
                            {
                              width: hasTransition ? transitionWidth : 16,
                              right: hasTransition ? -(transitionWidth / 2) : -8,
                              backgroundColor: hasTransition ? colors.primary : colors.surfaceElevated
                            }
                          ]}
                        >
                          <Ionicons name={hasTransition ? "flash" : "add"} size={12} color={hasTransition ? "#FFF" : colors.textTertiary} />
                        </Pressable>
                      )}
                    </View>
                  );
                })
              )}
            </View>

            {/* Overlay Track */}
            <View style={styles.overlayTrack}>
              {overlayClips.map((o) => {
                const left = (o.startTime / 1000) * PIXELS_PER_SECOND;
                const width = (o.duration / 1000) * PIXELS_PER_SECOND;
                const isSelected = selectedClipId === o.id;
                return (
                  <Pressable
                    key={o.id}
                    onPress={() => onSelectClip(isSelected ? null : o.id)}
                    style={[styles.overlayClip, { left, width, backgroundColor: isSelected ? colors.primaryVariant : colors.primarySoft, opacity: o.visible ? 1 : 0.4 }]}
                  >
                    <Image source={{ uri: o.uri }} style={{ width: 16, height: 16, borderRadius: 2, marginRight: 4 }} contentFit="cover" />
                    <Text style={styles.subtitleLabel} numberOfLines={1}>{o.name}</Text>
                    {isSelected && renderKeyframes(o.keyframes, o.effects)}
                  </Pressable>
                );
              })}
            </View>

            {/* Subtitle Track */}
            <View style={styles.subtitleTrack}>
              {subtitles.map((s) => {
                const left = (s.startTime / 1000) * PIXELS_PER_SECOND;
                const width = ((s.endTime - s.startTime) / 1000) * PIXELS_PER_SECOND;
                const isSelected = selectedSubtitleId === s.id;
                return (
                  <Pressable
                    key={s.id}
                    onPress={() => onSelectSubtitle && onSelectSubtitle(isSelected ? null : s.id)}
                    style={[styles.subtitleClip, { left, width, backgroundColor: isSelected ? colors.primaryVariant : colors.primarySoft }]}
                  >
                    <Text style={styles.subtitleLabel} numberOfLines={1}>{s.text}</Text>
                  </Pressable>
                );
              })}
            </View>

            {/* Audio Track */}
            {clips.length > 0 && (
              <View style={styles.audioTrackLayer}>
                {(useEditorStore.getState().config.audios || []).map((a) => {
                  const left = (a.startTime / 1000) * PIXELS_PER_SECOND;
                  const width = (a.duration / 1000) * PIXELS_PER_SECOND;
                  const isSelected = selectedAudioId === a.id;
                  return (
                    <Pressable
                      key={a.id}
                      onPress={() => onSelectAudio && onSelectAudio(isSelected ? null : a.id)}
                      style={[
                        styles.audioClip,
                        { left, width },
                        isSelected && styles.audioClipSelected,
                        !a.visible && { opacity: 0.4 }
                      ]}
                    >
                      <Ionicons name="musical-note" size={10} color={colors.onPrimary} style={styles.audioIcon} />
                      <MemoizedAudioWaveform audio={a} pixelsPerSecond={PIXELS_PER_SECOND} />
                      {a.fadeIn > 0 && <View style={[styles.fadeIndicator, { width: (a.fadeIn / 1000) * PIXELS_PER_SECOND, left: 0 }]} />}
                      {a.fadeOut > 0 && <View style={[styles.fadeIndicator, { width: (a.fadeOut / 1000) * PIXELS_PER_SECOND, right: 0 }]} />}
                    </Pressable>
                  );
                })}
              </View>
            )}

            {/* Text Track */}
            <View style={styles.textTrack}>
              {texts.map((t) => {
                const textWidth = (t.duration / 1000) * PIXELS_PER_SECOND;
                const textLeft = (t.startTime / 1000) * PIXELS_PER_SECOND;
                const isSelected = selectedTextId === t.id;
                return (
                  <Pressable
                    key={t.id}
                    onPress={() => onSelectText && onSelectText(isSelected ? null : t.id)}
                    style={[
                      styles.textClip,
                      {
                        width: textWidth,
                        position: 'absolute',
                        left: textLeft,
                        borderColor: isSelected ? colors.primaryVariant : 'transparent',
                        borderWidth: isSelected ? 2 : 0,
                        opacity: t.visible ? 1 : 0.4
                      }
                    ]}
                  >
                    <Ionicons name="text" size={12} color={colors.onPrimary} />
                    <Text style={styles.textClipLabel} numberOfLines={1}>{t.text}</Text>
                    {isSelected && renderKeyframes(t.keyframes)}
                  </Pressable>
                );
              })}
            </View>
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

export const Timeline = React.memo(TimelineComponent);

const styles = StyleSheet.create({
  container: { paddingTop: spacing.sm },
  controls: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.lg, marginBottom: spacing.sm, gap: spacing.sm },
  fill: { flex: 1 },
  zoomBtn: { width: 30, height: 30, borderRadius: 8, backgroundColor: colors.surfaceElevated, alignItems: 'center', justifyContent: 'center' },
  addBtn: { width: 32, height: 32, borderRadius: 10, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  tracksWrap: { position: 'relative' },
  playhead: { position: 'absolute', left: '50%', top: 0, bottom: 0, width: 2, backgroundColor: '#FFFFFF', zIndex: 100, borderRadius: 1 },
  tracksContent: { paddingHorizontal: '50%' },
  tracksStack: { position: 'relative', minHeight: 230 },
  ruler: { height: 24, position: 'relative', marginBottom: 6 },
  rulerMark: { position: 'absolute', top: 0, width: 40, alignItems: 'flex-start' },
  rulerText: { color: colors.textTertiary, fontSize: 10, fontWeight: fontWeight.medium, ...androidFont },
  rulerTick: { width: 1, height: 4, backgroundColor: colors.textTertiary, marginTop: 2 },
  videoTrack: { flexDirection: 'row', gap: 0, marginBottom: 6, minHeight: 54, alignItems: 'center' },
  overlayTrack: { height: 24, position: 'relative', marginBottom: 6 },
  overlayClip: { position: 'absolute', height: 20, borderRadius: 4, paddingHorizontal: 4, flexDirection: 'row', alignItems: 'center' },
  emptyTrackPrompt: { paddingVertical: spacing.md, paddingHorizontal: spacing.xl, backgroundColor: colors.surface, borderRadius: 8, borderStyle: 'dashed', borderWidth: 1, borderColor: colors.borderStrong },
  emptyTrackText: { color: colors.textTertiary, fontSize: 12, ...androidFont },
  clipContainer: { flexDirection: 'row', height: 54, alignItems: 'stretch', position: 'relative' },
  trimHandleLeft: { width: 12, backgroundColor: colors.primaryVariant, borderTopLeftRadius: 6, borderBottomLeftRadius: 6, justifyContent: 'center', alignItems: 'center' },
  trimHandleRight: { width: 12, backgroundColor: colors.primaryVariant, borderTopRightRadius: 6, borderBottomRightRadius: 6, justifyContent: 'center', alignItems: 'center' },
  trimBarIndicator: { width: 2, height: 16, backgroundColor: '#FFF', borderRadius: 1 },
  clip: { borderRadius: 4, overflow: 'hidden', borderWidth: 1, borderColor: colors.borderStrong, backgroundColor: '#111', position: 'relative', justifyContent: 'center' },
  clipSelected: { borderColor: colors.primaryVariant, borderWidth: 1, borderRadius: 0 },
  clipImg: { width: '100%', height: '100%', opacity: 0.8 },
  clipStatusRow: { position: 'absolute', top: 4, left: 4, right: 4, flexDirection: 'row', gap: 4, zIndex: 10 },
  reorderOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.4)', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4, zIndex: 10 },
  reorderBtn: { width: 18, height: 18, borderRadius: 9, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  clipOverlay: { position: 'absolute', bottom: 4, left: 4, right: 4, flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: 'rgba(0,0,0,0.5)', paddingHorizontal: 4, paddingVertical: 2, borderRadius: 4, zIndex: 5 },
  clipDurationLabel: { color: colors.onPrimary, fontSize: 9, fontWeight: fontWeight.semibold, ...androidFont },
  transitionMarker: { height: 24, borderRadius: 4, alignItems: 'center', justifyContent: 'center', zIndex: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)', position: 'absolute', top: 15 },
  subtitleTrack: { height: 24, position: 'relative', marginBottom: 6 },
  subtitleClip: { position: 'absolute', height: 20, borderRadius: 4, paddingHorizontal: 4, justifyContent: 'center' },
  subtitleLabel: { color: colors.textPrimary, fontSize: 9, ...androidFont },
  audioTrackLayer: { height: 32, position: 'relative', marginBottom: 6 },
  audioClip: { position: 'absolute', height: 30, backgroundColor: colors.waveform, borderRadius: 6, overflow: 'hidden', flexDirection: 'row', alignItems: 'center' },
  audioClipSelected: { borderColor: colors.primaryVariant, borderWidth: 1.5 },
  audioIcon: { marginHorizontal: 4 },
  waveformContainer: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 1, paddingHorizontal: 4 },
  waveBar: { width: 2, backgroundColor: 'rgba(255,255,255,0.5)', borderRadius: 1 },
  waveformPlaceholder: { flex: 1, height: 2, backgroundColor: 'rgba(255,255,255,0.1)', marginHorizontal: 10 },
  fadeIndicator: { position: 'absolute', top: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.2)', borderLeftWidth: 1, borderRightWidth: 1, borderColor: 'rgba(255,255,255,0.2)' },
  textTrack: { flexDirection: 'row', height: 30, position: 'relative' },
  textClip: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: colors.primary, borderRadius: 6, paddingHorizontal: 8, height: 26 },
  textClipLabel: { color: colors.onPrimary, fontSize: 11, fontWeight: fontWeight.medium, ...androidFont },
  keyframePoint: { position: 'absolute', top: '50%', marginTop: -4, zIndex: 30 },
});
