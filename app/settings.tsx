// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, Switch } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ScreenHeader } from '@/components';
import { colors, gradients, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export default function SettingsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [autoSave, setAutoSave] = useState(true);

  return (
    <View style={styles.container}>
      <View style={{ paddingTop: insets.top }}>
        <ScreenHeader title="Configurações" leftIcon="chevron-back" onLeftPress={() => router.back()} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + spacing.xxxl }}>
        <Pressable onPress={() => router.push('/(tabs)/premium')} style={styles.premiumCard}>
          <LinearGradient colors={gradients.primary} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.premiumInner}>
            <View style={styles.premiumIcon}>
              <Ionicons name="diamond" size={22} color={colors.onPrimary} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.premiumTitle}>Assinatura Premium</Text>
              <Text style={styles.premiumSub}>Desbloqueie todos os recursos</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onPrimary} />
          </LinearGradient>
        </Pressable>

        <Section title="Geral">
          <Row icon="globe-outline" label="Idioma" value="Português (BR)" onPress={() => {}} />
          <Row icon="save-outline" label="Salvar automaticamente" toggle value={autoSave} onToggle={setAutoSave} />
          <Row icon="folder-outline" label="Pasta de exportação" value="Galeria" onPress={() => {}} />
          <Row icon="color-palette-outline" label="Tema" value="Escuro" onPress={() => {}} />
        </Section>

        <Section title="Sobre">
          <Row icon="document-text-outline" label="Termos de uso" onPress={() => {}} />
          <Row icon="shield-checkmark-outline" label="Privacidade" onPress={() => {}} />
          <Row icon="star-outline" label="Avaliar o app" onPress={() => {}} />
          <Row icon="information-circle-outline" label="Versão" value="1.0.0" />
        </Section>
      </ScrollView>
    </View>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.card}>{children}</View>
    </View>
  );
}

function Row({ icon, label, value, onPress, toggle, onToggle }: { icon: keyof typeof Ionicons.glyphMap; label: string; value?: string | boolean; onPress?: () => void; toggle?: boolean; onToggle?: (v: boolean) => void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.row, pressed && onPress && { opacity: 0.7 }]}>
      <View style={styles.rowIcon}>
        <Ionicons name={icon} size={20} color={colors.primaryVariant} />
      </View>
      <Text style={styles.rowLabel}>{label}</Text>
      {toggle ? (
        <Switch
          value={!!value}
          onValueChange={onToggle}
          trackColor={{ true: colors.primary, false: colors.surfaceHigh }}
          thumbColor={colors.onPrimary}
        />
      ) : (
        <View style={styles.rowRight}>
          {typeof value === 'string' ? <Text style={styles.rowValue}>{value}</Text> : null}
          {onPress ? <Ionicons name="chevron-forward" size={18} color={colors.textTertiary} /> : null}
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  premiumCard: { borderRadius: radius.lg, overflow: 'hidden', marginBottom: spacing.xl },
  premiumInner: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, padding: spacing.lg },
  premiumIcon: { width: 44, height: 44, borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center' },
  premiumTitle: { color: colors.onPrimary, fontSize: fontSize.md, fontWeight: fontWeight.bold, ...androidFont },
  premiumSub: { color: 'rgba(255,255,255,0.85)', fontSize: fontSize.sm, marginTop: 2, ...androidFont },
  section: { marginBottom: spacing.xl },
  sectionTitle: { color: colors.textTertiary, fontSize: fontSize.sm, fontWeight: fontWeight.semibold, marginBottom: spacing.sm, marginLeft: spacing.xs, textTransform: 'uppercase', letterSpacing: 0.5, ...androidFont },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingHorizontal: spacing.md, minHeight: 56, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border },
  rowIcon: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  rowLabel: { flex: 1, color: colors.textPrimary, fontSize: fontSize.md, ...androidFont },
  rowRight: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  rowValue: { color: colors.textTertiary, fontSize: fontSize.sm, ...androidFont },
});
