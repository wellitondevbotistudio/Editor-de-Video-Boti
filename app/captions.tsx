// Powered by OnSpace.AI
import React, { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ScreenHeader, Button, SegmentedControl, Slider } from '@/components';
import { captionLanguages, textColors } from '@/services/mockData';
import { colors, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

type Phase = 'setup' | 'processing' | 'ready';

const GENERATED = [
  { time: '00:01', text: 'Bem-vindo ao meu vídeo!' },
  { time: '00:04', text: 'Hoje vamos criar algo incrível' },
  { time: '00:08', text: 'Não esqueça de curtir e seguir' },
];

export default function CaptionsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [phase, setPhase] = useState<Phase>('setup');
  const [lang, setLang] = useState('Português (BR)');
  const [progress, setProgress] = useState(0);
  const [color, setColor] = useState(textColors[0]);
  const [size, setSize] = useState(28);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => () => { if (timer.current) clearInterval(timer.current); }, []);

  const generate = () => {
    setPhase('processing');
    setProgress(0);
    let p = 0;
    timer.current = setInterval(() => {
      p += 5;
      setProgress(p);
      if (p >= 100) {
        if (timer.current) clearInterval(timer.current);
        setPhase('ready');
      }
    }, 120);
  };

  return (
    <View style={styles.container}>
      <View style={{ paddingTop: insets.top }}>
        <ScreenHeader title="Legendas Automáticas" leftIcon="chevron-back" onLeftPress={() => router.back()} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ padding: spacing.lg, paddingBottom: 140 }}>
        <Text style={styles.label}>Idioma do áudio</Text>
        <View style={styles.langWrap}>
          <SegmentedControl options={captionLanguages} value={lang} onChange={setLang} scrollable />
        </View>

        {phase === 'setup' ? (
          <View style={styles.hint}>
            <Ionicons name="sparkles-outline" size={28} color={colors.primaryVariant} />
            <Text style={styles.hintTitle}>Gere legendas automaticamente</Text>
            <Text style={styles.hintText}>A IA analisa o áudio do seu vídeo e cria legendas sincronizadas.</Text>
          </View>
        ) : null}

        {phase === 'processing' ? (
          <View style={styles.processing}>
            <Ionicons name="pulse-outline" size={30} color={colors.primaryVariant} />
            <Text style={styles.processingTitle}>Analisando áudio…</Text>
            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${progress}%` }]} />
            </View>
            <Text style={styles.processingPct}>{progress}%</Text>
          </View>
        ) : null}

        {phase === 'ready' ? (
          <View>
            <Text style={styles.label}>Estilo das legendas</Text>
            <View style={styles.stylePreview}>
              <Text style={[styles.previewText, { color, fontSize: size }]}>Legenda de exemplo</Text>
            </View>
            <View style={styles.colorRow}>
              {textColors.map((c) => (
                <Pressable key={c} onPress={() => setColor(c)} style={[styles.colorDot, { backgroundColor: c }, color === c && styles.colorDotActive]} />
              ))}
            </View>
            <View style={{ marginTop: spacing.lg }}>
              <Slider value={size} min={16} max={44} onChange={setSize} label="Tamanho" showValue />
            </View>

            <Text style={[styles.label, { marginTop: spacing.xl }]}>Legendas geradas</Text>
            {GENERATED.map((g, i) => (
              <View key={i} style={styles.captionRow}>
                <Text style={styles.captionTime}>{g.time}</Text>
                <Text style={styles.captionText}>{g.text}</Text>
                <Ionicons name="create-outline" size={16} color={colors.textTertiary} />
              </View>
            ))}
          </View>
        ) : null}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.md }]}>
        {phase === 'ready' ? (
          <Button title="Aplicar legendas" icon="checkmark" onPress={() => router.back()} />
        ) : (
          <Button title="Gerar Legendas" icon="sparkles-outline" loading={phase === 'processing'} onPress={generate} />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  label: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, marginBottom: spacing.md, ...androidFont },
  langWrap: { marginBottom: spacing.xl },
  hint: { alignItems: 'center', backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.xl, gap: spacing.sm },
  hintTitle: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.semibold, ...androidFont },
  hintText: { color: colors.textSecondary, fontSize: fontSize.md, textAlign: 'center', lineHeight: 22, ...androidFont },
  processing: { alignItems: 'center', backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.xl, gap: spacing.md },
  processingTitle: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.semibold, ...androidFont },
  progressTrack: { width: '100%', height: 8, borderRadius: 4, backgroundColor: colors.track, overflow: 'hidden' },
  progressFill: { height: 8, borderRadius: 4, backgroundColor: colors.primary },
  processingPct: { color: colors.primaryVariant, fontSize: fontSize.md, fontWeight: fontWeight.bold, ...androidFont },
  stylePreview: { height: 120, borderRadius: radius.lg, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center', marginBottom: spacing.lg },
  previewText: { fontWeight: fontWeight.bold, ...androidFont },
  colorRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  colorDot: { width: 34, height: 34, borderRadius: 17, borderWidth: 2, borderColor: colors.border },
  colorDotActive: { borderColor: colors.primaryVariant, borderWidth: 3 },
  captionRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, backgroundColor: colors.surface, borderRadius: radius.md, padding: spacing.md, marginBottom: spacing.sm },
  captionTime: { color: colors.primaryVariant, fontSize: fontSize.sm, fontWeight: fontWeight.semibold, fontVariant: ['tabular-nums'], ...androidFont },
  captionText: { flex: 1, color: colors.textPrimary, fontSize: fontSize.md, ...androidFont },
  footer: { position: 'absolute', bottom: 0, left: 0, right: 0, paddingHorizontal: spacing.lg, paddingTop: spacing.md, backgroundColor: colors.backgroundElevated, borderTopWidth: 1, borderTopColor: colors.border },
});
