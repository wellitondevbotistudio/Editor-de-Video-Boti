// Powered by OnSpace.AI
// Design System — Editor de Vídeo

export const colors = {
  background: '#0D0D0D',
  backgroundElevated: '#141414',
  surface: '#1A1A1A',
  surfaceElevated: '#242424',
  surfaceHigh: '#2E2E2E',

  primary: '#7C3AED',
  primaryVariant: '#8A2BE2',
  primaryDark: '#5B21B6',
  primarySoft: 'rgba(124, 58, 237, 0.16)',
  onPrimary: '#FFFFFF',

  textPrimary: '#FFFFFF',
  textSecondary: '#A1A1AA',
  textTertiary: '#6B7280',

  border: '#2A2A2A',
  borderStrong: '#3A3A3A',

  success: '#22C55E',
  danger: '#EF4444',
  warning: '#F59E0B',
  info: '#3B82F6',

  track: '#333333',
  waveform: '#5B21B6',

  overlay: 'rgba(0,0,0,0.65)',
} as const;

export const gradients = {
  primary: ['#8A2BE2', '#7C3AED'] as const,
  primaryDeep: ['#7C3AED', '#5B21B6'] as const,
  premium: ['#8A2BE2', '#6D28D9', '#4C1D95'] as const,
  dark: ['#1A1A1A', '#0D0D0D'] as const,
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 28,
  pill: 999,
} as const;

export const fontSize = {
  xs: 11,
  sm: 13,
  md: 15,
  base: 16,
  lg: 18,
  xl: 22,
  xxl: 28,
  xxxl: 34,
} as const;

export const fontWeight = {
  regular: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
};

import { Platform } from 'react-native';

export const shadow = {
  card: Platform.select({
    ios: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 6 },
      shadowOpacity: 0.35,
      shadowRadius: 12,
    },
    android: {
      elevation: 6,
    },
    web: {
      boxShadow: '0 6px 12px rgba(0,0,0,0.35)',
    },
    default: {}
  }),
  glow: Platform.select({
    ios: {
      shadowColor: '#7C3AED',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.5,
      shadowRadius: 16,
    },
    android: {
      elevation: 10,
    },
    web: {
      boxShadow: '0 4px 16px rgba(124, 58, 237, 0.5)',
    },
    default: {}
  }),
};

export const theme = { colors, gradients, spacing, radius, fontSize, fontWeight, shadow };
