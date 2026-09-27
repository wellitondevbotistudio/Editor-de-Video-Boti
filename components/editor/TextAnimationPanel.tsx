// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, TextAnimationMode, TextAnimationPreset } from '@/services/editorState';
import { Slider, SegmentedControl } from '@/components';

interface Props {
  onAction: (msg: string) => void;
  selectedTextId?: string | null;
}

const PRESETS: { id: TextAnimationPreset; name: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { id: 'none', name: 'Nenhum', icon: 'close-circle-outline' },
  { id: 'fade', name: 'Fade', icon: 'sunny-outline' },
  { id: 'slide_up', name: 'Slide Up', icon: 'arrow-up-outline' },
  { id: 'slide_down', name: 'Slide Down', icon: 'arrow-down-outline' },
  { id: 'zoom', name: 'Zoom', icon: 'add-circle-outline' },
  { id: 'pop', name: 'Pop', icon: 'flash-outline' },
];

export function TextAnimationPanel({ onAction, selectedTextId }: Props) {
  const { config, updateText } = useEditorStore();

  const selectedText = config.texts.find(t => t.id === selectedTextId);
  const anim = selectedText?.animation || { mode: 'full', presetIn: 'none', presetOut: 'none', duration: 500, delay: 100 };

  if (!selectedText) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um texto para animar.</Text>
      </View>
    );
  }

  const updateAnim = (partial: any) => {
    if (selectedTextId) {
      updateText(selectedTextId, { animation: { ...anim, ...partial } });
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>Modo de Animação</Text>
      <SegmentedControl
        options={['Inteiro', 'Palavra', 'Letra']}
        value={anim.mode === 'full' ? 'Inteiro' : anim.mode === 'word' ? 'Palavra' : 'Letra'}
        onChange={(v) => {
          const mode: TextAnimationMode = v === 'Inteiro' ? 'full' : v === 'Palavra' ? 'word' : 'letter';
          updateAnim({ mode });
        }}
      />

      <Text style={[styles.sectionTitle, { marginTop: spacing.lg }]}>Entrada</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.list}>
        {PRESETS.map(p => (
          <Pressable
            key={p.id}
            onPress={() => updateAnim({ presetIn: p.id })}
            style={[styles.presetCard, anim.presetIn === p.id && styles.presetCardActive]}
          >
            <Ionicons name={p.icon} size={20} color={anim.presetIn === p.id ? colors.primaryVariant : colors.textSecondary} />
            <Text style={[styles.presetLabel, anim.presetIn === p.id && styles.textActive]}>{p.name}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Text style={[styles.sectionTitle, { marginTop: spacing.lg }]}>Configurações de Tempo</Text>
      <Slider
        value={anim.duration}
        min={100}
        max={2000}
        step={50}
        onChange={(v) => updateAnim({ duration: v })}
        label="Duração Total"
        showValue
        suffix="ms"
      />
      <View style={{ height: spacing.md }} />
      <Slider
        value={anim.delay}
        min={0}
        max={500}
        step={10}
        onChange={(v) => updateAnim({ delay: v })}
        label="Delay entre Partes"
        showValue
        suffix="ms"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  list: { gap: spacing.sm, paddingBottom: 4 },
  presetCard: { width: 70, height: 70, backgroundColor: colors.surface, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1, borderColor: colors.border },
  presetCardActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  presetLabel: { fontSize: 10, color: colors.textSecondary, ...androidFont },
  textActive: { color: colors.primaryVariant, fontWeight: fontWeight.bold },
});
