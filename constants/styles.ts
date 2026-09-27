// Powered by OnSpace.AI
import { StyleSheet, Platform } from 'react-native';
import { colors, spacing, radius, fontSize, fontWeight } from './theme';

export const androidFont = Platform.OS === 'android' ? { includeFontPadding: false } : {};

export const commonStyles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.background,
  },
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  center: { alignItems: 'center', justifyContent: 'center' },
  title: {
    color: colors.textPrimary,
    fontSize: fontSize.xl,
    fontWeight: fontWeight.bold,
    ...androidFont,
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontSize: fontSize.lg,
    fontWeight: fontWeight.semibold,
    ...androidFont,
  },
  body: {
    color: colors.textSecondary,
    fontSize: fontSize.base,
    fontWeight: fontWeight.regular,
    lineHeight: 24,
    ...androidFont,
  },
  caption: {
    color: colors.textTertiary,
    fontSize: fontSize.sm,
    ...androidFont,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
  },
  divider: {
    height: 1,
    backgroundColor: colors.border,
  },
  chip: {
    height: 40,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceElevated,
  },
});
