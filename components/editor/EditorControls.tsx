import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface EditorControlsProps {
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  onTogglePlay: () => void;
  onSeekRelative: (deltaMs: number) => void;
}

function formatTime(millis: number) {
  const totalSeconds = Math.floor(Math.max(0, millis) / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export function EditorControls({
  isPlaying,
  currentTime,
  duration,
  onTogglePlay,
  onSeekRelative,
}: EditorControlsProps) {
  return (
    <View style={styles.container}>
      <Pressable hitSlop={10} onPress={() => onSeekRelative(-5000)} style={styles.btn}>
        <Ionicons name="refresh-back" size={20} color={colors.textPrimary} />
      </Pressable>

      <Pressable hitSlop={10} onPress={onTogglePlay} style={styles.playBtn}>
        <Ionicons
          name={isPlaying ? 'pause' : 'play'}
          size={24}
          color={colors.onPrimary}
          style={!isPlaying && { marginLeft: 2 }}
        />
      </Pressable>

      <Pressable hitSlop={10} onPress={() => onSeekRelative(5000)} style={styles.btn}>
        <Ionicons name="refresh-forward" size={20} color={colors.textPrimary} />
      </Pressable>

      <Text style={styles.timeText}>
        {formatTime(currentTime)} / {formatTime(duration)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.md,
    paddingVertical: spacing.xs,
  },
  btn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.surfaceElevated,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  timeText: {
    color: colors.textSecondary,
    fontSize: fontSize.sm,
    fontWeight: fontWeight.medium,
    marginLeft: spacing.sm,
    fontVariant: ['tabular-nums'],
    ...androidFont,
  },
});
