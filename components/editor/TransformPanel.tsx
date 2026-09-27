// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, Transform, KeyframeProperty } from '@/services/editorState';
import { Slider, Button } from '@/components';

interface Props {
  onAction: (msg: string) => void;
  selectedLayerId?: string | null;
  timelinePosition: number;
}

export function TransformPanel({ onAction, selectedLayerId, timelinePosition }: Props) {
  const { config, updateClipTransform, addKeyframe, deleteKeyframe } = useEditorStore();

  const selectedClip = config.clips.find(c => c.id === selectedLayerId);
  const selectedText = config.texts.find(t => t.id === selectedLayerId);
  const item = selectedClip || selectedText;
  const transform = item?.transform;

  if (!item || !transform) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione uma imagem, vídeo ou texto para transformar.</Text>
      </View>
    );
  }

  // Calculate local time for keyframe placement
  const localTime = Math.max(0, timelinePosition - item.startTime);

  const handleUpdate = (partial: Partial<Transform>) => {
    if (selectedLayerId) {
      updateClipTransform(selectedLayerId, partial);
    }
  };

  const toggleKeyframe = (prop: KeyframeProperty, currentVal: number) => {
    if (!selectedLayerId) return;
    const existing = (item.keyframes || []).find(k => k.property === prop && Math.abs(k.time - localTime) < 50);
    if (existing) {
      deleteKeyframe(selectedLayerId, existing.id);
    } else {
      addKeyframe(selectedLayerId, localTime, prop, currentVal);
    }
  };

  const hasKeyframe = (prop: KeyframeProperty) => {
    return (item.keyframes || []).some(k => k.property === prop && Math.abs(k.time - localTime) < 50);
  };

  const reset = () => {
    handleUpdate({ x: 50, y: 50, scale: 1, rotation: 0, opacity: 1 });
    onAction('Transformação resetada');
  };

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Text style={styles.sectionTitle}>Posição</Text>
        <Ionicons name="flash-outline" size={14} color={colors.textTertiary} />
      </View>

      <View style={styles.row}>
        <View style={{ flex: 1 }}>
          <View style={styles.sliderLabelRow}>
            <Text style={styles.sliderLabel}>Eixo X</Text>
            <Pressable onPress={() => toggleKeyframe('x', transform.x)}>
              <Ionicons name={hasKeyframe('x') ? "diamond" : "diamond-outline"} size={16} color={hasKeyframe('x') ? colors.primaryVariant : colors.textTertiary} />
            </Pressable>
          </View>
          <Slider value={transform.x} min={0} max={100} onChange={(v) => handleUpdate({ x: v })} showValue suffix="%" />
        </View>
        <View style={{ width: spacing.lg }} />
        <View style={{ flex: 1 }}>
          <View style={styles.sliderLabelRow}>
            <Text style={styles.sliderLabel}>Eixo Y</Text>
            <Pressable onPress={() => toggleKeyframe('y', transform.y)}>
              <Ionicons name={hasKeyframe('y') ? "diamond" : "diamond-outline"} size={16} color={hasKeyframe('y') ? colors.primaryVariant : colors.textTertiary} />
            </Pressable>
          </View>
          <Slider value={transform.y} min={0} max={100} onChange={(v) => handleUpdate({ y: v })} showValue suffix="%" />
        </View>
      </View>

      <View style={[styles.headerRow, { marginTop: spacing.lg }]}>
        <Text style={styles.sectionTitle}>Escala & Rotação</Text>
      </View>

      <View style={styles.sliderWithKeyframe}>
        <View style={styles.sliderLabelRow}>
          <Text style={styles.sliderLabel}>Tamanho</Text>
          <Pressable onPress={() => toggleKeyframe('scale', transform.scale)}>
             <Ionicons name={hasKeyframe('scale') ? "diamond" : "diamond-outline"} size={16} color={hasKeyframe('scale') ? colors.primaryVariant : colors.textTertiary} />
          </Pressable>
        </View>
        <Slider value={transform.scale * 100} min={10} max={400} onChange={(v) => handleUpdate({ scale: v / 100 })} showValue suffix="%" />
      </View>

      <View style={[styles.sliderWithKeyframe, { marginTop: spacing.md }]}>
        <View style={styles.sliderLabelRow}>
          <Text style={styles.sliderLabel}>Rotação</Text>
          <Pressable onPress={() => toggleKeyframe('rotation', transform.rotation)}>
             <Ionicons name={hasKeyframe('rotation') ? "diamond" : "diamond-outline"} size={16} color={hasKeyframe('rotation') ? colors.primaryVariant : colors.textTertiary} />
          </Pressable>
        </View>
        <Slider value={transform.rotation} min={0} max={360} onChange={(v) => handleUpdate({ rotation: v })} showValue suffix="°" />
      </View>

      <View style={[styles.headerRow, { marginTop: spacing.lg }]}>
        <Text style={styles.sectionTitle}>Visibilidade</Text>
      </View>

      <View style={styles.sliderWithKeyframe}>
        <View style={styles.sliderLabelRow}>
          <Text style={styles.sliderLabel}>Opacidade</Text>
          <Pressable onPress={() => toggleKeyframe('opacity', transform.opacity)}>
             <Ionicons name={hasKeyframe('opacity') ? "diamond" : "diamond-outline"} size={16} color={hasKeyframe('opacity') ? colors.primaryVariant : colors.textTertiary} />
          </Pressable>
        </View>
        <Slider value={transform.opacity * 100} min={0} max={100} onChange={(v) => handleUpdate({ opacity: v / 100 })} showValue suffix="%" />
      </View>

      <Button
        title="Resetar Transformação"
        variant="ghost"
        onPress={reset}
        style={{ marginTop: spacing.xl }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing.md },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', letterSpacing: 1, ...androidFont },
  row: { flexDirection: 'row', alignItems: 'center' },
  sliderLabelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  sliderLabel: { color: colors.textSecondary, fontSize: 11, ...androidFont },
  sliderWithKeyframe: { width: '100%' }
});
