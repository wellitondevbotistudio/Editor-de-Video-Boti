// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Slider, Button } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, KeyframeProperty } from '@/services/editorState';
import { getInterpolatedAdjustments } from '@/services/animationUtils';

interface Props {
  onAction: (msg: string) => void;
  selectedClipId?: string | null;
  timelinePosition: number;
}

const FILTERS = ['Original', 'Vívido', 'Frio', 'Quente', 'P&B', 'Filme', 'Retrô', 'Suave'];

export function ColorPanel({ onAction, selectedClipId, timelinePosition }: Props) {
  const { config, updateClipAdjustments, updateClipFilter, addKeyframe, deleteKeyframe } = useEditorStore();

  const selectedClip = config.clips.find(c => c.id === selectedClipId);

  if (!selectedClip) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um clipe para ajustar cores.</Text>
      </View>
    );
  }

  const localTime = Math.max(0, timelinePosition - selectedClip.startTime);
  const adjustments = getInterpolatedAdjustments(selectedClip, timelinePosition);

  const toggleKeyframe = (prop: KeyframeProperty, currentVal: number) => {
    const existing = (selectedClip.keyframes || []).find(k => k.property === prop && Math.abs(k.time - localTime) < 50);
    if (existing) {
      deleteKeyframe(selectedClip.id, existing.id);
    } else {
      addKeyframe(selectedClip.id, localTime, prop, currentVal);
    }
  };

  const hasKeyframe = (prop: KeyframeProperty) => {
    return (selectedClip.keyframes || []).some(k => k.property === prop && Math.abs(k.time - localTime) < 50);
  };

  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.container}>
      <Text style={styles.sectionTitle}>Ajustes Dinâmicos</Text>

      <View style={styles.adjustmentItem}>
        <View style={styles.labelRow}>
          <Text style={styles.label}>Brilho</Text>
          <Pressable onPress={() => toggleKeyframe('brightness', adjustments.brightness)}>
             <Ionicons
                name={hasKeyframe('brightness') ? "diamond" : "diamond-outline"}
                size={16}
                color={hasKeyframe('brightness') ? colors.primaryVariant : colors.textTertiary}
             />
          </Pressable>
        </View>
        <Slider
            value={adjustments.brightness}
            min={-100} max={100}
            onChange={(v) => updateClipAdjustments(selectedClip.id, { brightness: v })}
            showValue
        />
      </View>

      <View style={styles.adjustmentItem}>
        <View style={styles.labelRow}>
          <Text style={styles.label}>Contraste</Text>
          <Pressable onPress={() => toggleKeyframe('contrast', adjustments.contrast)}>
             <Ionicons
                name={hasKeyframe('contrast') ? "diamond" : "diamond-outline"}
                size={16}
                color={hasKeyframe('contrast') ? colors.primaryVariant : colors.textTertiary}
             />
          </Pressable>
        </View>
        <Slider
            value={adjustments.contrast}
            min={-100} max={100}
            onChange={(v) => updateClipAdjustments(selectedClip.id, { contrast: v })}
            showValue
        />
      </View>

      <View style={styles.adjustmentItem}>
        <View style={styles.labelRow}>
          <Text style={styles.label}>Saturação</Text>
          <Pressable onPress={() => toggleKeyframe('saturation', adjustments.saturation)}>
             <Ionicons
                name={hasKeyframe('saturation') ? "diamond" : "diamond-outline"}
                size={16}
                color={hasKeyframe('saturation') ? colors.primaryVariant : colors.textTertiary}
             />
          </Pressable>
        </View>
        <Slider
            value={adjustments.saturation}
            min={-100} max={100}
            onChange={(v) => updateClipAdjustments(selectedClip.id, { saturation: v })}
            showValue
        />
      </View>

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Presets de Filtro</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterList}>
        {FILTERS.map(f => (
          <Pressable
            key={f}
            onPress={() => updateClipFilter(selectedClip.id, f)}
            style={[styles.filterCard, selectedClip.filter === f && styles.filterCardActive]}
          >
            <View style={[styles.filterThumb, { backgroundColor: colors.surfaceElevated }]} />
            <Text style={[styles.filterLabel, selectedClip.filter === f && { color: colors.primaryVariant }]}>{f}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Button
        title="Resetar Todos Ajustes"
        variant="ghost"
        onPress={() => updateClipAdjustments(selectedClip.id, { brightness: 0, contrast: 0, saturation: 0 })}
        style={{ marginTop: spacing.xl }}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.xl },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  adjustmentItem: { marginBottom: spacing.lg },
  labelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  label: { color: colors.textSecondary, fontSize: 12, ...androidFont },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.lg },
  filterList: { gap: spacing.md },
  filterCard: { width: 80, alignItems: 'center', gap: 6 },
  filterThumb: { width: 60, height: 60, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.border },
  filterCardActive: { opacity: 1 },
  filterLabel: { fontSize: 11, color: colors.textTertiary, ...androidFont },
});
