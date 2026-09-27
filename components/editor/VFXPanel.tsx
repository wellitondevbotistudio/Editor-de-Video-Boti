// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Slider, Button } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, VFXEffect } from '@/services/editorState';
import { getInterpolatedVFXIntensity } from '@/services/animationUtils';

interface Props {
  onAction: (msg: string) => void;
  selectedClipId?: string | null;
  timelinePosition: number;
}

const AVAILABLE_EFFECTS = [
  { type: 'blur', name: 'Blur', icon: 'blur-outline' },
  { type: 'glow', name: 'Glow', icon: 'sunny-outline' },
  { type: 'vignette', name: 'Vignette', icon: 'contrast-outline' },
];

export function VFXPanel({ onAction, selectedClipId, timelinePosition }: Props) {
  const { config, updateClipEffects, addVFXKeyframe, deleteVFXKeyframe, clearVFXAnimation } = useEditorStore();

  const selectedClip = config.clips.find(c => c.id === selectedClipId);
  const activeEffects = selectedClip?.effects || [];
  const localTime = selectedClip ? Math.max(0, timelinePosition - selectedClip.startTime) : 0;

  const addEffect = (type: string) => {
    if (!selectedClipId) return;
    const newEffect: VFXEffect = {
      id: `vfx_${Date.now()}`,
      type,
      enabled: true,
      intensity: 50,
      keyframes: []
    };
    updateClipEffects(selectedClipId, [...activeEffects, newEffect]);
    onAction(`Efeito ${type} adicionado`);
  };

  const removeEffect = (id: string) => {
    if (!selectedClipId) return;
    updateClipEffects(selectedClipId, activeEffects.filter(e => e.id !== id));
  };

  const updateEffect = (id: string, partial: Partial<VFXEffect>) => {
    if (!selectedClipId) return;
    const updated = activeEffects.map(e => e.id === id ? { ...e, ...partial } : e);
    updateClipEffects(selectedClipId, updated);
  };

  const toggleKeyframe = (vfxId: string, currentVal: number) => {
    if (!selectedClip) return;
    const effect = activeEffects.find(e => e.id === vfxId);
    if (!effect) return;
    const existing = (effect.keyframes || []).find(k => Math.abs(k.time - localTime) < 50);
    if (existing) {
      deleteVFXKeyframe(selectedClip.id, vfxId, existing.id);
    } else {
      addVFXKeyframe(selectedClip.id, vfxId, localTime, currentVal);
    }
  };

  const hasKeyframe = (vfxId: string) => {
    const effect = activeEffects.find(e => e.id === vfxId);
    return (effect?.keyframes || []).some(k => Math.abs(k.time - localTime) < 50);
  };

  if (!selectedClip) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um clipe para adicionar efeitos visuais.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>Efeitos Aplicados</Text>

      {activeEffects.length === 0 ? (
        <View style={styles.noneBox}>
          <Text style={styles.noneText}>Nenhum efeito aplicado a este clipe.</Text>
        </View>
      ) : (
        activeEffects.map((effect) => (
          <View key={effect.id} style={styles.effectCard}>
            <View style={styles.cardHeader}>
              <Ionicons
                name={AVAILABLE_EFFECTS.find(ae => ae.type === effect.type)?.icon as any || 'flash-outline'}
                size={20}
                color={effect.enabled ? colors.primaryVariant : colors.textTertiary}
              />
              <Text style={[styles.effectName, !effect.enabled && { color: colors.textTertiary }]}>
                {AVAILABLE_EFFECTS.find(ae => ae.type === effect.type)?.name || effect.type}
              </Text>

              <Pressable onPress={() => clearVFXAnimation(selectedClip.id, effect.id)} style={styles.actionBtn}>
                <Ionicons name="flash-off-outline" size={18} color={colors.textTertiary} />
              </Pressable>

              <Pressable onPress={() => updateEffect(effect.id, { enabled: !effect.enabled })} style={styles.actionBtn}>
                <Ionicons
                  name={effect.enabled ? "eye-outline" : "eye-off-outline"}
                  size={20}
                  color={effect.enabled ? colors.primary : colors.textTertiary}
                />
              </Pressable>
              <Pressable onPress={() => removeEffect(effect.id)} style={styles.actionBtn}>
                <Ionicons name="trash-outline" size={20} color={colors.danger} />
              </Pressable>
            </View>

            {effect.enabled && (
              <View style={styles.sliderBox}>
                <View style={styles.sliderLabelRow}>
                  <Text style={styles.sliderLabel}>Intensidade</Text>
                  <Pressable onPress={() => toggleKeyframe(effect.id, getInterpolatedVFXIntensity(effect, selectedClip.startTime, timelinePosition))}>
                    <Ionicons
                      name={hasKeyframe(effect.id) ? "diamond" : "diamond-outline"}
                      size={16}
                      color={hasKeyframe(effect.id) ? colors.primaryVariant : colors.textTertiary}
                    />
                  </Pressable>
                </View>
                <Slider
                  value={getInterpolatedVFXIntensity(effect, selectedClip.startTime, timelinePosition)}
                  min={0}
                  max={100}
                  onChange={(v) => updateEffect(effect.id, { intensity: v })}
                  showValue
                  suffix="%"
                />
              </View>
            )}
          </View>
        ))
      )}

      <Text style={styles.sectionTitle}>Adicionar Novo</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.addList}>
        {AVAILABLE_EFFECTS.map((ae) => (
          <Pressable
            key={ae.type}
            onPress={() => addEffect(ae.type)}
            style={styles.addCard}
          >
            <View style={styles.addIconCircle}>
              <Ionicons name={ae.icon as any} size={24} color={colors.primaryVariant} />
            </View>
            <Text style={styles.addLabel}>{ae.name}</Text>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  sectionTitle: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, marginTop: spacing.lg, letterSpacing: 1, ...androidFont },
  noneBox: { padding: spacing.lg, backgroundColor: colors.surface, borderRadius: radius.md, alignItems: 'center', borderStyle: 'dashed', borderWidth: 1, borderColor: colors.borderStrong },
  noneText: { color: colors.textTertiary, fontSize: fontSize.sm, ...androidFont },
  effectCard: { backgroundColor: colors.surface, borderRadius: radius.md, padding: spacing.md, marginBottom: spacing.md, borderWidth: 1, borderColor: colors.border },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  effectName: { flex: 1, color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, ...androidFont },
  actionBtn: { padding: 4 },
  sliderBox: { marginTop: spacing.md, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.sm },
  sliderLabelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  sliderLabel: { color: colors.textSecondary, fontSize: 11, ...androidFont },
  addList: { gap: spacing.md, paddingVertical: spacing.xs },
  addCard: { width: 90, alignItems: 'center', gap: 8 },
  addIconCircle: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.surfaceElevated, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  addLabel: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.medium, ...androidFont },
});
