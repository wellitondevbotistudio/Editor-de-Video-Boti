// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface ScreenHeaderProps {
  title?: string;
  leftIcon?: keyof typeof Ionicons.glyphMap;
  onLeftPress?: () => void;
  rightNode?: React.ReactNode;
  centerTitle?: boolean;
}

export function ScreenHeader({ title, leftIcon, onLeftPress, rightNode, centerTitle }: ScreenHeaderProps) {
  return (
    <View style={styles.container}>
      <View style={styles.side}>
        {leftIcon ? (
          <Pressable onPress={onLeftPress} hitSlop={12} style={styles.iconBtn}>
            <Ionicons name={leftIcon} size={22} color={colors.textPrimary} />
          </Pressable>
        ) : null}
      </View>
      {title ? (
        <Text style={[styles.title, centerTitle && { textAlign: 'center' }]} numberOfLines={1}>
          {title}
        </Text>
      ) : (
        <View style={styles.fill} />
      )}
      <View style={[styles.side, { alignItems: 'flex-end' }]}>{rightNode}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    height: 52,
  },
  side: { minWidth: 44, justifyContent: 'center' },
  fill: { flex: 1 },
  title: { flex: 1, textAlign: 'center', color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.semibold, ...androidFont },
  iconBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -10 },
});
