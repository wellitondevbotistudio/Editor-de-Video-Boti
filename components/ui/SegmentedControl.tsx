// Powered by OnSpace.AI
import React from 'react';
import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface SegmentedControlProps {
  options: string[];
  value: string;
  onChange: (v: string) => void;
  scrollable?: boolean;
}

export function SegmentedControl({ options, value, onChange, scrollable }: SegmentedControlProps) {
  if (scrollable) {
    return (
      <View style={styles.chipOuter}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {options.map((opt) => {
            const selected = opt === value;
            return (
              <Pressable
                key={opt}
                onPress={() => onChange(opt)}
                style={({ pressed }) => [styles.chip, selected && styles.chipSelected, pressed && { opacity: 0.85 }]}
              >
                <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{opt}</Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>
    );
  }

  return (
    <View style={styles.segment}>
      {options.map((opt) => {
        const selected = opt === value;
        return (
          <Pressable key={opt} onPress={() => onChange(opt)} style={[styles.segmentItem, selected && styles.segmentItemSelected]}>
            <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{opt}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  segment: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 4,
  },
  segmentItem: {
    flex: 1,
    height: 40,
    borderRadius: radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentItemSelected: { backgroundColor: colors.primary },
  segmentText: { color: colors.textSecondary, fontSize: fontSize.sm, fontWeight: fontWeight.medium, ...androidFont },
  segmentTextSelected: { color: colors.onPrimary, fontWeight: fontWeight.semibold },

  chipOuter: { minHeight: 44 },
  chipRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingRight: spacing.lg },
  chip: {
    height: 40,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceElevated,
    borderWidth: 1,
    borderColor: colors.border,
  },
  chipSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.textSecondary, fontSize: fontSize.sm, fontWeight: fontWeight.medium, ...androidFont },
  chipTextSelected: { color: colors.onPrimary, fontWeight: fontWeight.semibold },
});
