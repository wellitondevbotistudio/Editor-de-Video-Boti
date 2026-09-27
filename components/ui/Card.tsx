// Powered by OnSpace.AI
import React from 'react';
import { Pressable, StyleSheet, ViewStyle, View } from 'react-native';
import { colors, radius, spacing } from '@/constants/theme';

interface CardProps {
  children: React.ReactNode;
  onPress?: () => void;
  style?: ViewStyle;
  selected?: boolean;
  padded?: boolean;
}

export function Card({ children, onPress, style, selected, padded = true }: CardProps) {
  const inner = (
    <View style={[styles.card, padded && { padding: spacing.lg }, selected && styles.selected, style]}>{children}</View>
  );
  if (!onPress) return inner;
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [pressed && { opacity: 0.9, transform: [{ scale: 0.995 }] }]}>
      {inner}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: 'transparent',
    overflow: 'hidden',
  },
  selected: { borderColor: colors.primary },
});
