// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAlert } from '@/template';
import { colors, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export default function PlayerScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { showAlert } = useAlert();
  const [playing, setPlaying] = useState(true);
  const [loop, setLoop] = useState(false);

  return (
    <View style={styles.container}>
      <Image source={{ uri: 'https://picsum.photos/seed/preview/800/1400' }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      <View style={styles.scrim} />

      <View style={[styles.topBar, { paddingTop: insets.top + spacing.sm }]}>
        <Pressable hitSlop={12} onPress={() => router.back()} style={styles.roundBtn}>
          <Ionicons name="chevron-down" size={24} color={colors.onPrimary} />
        </Pressable>
        <Pressable hitSlop={12} onPress={() => setLoop(!loop)} style={styles.roundBtn}>
          <Ionicons name={loop ? 'repeat' : 'repeat-outline'} size={22} color={loop ? colors.primaryVariant : colors.onPrimary} />
        </Pressable>
      </View>

      <Pressable style={styles.center} onPress={() => setPlaying(!playing)}>
        <View style={styles.playBig}>
          <Ionicons name={playing ? 'pause' : 'play'} size={34} color={colors.onPrimary} style={!playing && { marginLeft: 4 }} />
        </View>
      </Pressable>

      <View style={[styles.bottom, { paddingBottom: insets.bottom + spacing.lg }]}>
        <View style={styles.progressRow}>
          <Text style={styles.time}>00:06</Text>
          <View style={styles.progressTrack}>
            <View style={styles.progressFill} />
            <View style={styles.progressThumb} />
          </View>
          <Text style={styles.time}>00:15</Text>
        </View>

        <View style={styles.actions}>
          <Pressable style={styles.actionBtn} onPress={() => showAlert('Compartilhar', 'Fluxo simulado.')}>
            <Ionicons name="share-social-outline" size={20} color={colors.onPrimary} />
            <Text style={styles.actionText}>Compartilhar</Text>
          </Pressable>
          <Pressable style={[styles.actionBtn, styles.actionPrimary]} onPress={() => router.back()}>
            <Ionicons name="create-outline" size={20} color={colors.onPrimary} />
            <Text style={styles.actionText}>Voltar ao Editor</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  scrim: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.35)' },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing.lg },
  roundBtn: { width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(0,0,0,0.4)', alignItems: 'center', justifyContent: 'center' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  playBig: { width: 80, height: 80, borderRadius: 40, backgroundColor: 'rgba(124,58,237,0.85)', alignItems: 'center', justifyContent: 'center' },
  bottom: { paddingHorizontal: spacing.lg },
  progressRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.xl },
  time: { color: colors.onPrimary, fontSize: fontSize.sm, fontVariant: ['tabular-nums'], ...androidFont },
  progressTrack: { flex: 1, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.3)', justifyContent: 'center' },
  progressFill: { width: '40%', height: 4, borderRadius: 2, backgroundColor: colors.primary },
  progressThumb: { position: 'absolute', left: '40%', width: 14, height: 14, borderRadius: 7, backgroundColor: '#FFFFFF' },
  actions: { flexDirection: 'row', gap: spacing.md },
  actionBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing.sm, height: 50, borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.15)' },
  actionPrimary: { backgroundColor: colors.primary },
  actionText: { color: colors.onPrimary, fontSize: fontSize.sm, fontWeight: fontWeight.semibold, ...androidFont },
});
