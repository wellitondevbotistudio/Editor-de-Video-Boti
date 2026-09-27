// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Slider, Button, SegmentedControl } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, LocalMediaItem } from '@/services/editorState';
import { globalToLocalTime } from '@/services/timelineService';

interface Props {
  onAction: (msg: string) => void;
  selectedClipId?: string | null;
  timelinePosition: number;
}

const SPEED_PRESETS = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 4.0];

export function EditPanel({ onAction, selectedClipId, timelinePosition }: Props) {
  const { config, updateClipEditing, freezeFrame, splitClip, deleteClip } = useEditorStore();

  const selectedClip = config.clips.find(c => c.id === selectedClipId);

  if (!selectedClip) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um clipe para editar.</Text>
      </View>
    );
  }

  const handleSpeed = (speed: number) => {
    updateClipEditing(selectedClip.id, { speed });
    onAction(`Velocidade alterada para ${speed}x`);
  };

  const toggleReverse = () => {
    updateClipEditing(selectedClip.id, { reverse: !selectedClip.reverse });
    onAction(selectedClip.reverse ? 'Reverse desativado' : 'Reverse ativado');
  };

  const toggleFlip = (dir: 'h' | 'v') => {
    const t = selectedClip.transform;
    const newTransform = dir === 'h' ? { flipH: !t.flipH } : { flipV: !t.flipV };
    useEditorStore.getState().updateClipTransform(selectedClip.id, newTransform);
  };

  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.container}>
      <Text style={styles.sectionTitle}>Velocidade</Text>
      <View style={styles.speedGrid}>
        {SPEED_PRESETS.map((s) => (
          <Pressable
            key={s}
            onPress={() => handleSpeed(s)}
            style={[styles.speedBtn, selectedClip.speed === s && styles.speedBtnActive]}
          >
            <Text style={[styles.speedText, selectedClip.speed === s && styles.speedTextActive]}>{s}x</Text>
          </Pressable>
        ))}
      </View>
      <Slider
        value={selectedClip.speed}
        min={0.1}
        max={10.0}
        step={0.1}
        onChange={handleSpeed}
        label="Personalizado"
        showValue
        suffix="x"
      />

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Orientação</Text>
      <View style={styles.row}>
        <Button
          title="Flip Horizontal"
          variant={selectedClip.transform.flipH ? 'primary' : 'secondary'}
          icon="swap-horizontal-outline"
          onPress={() => toggleFlip('h')}
          style={{ flex: 1 }}
        />
        <View style={{ width: spacing.md }} />
        <Button
          title="Flip Vertical"
          variant={selectedClip.transform.flipV ? 'primary' : 'secondary'}
          icon="swap-vertical-outline"
          onPress={() => toggleFlip('v')}
          style={{ flex: 1 }}
        />
      </View>

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Ferramentas</Text>
      <View style={styles.toolGrid}>
        <Pressable
            onPress={toggleReverse}
            style={[styles.toolCard, selectedClip.reverse && styles.toolCardActive]}
        >
          <Ionicons name="infinite-outline" size={24} color={selectedClip.reverse ? colors.primaryVariant : colors.textSecondary} />
          <Text style={styles.toolLabel}>Reverse</Text>
        </Pressable>

        <Pressable
            onPress={() => freezeFrame(selectedClip.id, timelinePosition)}
            style={styles.toolCard}
        >
          <Ionicons name="snow-outline" size={24} color={colors.textSecondary} />
          <Text style={styles.toolLabel}>Freeze</Text>
        </Pressable>

        <Pressable
            onPress={() => {
              const clipInfo = globalToLocalTime(timelinePosition, config.clips);
              const splitPoint = clipInfo.activeClip?.id === selectedClip.id ? clipInfo.clipElapsedTimelineMs : timelinePosition;
              splitClip(selectedClip.id, splitPoint);
            }}
            style={styles.toolCard}
        >
          <Ionicons name="cut-outline" size={24} color={colors.textSecondary} />
          <Text style={styles.toolLabel}>Dividir</Text>
        </Pressable>

        <Pressable
            onPress={() => deleteClip(selectedClip.id)}
            style={styles.toolCard}
        >
          <Ionicons name="trash-outline" size={24} color={colors.danger} />
          <Text style={[styles.toolLabel, { color: colors.danger }]}>Excluir</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.xl },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  speedGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: spacing.lg },
  speedBtn: { paddingVertical: 8, paddingHorizontal: 12, borderRadius: radius.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  speedBtnActive: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  speedText: { color: colors.textSecondary, fontSize: 12, fontWeight: '600' },
  speedTextActive: { color: colors.primaryVariant },
  row: { flexDirection: 'row' },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.lg },
  toolGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  toolCard: { width: '22%', aspectRatio: 1, backgroundColor: colors.surface, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1, borderColor: colors.border },
  toolCardActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  toolLabel: { fontSize: 10, color: colors.textSecondary, fontWeight: '500', ...androidFont },
});
