// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Slider, Button } from '@/components';
import { colors, radius, spacing, fontSize } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, LocalAudioItem } from '@/services/editorState';

interface Props {
  onAction: (msg: string) => void;
}

export function AudioPanel({ onAction }: Props) {
  const router = useRouter();
  const { config, updateAudio, deleteAudio, duplicateAudio } = useEditorStore();
  const audios = config.audios || [];

  const handleVolume = async (id: string, val: number) => {
    await updateAudio(id, { volume: val / 100 });
  };

  const handleMute = async (item: LocalAudioItem) => {
    await updateAudio(item.id, { muted: !item.muted });
  };

  return (
    <View style={styles.container}>
      {audios.length === 0 ? (
        <View style={styles.empty}>
          <Ionicons name="musical-notes-outline" size={48} color={colors.textTertiary} />
          <Text style={styles.emptyText}>Nenhuma trilha de áudio adicionada</Text>
          <Button
            title="Adicionar Música"
            icon="add"
            onPress={() => router.push('/import')}
            style={{ marginTop: spacing.lg }}
          />
        </View>
      ) : (
        <ScrollView showsVerticalScrollIndicator={false}>
          {audios.map((item) => (
            <View key={item.id} style={styles.audioCard}>
              <View style={styles.cardHeader}>
                <Ionicons name="musical-note" size={20} color={colors.primary} />
                <Text style={styles.audioName} numberOfLines={1}>{item.name}</Text>
                <Pressable onPress={() => duplicateAudio(item.id)} style={styles.iconBtn}>
                   <Ionicons name="copy-outline" size={18} color={colors.textSecondary} />
                </Pressable>
                <Pressable onPress={() => {
                  deleteAudio(item.id);
                  onAction('Áudio removido');
                }} style={styles.iconBtn}>
                   <Ionicons name="trash-outline" size={18} color={colors.danger} />
                </Pressable>
              </View>

              <View style={styles.controls}>
                <Pressable onPress={() => handleMute(item)} style={styles.muteBtn}>
                  <Ionicons
                    name={item.muted || item.volume === 0 ? "volume-mute" : "volume-high"}
                    size={20}
                    color={item.muted ? colors.danger : colors.primary}
                  />
                </Pressable>
                <View style={{ flex: 1 }}>
                  <Slider
                    value={item.muted ? 0 : item.volume * 100}
                    min={0}
                    max={100}
                    onChange={(v) => handleVolume(item.id, v)}
                    label="Volume"
                    showValue
                    suffix="%"
                  />
                </View>
              </View>

              <View style={styles.fadeContainer}>
                <View style={{ flex: 1 }}>
                   <Slider
                      value={item.fadeIn}
                      min={0}
                      max={Math.min(item.duration / 2, 5000)}
                      step={100}
                      onChange={(v) => updateAudio(item.id, { fadeIn: v })}
                      label="Fade In"
                      showValue
                      suffix="ms"
                   />
                </View>
                <View style={{ width: spacing.md }} />
                <View style={{ flex: 1 }}>
                   <Slider
                      value={item.fadeOut}
                      min={0}
                      max={Math.min(item.duration / 2, 5000)}
                      step={100}
                      onChange={(v) => updateAudio(item.id, { fadeOut: v })}
                      label="Fade Out"
                      showValue
                      suffix="ms"
                   />
                </View>
              </View>

              <Pressable
                onPress={() => updateAudio(item.id, { fadeIn: 0, fadeOut: 0 })}
                style={styles.resetBtn}
              >
                <Text style={styles.resetText}>Resetar Fades</Text>
              </Pressable>
            </View>
          ))}
          <Button
            variant="secondary"
            title="Adicionar mais áudio"
            icon="add"
            onPress={() => router.push('/import')}
          />
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { minHeight: 200 },
  empty: { alignItems: 'center', paddingVertical: spacing.xl },
  emptyText: { color: colors.textSecondary, fontSize: fontSize.md, marginTop: spacing.md, ...androidFont },
  audioCard: { backgroundColor: colors.surface, borderRadius: radius.md, padding: spacing.md, marginBottom: spacing.md },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md },
  audioName: { flex: 1, color: colors.textPrimary, fontSize: fontSize.md, fontWeight: '600', ...androidFont },
  iconBtn: { padding: 4 },
  controls: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.md },
  muteBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.background, alignItems: 'center', justifyContent: 'center' },
  fadeContainer: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.sm },
  resetBtn: { alignSelf: 'center', paddingVertical: 4 },
  resetText: { color: colors.textTertiary, fontSize: 11, fontWeight: '500', ...androidFont }
});
