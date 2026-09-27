// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { SegmentedControl, Slider } from '@/components';
import { effects, effectCategories } from '@/services/mockData';
import { colors, radius, spacing, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface Props {
  onAction: (msg: string) => void;
  onPremium: () => void;
}

export function EffectsPanel({ onAction, onPremium }: Props) {
  const [cat, setCat] = useState('Tudo');
  const [selected, setSelected] = useState<string | null>(null);
  const [intensity, setIntensity] = useState(50);

  const list = effects.filter((e) => cat === 'Tudo' || e.category === cat);

  return (
    <View>
      <SegmentedControl options={effectCategories} value={cat} onChange={setCat} scrollable />

      <View style={styles.grid}>
        {list.map((e) => {
          const isSel = selected === e.id;
          return (
            <Pressable
              key={e.id}
              onPress={() => {
                if (e.premium) {
                  onPremium();
                  return;
                }
                setSelected(e.id);
                onAction(`Efeito "${e.name}" aplicado`);
              }}
              style={[styles.cell, isSel && styles.cellSelected]}
            >
              <Image source={{ uri: e.thumb }} style={styles.img} contentFit="cover" transition={150} />
              {e.premium ? (
                <View style={styles.lock}>
                  <Ionicons name="lock-closed" size={12} color={colors.onPrimary} />
                </View>
              ) : null}
              <View style={styles.nameWrap}>
                <Text style={styles.name} numberOfLines={1}>{e.name}</Text>
              </View>
            </Pressable>
          );
        })}
      </View>

      {selected ? (
        <View style={styles.sliderWrap}>
          <Slider value={intensity} min={0} max={100} onChange={setIntensity} label="Intensidade" showValue suffix="%" />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.md },
  cell: {
    width: '31.5%',
    aspectRatio: 0.9,
    borderRadius: radius.md,
    overflow: 'hidden',
    backgroundColor: colors.surface,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  cellSelected: { borderColor: colors.primary },
  img: { width: '100%', height: '100%' },
  lock: { position: 'absolute', top: 6, right: 6, width: 22, height: 22, borderRadius: 11, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  nameWrap: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.55)', paddingVertical: 5, alignItems: 'center' },
  name: { color: colors.textPrimary, fontSize: 11, fontWeight: fontWeight.medium, ...androidFont },
  sliderWrap: { marginTop: spacing.lg, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.lg },
});
