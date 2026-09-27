// Powered by OnSpace.AI
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Slider } from '@/components';
import { transitions } from '@/services/mockData';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore } from '@/services/editorState';

interface Props {
  onAction: (msg: string) => void;
  selectedClipId?: string | null;
}

const TRANSITION_TYPES: Record<string, string> = {
  tr1: 'dissolve',
  tr2: 'fade',
  tr3: 'slideleft',
  tr4: 'slideright',
};

// Add a "None" transition to mockData or handle it here
const EXTENDED_TRANSITIONS = [
  { id: 'none', name: 'Nenhuma', icon: 'ban-outline' },
  ...transitions
];

export function TransitionPanel({ onAction, selectedClipId }: Props) {
  const { config, updateClipTransition } = useEditorStore();
  const [duration, setDuration] = useState(0.5);
  const [selectedType, setSelectedType] = useState('none');

  const selectedClip = config.clips.find(c => c.id === selectedClipId);

  useEffect(() => {
    if (selectedClip?.nextTransition) {
      const type = selectedClip.nextTransition.type;
      // Map back from internal type to ID
      const foundId = Object.keys(TRANSITION_TYPES).find(key => TRANSITION_TYPES[key] === type) || 'none';
      setSelectedType(foundId);
      setDuration(selectedClip.nextTransition.duration / 1000);
    } else {
      setSelectedType('none');
      setDuration(0.5);
    }
  }, [selectedClipId]);

  const handleApply = (id: string, durSec: number) => {
    if (!selectedClipId) return;

    const type = id === 'none' ? 'none' : TRANSITION_TYPES[id] || 'none';
    setSelectedType(id);

    updateClipTransition(selectedClipId, {
      type,
      duration: Math.round(durSec * 1000)
    });
  };

  if (!selectedClipId) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Selecione um clipe para definir a transição para o próximo.</Text>
      </View>
    );
  }

  // Hide for the last clip
  const isLastClip = config.clips[config.clips.length - 1]?.id === selectedClipId;
  if (isLastClip) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>Não é possível aplicar transição no último clipe.</Text>
      </View>
    );
  }

  return (
    <View>
      <Slider
        value={duration}
        min={0.2}
        max={2}
        step={0.1}
        onChange={(v) => {
          setDuration(v);
          handleApply(selectedType, v);
        }}
        label="Duração"
        showValue
        suffix="s"
      />

      <View style={styles.grid}>
        {EXTENDED_TRANSITIONS.map((t) => {
          const isSel = selectedType === t.id;
          return (
            <Pressable
              key={t.id}
              onPress={() => handleApply(t.id, duration)}
              style={styles.item}
            >
              <View style={[styles.iconBox, isSel && styles.iconBoxSelected]}>
                <Ionicons name={t.icon as any} size={26} color={isSel ? colors.primaryVariant : colors.textSecondary} />
              </View>
              <Text style={[styles.label, isSel && { color: colors.textPrimary }]}>{t.name}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  emptyText: { color: colors.textSecondary, textAlign: 'center', ...androidFont },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: spacing.lg },
  item: { width: '33.33%', alignItems: 'center', paddingVertical: spacing.md },
  iconBox: {
    width: 70,
    height: 70,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  iconBoxSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  label: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.medium, ...androidFont },
});
