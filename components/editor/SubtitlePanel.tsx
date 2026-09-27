// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Slider, Button, SegmentedControl } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, SubtitleSegment } from '@/services/editorState';
import { transcribeMedia } from '@/services/transcriptionService';

interface Props {
  onAction: (msg: string) => void;
  timelinePosition: number;
}

export function SubtitlePanel({ onAction, timelinePosition }: Props) {
  const { config, addManualSubtitle, updateSubtitle, deleteSubtitle, clearSubtitles, addSubtitles, updateSubtitleStyle } = useEditorStore();
  const [tab, setTab] = useState('Legendas');
  const [loading, setLoading] = useState(false);

  const style = config.subtitleStyle;

  const handleTranscribe = async () => {
    if (config.clips.length === 0) return;
    setLoading(true);
    try {
      // In this version, we take the first clip as reference for STT
      const result = await transcribeMedia(config.clips[0].uri);
      if (result.segments.length > 0) {
        const segments: SubtitleSegment[] = result.segments.map(s => ({
          ...s,
          enabled: true
        }));
        await addSubtitles(segments);
        onAction('Legendas geradas com sucesso');
      } else {
        onAction('Nenhuma fala detectada');
      }
    } catch (e) {
      onAction('Erro ao gerar legendas');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <SegmentedControl options={['Legendas', 'Estilo']} value={tab} onChange={setTab} />

      {tab === 'Legendas' ? (
        <View style={{ flex: 1, marginTop: spacing.md }}>
          {loading ? (
            <View style={styles.loadingBox}>
              <ActivityIndicator size="large" color={colors.primary} />
              <Text style={styles.loadingText}>Analisando áudio e gerando legendas...</Text>
            </View>
          ) : config.subtitles.length === 0 ? (
            <View style={styles.emptyBox}>
              <Ionicons name="chatbubble-ellipses-outline" size={48} color={colors.textTertiary} />
              <Text style={styles.emptyText}>Crie legendas automáticas com um toque</Text>
              <Button title="Gerar Legendas Automáticas" icon="sparkles-outline" onPress={handleTranscribe} style={{ marginTop: spacing.lg }} />
              <Button title="Adicionar Manualmente" variant="ghost" icon="add" onPress={() => addManualSubtitle(timelinePosition)} />
            </View>
          ) : (
            <ScrollView showsVerticalScrollIndicator={false} style={styles.list}>
              <View style={{ marginBottom: spacing.md }}>
                <Button title="Regerar Legendas Automáticas" variant="secondary" icon="sparkles-outline" onPress={handleTranscribe} />
              </View>
              {config.subtitles.map((s) => (
                <View key={s.id} style={styles.subCard}>
                  <TextInput
                    value={s.text}
                    onChangeText={(v) => updateSubtitle(s.id, { text: v })}
                    style={styles.subInput}
                    multiline
                  />
                  <View style={styles.subFooter}>
                    <Text style={styles.timeText}>
                      {(s.startTime / 1000).toFixed(1)}s → {(s.endTime / 1000).toFixed(1)}s
                    </Text>
                    <View style={{ flex: 1 }} />
                    <Pressable onPress={() => updateSubtitle(s.id, { enabled: !s.enabled })} style={styles.actionBtn}>
                      <Ionicons name={s.enabled ? "eye-outline" : "eye-off-outline"} size={18} color={s.enabled ? colors.primary : colors.textTertiary} />
                    </Pressable>
                    <Pressable onPress={() => deleteSubtitle(s.id)} style={styles.actionBtn}>
                      <Ionicons name="trash-outline" size={18} color={colors.danger} />
                    </Pressable>
                  </View>
                </View>
              ))}
              <View style={styles.listFooter}>
                <Button title="Adicionar Segmento" variant="secondary" icon="add" onPress={() => addManualSubtitle(timelinePosition)} />
                <Button title="Limpar Tudo" variant="ghost" onPress={clearSubtitles} style={{ marginTop: spacing.sm }} />
              </View>
            </ScrollView>
          )}
        </View>
      ) : (
        <ScrollView showsVerticalScrollIndicator={false} style={{ marginTop: spacing.md }}>
          <Slider
            value={style.fontSize}
            min={12} max={120}
            onChange={(v) => updateSubtitleStyle({ fontSize: v })}
            label="Tamanho da Fonte"
            showValue
          />
          <View style={{ height: spacing.lg }} />
          <Slider
            value={style.positionY}
            min={10} max={95}
            onChange={(v) => updateSubtitleStyle({ positionY: v })}
            label="Posição Vertical"
            showValue
            suffix="%"
          />
          <View style={{ height: spacing.lg }} />
          <View style={styles.styleRow}>
            <Text style={styles.styleLabel}>Cor do Texto</Text>
            <View style={[styles.colorDot, { backgroundColor: style.color }]} />
          </View>
          <View style={styles.styleRow}>
            <Text style={styles.styleLabel}>Fundo da Legenda</Text>
            <Pressable
              onPress={() => updateSubtitleStyle({ backgroundEnabled: !style.backgroundEnabled })}
              style={[styles.toggle, style.backgroundEnabled && styles.toggleOn]}
            >
              <View style={[styles.toggleCircle, style.backgroundEnabled && styles.toggleCircleOn]} />
            </Pressable>
          </View>
          {style.backgroundEnabled && (
            <Slider
              value={style.backgroundOpacity * 100}
              min={0} max={100}
              onChange={(v) => updateSubtitleStyle({ backgroundOpacity: v / 100 })}
              label="Opacidade do Fundo"
              showValue
              suffix="%"
            />
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, minHeight: 300 },
  emptyBox: { alignItems: 'center', paddingVertical: spacing.xl },
  emptyText: { color: colors.textSecondary, textAlign: 'center', marginTop: spacing.md, ...androidFont },
  loadingBox: { alignItems: 'center', paddingVertical: spacing.xxl },
  loadingText: { color: colors.textSecondary, marginTop: spacing.lg, ...androidFont },
  list: { flex: 1 },
  subCard: { backgroundColor: colors.surface, borderRadius: radius.md, padding: spacing.md, marginBottom: spacing.sm, borderWidth: 1, borderColor: colors.border },
  subInput: { color: colors.textPrimary, fontSize: fontSize.md, ...androidFont, marginBottom: spacing.sm },
  subFooter: { flexDirection: 'row', alignItems: 'center' },
  timeText: { color: colors.textTertiary, fontSize: 11, ...androidFont },
  actionBtn: { padding: 8 },
  listFooter: { paddingVertical: spacing.lg },
  styleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing.xl },
  styleLabel: { color: colors.textPrimary, fontSize: fontSize.md, ...androidFont },
  colorDot: { width: 24, height: 24, borderRadius: 12, borderWidth: 1, borderColor: '#FFF' },
  toggle: { width: 44, height: 24, borderRadius: 12, backgroundColor: colors.surfaceElevated, padding: 2 },
  toggleOn: { backgroundColor: colors.primary },
  toggleCircle: { width: 20, height: 20, borderRadius: 10, backgroundColor: colors.textSecondary },
  toggleCircleOn: { alignSelf: 'flex-end', backgroundColor: '#FFF' },
});
