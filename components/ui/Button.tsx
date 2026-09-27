// Powered by OnSpace.AI
import React from 'react';
import { Text, StyleSheet, Pressable, ActivityIndicator, View, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { colors, gradients, radius, spacing, fontSize, fontWeight, shadow } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

interface ButtonProps {
  title: string;
  onPress?: () => void;
  variant?: Variant;
  icon?: keyof typeof Ionicons.glyphMap;
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: ViewStyle;
}

export function Button({ title, onPress, variant = 'primary', icon, loading, disabled, fullWidth = true, style }: ButtonProps) {
  const isPrimary = variant === 'primary';
  const content = (
    <View style={styles.content}>
      {loading ? (
        <ActivityIndicator color={isPrimary ? colors.onPrimary : colors.textPrimary} />
      ) : (
        <>
          {icon ? <Ionicons name={icon} size={18} color={textColorFor(variant)} style={{ marginRight: spacing.sm }} /> : null}
          <Text style={[styles.text, { color: textColorFor(variant) }]}>{title}</Text>
        </>
      )}
    </View>
  );

  return (
    <Pressable
      onPress={disabled || loading ? undefined : onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.base,
        fullWidth && { alignSelf: 'stretch' },
        variant === 'secondary' && styles.secondary,
        variant === 'ghost' && styles.ghost,
        variant === 'danger' && styles.danger,
        isPrimary && shadow.glow,
        disabled && styles.disabled,
        pressed && { opacity: 0.85, transform: [{ scale: 0.99 }] },
        style,
      ]}
    >
      {isPrimary ? (
        <LinearGradient colors={gradients.primary} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.gradient}>
          {content}
        </LinearGradient>
      ) : (
        content
      )}
    </Pressable>
  );
}

function textColorFor(variant: Variant) {
  if (variant === 'ghost') return colors.textSecondary;
  if (variant === 'danger') return colors.danger;
  return colors.textPrimary;
}

const styles = StyleSheet.create({
  base: {
    height: 52,
    borderRadius: radius.md,
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
  },
  gradient: { ...StyleSheet.absoluteFillObject, alignItems: 'center', justifyContent: 'center' },
  content: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.lg },
  text: { fontSize: fontSize.base, fontWeight: fontWeight.semibold, ...androidFont },
  secondary: { backgroundColor: colors.surfaceElevated, borderWidth: 1, borderColor: colors.borderStrong },
  ghost: { backgroundColor: 'transparent' },
  danger: { backgroundColor: 'rgba(239,68,68,0.12)', borderWidth: 1, borderColor: 'rgba(239,68,68,0.4)' },
  disabled: { opacity: 0.4 },
});
