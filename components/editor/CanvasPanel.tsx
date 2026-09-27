// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, AspectRatio } from '@/services/editorState';

interface Props {
  onAction: (msg: string) => void;
}

const RATIOS: { id: AspectRatio; name: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { id: '16:9', name: '16:9 (YouTube)', icon: 'desktop-outline' },
  { id: '9:16', name: '9:16 (TikTok)', icon: 'phone-portrait-outline' },
  { id: '1:1', name: '1:1 (Instagram)', icon: 'square-outline' },
  { id: '4:5', name: '4:5 (Post)', icon: 'tablet-portrait-outline' },
  { id: '4:3', name: '4:3 (TV)', icon: 'tv-outline' },
];

export function CanvasPanel({ onAction }: Props) {
  const { config, updateCanvas } = useEditorStore();
  const currentRatio = config.canvas.aspectRatio;

  const handleSelect = async (ratio: AspectRatio) => {
    await updateCanvas(ratio);
    onAction(`Proporção alterada para ${ratio}`);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Proporção do Projeto</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.grid}>
        {RATIOS.map((r) => {
          const isSel = currentRatio === r.id;
          return (
            <Pressable
              key={r.id}
              onPress={() => handleSelect(r.id)}
              style={styles.item}
            >
              <View style={[styles.iconBox, isSel && styles.iconBoxSelected]}>
                <Ionicons name={r.icon} size={28} color={isSel ? colors.primaryVariant : colors.textSecondary} />
              </View>
              <Text style={[styles.label, isSel && { color: colors.textPrimary }]}>{r.name}</Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  title: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.lg, letterSpacing: 1, ...androidFont },
  grid: { flexDirection: 'row', gap: spacing.md },
  item: { width: 100, alignItems: 'center', gap: 8 },
  iconBox: {
    width: 70,
    height: 70,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  iconBoxSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  label: { color: colors.textSecondary, fontSize: 10, textAlign: 'center', fontWeight: fontWeight.medium, ...androidFont },
});
