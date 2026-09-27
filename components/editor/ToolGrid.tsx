// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export interface GridItem {
  id: string;
  name: string;
  icon: keyof typeof Ionicons.glyphMap;
  danger?: boolean;
}

interface ToolGridProps {
  items: GridItem[];
  onSelect: (item: GridItem) => void;
  columns?: number;
}

export function ToolGrid({ items, onSelect, columns = 4 }: ToolGridProps) {
  return (
    <View style={styles.grid}>
      {items.map((item) => (
        <Pressable
          key={item.id}
          onPress={() => onSelect(item)}
          style={({ pressed }) => [styles.item, { width: `${100 / columns}%` }, pressed && { opacity: 0.7 }]}
        >
          <View style={[styles.iconBox, item.danger && styles.dangerBox]}>
            <Ionicons name={item.icon} size={22} color={item.danger ? colors.danger : colors.textPrimary} />
          </View>
          <Text style={[styles.label, item.danger && { color: colors.danger }]} numberOfLines={1}>
            {item.name}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  item: { alignItems: 'center', paddingVertical: spacing.md },
  iconBox: {
    width: 56,
    height: 56,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 6,
  },
  dangerBox: { backgroundColor: 'rgba(239,68,68,0.12)' },
  label: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.medium, ...androidFont },
});
