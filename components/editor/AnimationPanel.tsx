// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, KeyframeProperty } from '@/services/editorState';
import { Slider, Button } from '@/components';

interface Props {
  onAction: (msg: string) => void;
  selectedLayerId?: string | null;
}

const PRESETS_IN = [
  { id: 'fade_in', name: 'Fade In', icon: 'sunny-outline' },
  { id: 'slide_left', name: 'Slide Left', icon: 'arrow-back-outline' },
  { id: 'slide_right', name: 'Slide Right', icon: 'arrow-forward-outline' },
  { id: 'zoom_in', name: 'Zoom In', icon: 'add-circle-outline' },
];

const PRESETS_OUT = [
  { id: 'fade_out', name: 'Fade Out', icon: 'moon-outline' },
  { id: 'slide_left_out', name: 'Slide Left', icon: 'arrow-back-outline' },
  { id: 'slide_right_out', name: 'Slide Right', icon: 'arrow-forward-outline' },
  { id: 'zoom_out', name: 'Zoom Out', icon: 'remove-circle-outline' },
];

export function AnimationPanel({ onAction, selectedLayerId }: Props) {
  const { config, addKeyframe, clearAnimation } = useEditorStore();
  const [inDuration, setInDuration] = useState(0.5);
  const [outDuration, setOutDuration] = useState(0.5);

  const selectedItem = config.clips.find(c => c.id === selectedLayerId) || config.texts.find(t => t.id === selectedLayerId);

  if (!selectedItem) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um elemento para animar.</Text>
      </View>
    );
  }

  const applyIn = async (id: string) => {
    if (!selectedLayerId) return;
    const dur = Math.round(inDuration * 1000);
    const transform = (selectedItem as any).transform || { x: 50, y: 50, scale: 1, rotation: 0, opacity: 1 };

    switch (id) {
      case 'fade_in':
        await addKeyframe(selectedLayerId, 0, 'opacity', 0);
        await addKeyframe(selectedLayerId, dur, 'opacity', transform.opacity);
        break;
      case 'slide_left':
        await addKeyframe(selectedLayerId, 0, 'x', transform.x - 20);
        await addKeyframe(selectedLayerId, dur, 'x', transform.x);
        break;
      case 'slide_right':
        await addKeyframe(selectedLayerId, 0, 'x', transform.x + 20);
        await addKeyframe(selectedLayerId, dur, 'x', transform.x);
        break;
      case 'zoom_in':
        await addKeyframe(selectedLayerId, 0, 'scale', 0);
        await addKeyframe(selectedLayerId, dur, 'scale', transform.scale);
        break;
    }
    onAction('Animação de entrada aplicada');
  };

  const applyOut = async (id: string) => {
    if (!selectedLayerId) return;
    const dur = Math.round(outDuration * 1000);
    const itemDur = selectedItem.duration;
    const startOut = itemDur - dur;
    const transform = (selectedItem as any).transform || { x: 50, y: 50, scale: 1, rotation: 0, opacity: 1 };

    switch (id) {
      case 'fade_out':
        await addKeyframe(selectedLayerId, startOut, 'opacity', transform.opacity);
        await addKeyframe(selectedLayerId, itemDur, 'opacity', 0);
        break;
      case 'slide_left_out':
        await addKeyframe(selectedLayerId, startOut, 'x', transform.x);
        await addKeyframe(selectedLayerId, itemDur, 'x', transform.x - 20);
        break;
      case 'slide_right_out':
        await addKeyframe(selectedLayerId, startOut, 'x', transform.x);
        await addKeyframe(selectedLayerId, itemDur, 'x', transform.x + 20);
        break;
      case 'zoom_out':
        await addKeyframe(selectedLayerId, startOut, 'scale', transform.scale);
        await addKeyframe(selectedLayerId, itemDur, 'scale', 0);
        break;
    }
    onAction('Animação de saída aplicada');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>Entrada</Text>
      <View style={styles.durRow}>
        <Text style={styles.label}>Duração: {inDuration.toFixed(1)}s</Text>
        <Slider value={inDuration} min={0.2} max={2.0} step={0.1} onChange={setInDuration} />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.list}>
        {PRESETS_IN.map(p => (
          <Pressable key={p.id} onPress={() => applyIn(p.id)} style={styles.card}>
            <View style={styles.iconCircle}>
              <Ionicons name={p.icon as any} size={22} color={colors.primaryVariant} />
            </View>
            <Text style={styles.cardLabel}>{p.name}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Text style={[styles.sectionTitle, { marginTop: spacing.lg }]}>Saída</Text>
      <View style={styles.durRow}>
        <Text style={styles.label}>Duração: {outDuration.toFixed(1)}s</Text>
        <Slider value={outDuration} min={0.2} max={2.0} step={0.1} onChange={setOutDuration} />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.list}>
        {PRESETS_OUT.map(p => (
          <Pressable key={p.id} onPress={() => applyOut(p.id)} style={styles.card}>
            <View style={styles.iconCircle}>
              <Ionicons name={p.icon as any} size={22} color={colors.textSecondary} />
            </View>
            <Text style={styles.cardLabel}>{p.name}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Button
        title="Limpar Todas Animações"
        variant="ghost"
        onPress={() => clearAnimation(selectedLayerId!)}
        style={{ marginTop: spacing.xl }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  durRow: { marginBottom: spacing.md },
  label: { color: colors.textSecondary, fontSize: 12, marginBottom: 8, ...androidFont },
  list: { gap: spacing.md },
  card: { width: 80, alignItems: 'center', gap: 8 },
  iconCircle: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.surfaceElevated, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  cardLabel: { color: colors.textSecondary, fontSize: 10, textAlign: 'center', ...androidFont },
});
