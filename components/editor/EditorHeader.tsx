import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface EditorHeaderProps {
  title?: string;
}

export function EditorHeader({ title }: EditorHeaderProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.topBar, { paddingTop: insets.top + spacing.sm }]}>
      <Pressable hitSlop={12} onPress={() => router.back()} style={styles.topBtn}>
        <Ionicons name="chevron-back" size={22} color={colors.textPrimary} />
        <Text style={styles.exitText}>Sair</Text>
      </Pressable>

      <Text style={styles.projectTitleHeader} numberOfLines={1}>
        {title || 'Editor'}
      </Text>

      <Pressable hitSlop={12} onPress={() => router.push('/export')} style={styles.exportBtn}>
        <Text style={styles.exportText}>Exportar</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    backgroundColor: colors.background,
  },
  topBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  exitText: {
    color: colors.textPrimary,
    fontSize: fontSize.md,
    ...androidFont,
  },
  projectTitleHeader: {
    color: colors.textPrimary,
    fontSize: fontSize.md,
    fontWeight: fontWeight.semibold,
    maxWidth: 150,
    ...androidFont,
  },
  exportBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
  },
  exportText: {
    color: colors.onPrimary,
    fontSize: fontSize.sm,
    fontWeight: fontWeight.semibold,
    ...androidFont,
  },
});
