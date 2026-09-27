// Powered by OnSpace.AI
import React, { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, Animated, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button, ScreenHeader } from '@/components';
import { colors, gradients, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore } from '@/services/editorState';
import { exportVideo, ExportProgress } from '@/services/exportService';
import { Platform } from 'react-native';

const RESOLUTIONS = ['720p', '1080p', '4K'];
const FPS = ['24', '30', '60'];
const QUALITY = ['Padrão', 'Alta', 'Máxima'];

export default function ExportScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const config = useEditorStore((state) => state.config);

  const [resolution, setResolution] = useState('1080p');
  const [fps, setFps] = useState('30');
  const [quality, setQuality] = useState('Alta');
  const [status, setStatus] = useState<'idle' | 'exporting' | 'done' | 'error'>('idle');
  const [progress, setProgress] = useState(0);
  const [outputPath, setOutputPath] = useState<string | null>(null);

  const progressAnim = useRef(new Animated.Value(0)).current;

  const sizeEstimate = resolution === '4K' ? 240 : resolution === '1080p' ? 90 : 45;

  const startExport = async () => {
    if (Platform.OS === 'web') {
        Alert.alert('Funcionalidade Nativa', 'A exportação de vídeo utiliza FFmpeg e está disponível apenas em dispositivos Android e iOS reais.');
        return;
    }

    if (status === 'exporting') return;

    setStatus('exporting');
    setProgress(0);
    progressAnim.setValue(0);

    try {
      const result = await exportVideo(
        config,
        { resolution, fps, quality },
        (p: ExportProgress) => {
          setProgress(p.percent);
          Animated.timing(progressAnim, {
            toValue: p.percent / 100,
            duration: 300,
            useNativeDriver: false,
          }).start();
        }
      );

      setOutputPath(result);
      setStatus('done');
    } catch (error: any) {
      console.error(error);
      setStatus('idle');
      Alert.alert('Erro na Exportação', error.message || 'Ocorreu um erro inesperado.');
    }
  };

  if (status === 'done') {
    return (
      <View style={styles.container}>
        <View style={[styles.doneWrap, { paddingTop: insets.top }]}>
          <LinearGradient colors={gradients.primary} style={styles.doneIcon}>
            <Ionicons name="checkmark" size={48} color={colors.onPrimary} />
          </LinearGradient>
          <Text style={styles.doneTitle}>Vídeo exportado!</Text>
          <Text style={styles.doneSub}>{`${resolution} · ${fps}fps · Salvo na Galeria`}</Text>

          <View style={styles.shareRow}>
            <ShareButton icon="logo-instagram" label="Instagram" />
            <ShareButton icon="logo-tiktok" label="TikTok" />
            <ShareButton icon="share-social-outline" label="Mais" />
          </View>

          <View style={styles.doneFooter}>
            <Button title="Concluir" onPress={() => router.replace('/(tabs)')} />
            <Button title="Voltar ao editor" variant="ghost" onPress={() => router.back()} />
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={{ paddingTop: insets.top }}>
        <ScreenHeader title="Exportar" leftIcon="close" onLeftPress={() => router.back()} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ padding: spacing.lg, paddingBottom: 140 }}>
        <OptionGroup label="Resolução" options={RESOLUTIONS} value={resolution} onChange={setResolution} disabled={status === 'exporting'} />
        <OptionGroup label="Taxa de quadros (FPS)" options={FPS} value={fps} onChange={setFps} disabled={status === 'exporting'} />
        <OptionGroup label="Qualidade" options={QUALITY} value={quality} onChange={setQuality} disabled={status === 'exporting'} />

        <View style={styles.estimate}>
          <View style={styles.row}>
            <Ionicons name="cloud-download-outline" size={18} color={colors.textSecondary} />
            <Text style={styles.estimateLabel}>Tamanho estimado</Text>
          </View>
          <Text style={styles.estimateValue}>{`~${sizeEstimate} MB`}</Text>
        </View>

        {status === 'exporting' ? (
          <View style={styles.progressCard}>
            <Text style={styles.progressLabel}>Exportando… {progress}%</Text>
            <View style={styles.progressTrack}>
              <Animated.View style={[styles.progressFill, { width: progressAnim.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] }) }]} />
            </View>
            <Text style={styles.warningText}>Mantenha o app aberto durante o processamento.</Text>
          </View>
        ) : null}
      </ScrollView>

      {status === 'idle' ? (
        <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.md }]}>
          <Button title="Iniciar exportação" icon="cloud-upload-outline" onPress={startExport} />
        </View>
      ) : null}
    </View>
  );
}

function OptionGroup({ label, options, value, onChange, disabled }: { label: string; options: string[]; value: string; onChange: (v: string) => void; disabled?: boolean }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupLabel}>{label}</Text>
      <View style={styles.optionRow}>
        {options.map((opt) => {
          const sel = opt === value;
          return (
            <Pressable key={opt} disabled={disabled} onPress={() => onChange(opt)} style={[styles.option, sel && styles.optionSelected, disabled && { opacity: 0.5 }]}>
              <Text style={[styles.optionText, sel && { color: colors.onPrimary }]}>{opt}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function ShareButton({ icon, label }: { icon: keyof typeof Ionicons.glyphMap; label: string }) {
  return (
    <Pressable style={styles.shareItem}>
      <View style={styles.shareIcon}>
        <Ionicons name={icon} size={22} color={colors.textPrimary} />
      </View>
      <Text style={styles.shareLabel}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  group: { marginBottom: spacing.xl },
  groupLabel: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, marginBottom: spacing.md, ...androidFont },
  optionRow: { flexDirection: 'row', gap: spacing.sm },
  option: { flex: 1, height: 48, borderRadius: radius.md, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  optionSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  optionText: { color: colors.textSecondary, fontSize: fontSize.md, fontWeight: fontWeight.medium, ...androidFont },
  estimate: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.lg },
  estimateLabel: { color: colors.textSecondary, fontSize: fontSize.md, ...androidFont },
  estimateValue: { color: colors.primaryVariant, fontSize: fontSize.lg, fontWeight: fontWeight.bold, ...androidFont },
  progressCard: { marginTop: spacing.xl, backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.lg },
  progressLabel: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, marginBottom: spacing.md, ...androidFont },
  progressTrack: { height: 8, borderRadius: 4, backgroundColor: colors.track, overflow: 'hidden' },
  progressFill: { height: 8, borderRadius: 4, backgroundColor: colors.primary },
  warningText: { color: colors.textTertiary, fontSize: 12, textAlign: 'center', marginTop: spacing.md, ...androidFont },
  footer: { position: 'absolute', bottom: 0, left: 0, right: 0, paddingHorizontal: spacing.lg, paddingTop: spacing.md, backgroundColor: colors.backgroundElevated, borderTopWidth: 1, borderTopColor: colors.border },
  doneWrap: { flex: 1, alignItems: 'center', paddingHorizontal: spacing.xl, paddingTop: spacing.xxxl },
  doneIcon: { width: 96, height: 96, borderRadius: 48, alignItems: 'center', justifyContent: 'center', marginTop: spacing.xxxl, marginBottom: spacing.xl },
  doneTitle: { color: colors.textPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, ...androidFont },
  doneSub: { color: colors.textSecondary, fontSize: fontSize.md, marginTop: spacing.sm, ...androidFont },
  shareRow: { flexDirection: 'row', justifyContent: 'space-between', width: '100%', marginTop: spacing.xxxl },
  shareItem: { alignItems: 'center', gap: 8 },
  shareIcon: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center' },
  shareLabel: { color: colors.textSecondary, fontSize: fontSize.sm, ...androidFont },
  doneFooter: { width: '100%', marginTop: 'auto', paddingBottom: spacing.xl, gap: spacing.sm },
});
