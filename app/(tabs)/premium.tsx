// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '@/components';
import { useAlert } from '@/template';
import { colors, gradients, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

const BENEFITS = [
  { icon: 'water-outline', title: 'Sem marca d’água', desc: 'Exporte vídeos limpos e profissionais' },
  { icon: 'sparkles-outline', title: 'Efeitos liberados', desc: 'Todos os efeitos e filtros premium' },
  { icon: 'film-outline', title: 'Exportação 4K', desc: 'Máxima qualidade em 60fps' },
  { icon: 'musical-notes-outline', title: 'Biblioteca de áudio', desc: 'Milhares de músicas livres de direitos' },
];

const PLANS = [
  { id: 'monthly', title: 'Mensal', price: 'R$ 24,90', period: '/mês', badge: null },
  { id: 'yearly', title: 'Anual', price: 'R$ 149,90', period: '/ano', badge: 'Mais Popular', sub: 'Economize 50%' },
  { id: 'lifetime', title: 'Vitalício', price: 'R$ 399,90', period: 'pagamento único', badge: null },
];

export default function PremiumScreen() {
  const insets = useSafeAreaInsets();
  const { showAlert } = useAlert();
  const [selected, setSelected] = useState('yearly');

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: insets.bottom + 120 }}>
        <LinearGradient colors={gradients.premium} style={[styles.hero, { paddingTop: insets.top + spacing.xl }]}>
          <View style={styles.crown}>
            <Ionicons name="diamond" size={30} color={colors.onPrimary} />
          </View>
          <Text style={styles.heroTitle}>Editor Premium</Text>
          <Text style={styles.heroSub}>Desbloqueie todo o potencial da sua criatividade</Text>
        </LinearGradient>

        <View style={styles.benefits}>
          {BENEFITS.map((b) => (
            <View key={b.title} style={styles.benefitRow}>
              <View style={styles.benefitIcon}>
                <Ionicons name={b.icon as any} size={20} color={colors.primaryVariant} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.benefitTitle}>{b.title}</Text>
                <Text style={styles.benefitDesc}>{b.desc}</Text>
              </View>
              <Ionicons name="checkmark-circle" size={20} color={colors.success} />
            </View>
          ))}
        </View>

        <View style={styles.plans}>
          {PLANS.map((p) => {
            const isSel = selected === p.id;
            return (
              <Pressable key={p.id} onPress={() => setSelected(p.id)} style={[styles.planCard, isSel && styles.planCardSelected]}>
                {p.badge ? (
                  <View style={styles.planBadge}>
                    <Text style={styles.planBadgeText}>{p.badge}</Text>
                  </View>
                ) : null}
                <View style={styles.planLeft}>
                  <View style={[styles.radio, isSel && styles.radioSelected]}>
                    {isSel ? <View style={styles.radioDot} /> : null}
                  </View>
                  <View>
                    <Text style={styles.planTitle}>{p.title}</Text>
                    {p.sub ? <Text style={styles.planSub}>{p.sub}</Text> : null}
                  </View>
                </View>
                <View style={styles.planRight}>
                  <Text style={styles.planPrice}>{p.price}</Text>
                  <Text style={styles.planPeriod}>{p.period}</Text>
                </View>
              </Pressable>
            );
          })}
        </View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.md }]}>
        <Button title="Começar Agora" icon="rocket-outline" onPress={() => showAlert('Protótipo', 'Fluxo de assinatura simulado.')} />
        <Pressable onPress={() => showAlert('Restaurar', 'Nenhuma compra encontrada.')} style={styles.restore}>
          <Text style={styles.restoreText}>Restaurar Compras</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  hero: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xxl, alignItems: 'center', borderBottomLeftRadius: radius.xxl, borderBottomRightRadius: radius.xxl },
  crown: { width: 64, height: 64, borderRadius: radius.lg, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center', marginBottom: spacing.md },
  heroTitle: { color: colors.onPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, ...androidFont },
  heroSub: { color: 'rgba(255,255,255,0.85)', fontSize: fontSize.md, textAlign: 'center', marginTop: spacing.sm, ...androidFont },
  benefits: { paddingHorizontal: spacing.lg, marginTop: spacing.xl, gap: spacing.md },
  benefitRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.md },
  benefitIcon: { width: 44, height: 44, borderRadius: radius.md, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  benefitTitle: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, ...androidFont },
  benefitDesc: { color: colors.textSecondary, fontSize: fontSize.sm, marginTop: 2, ...androidFont },
  plans: { paddingHorizontal: spacing.lg, marginTop: spacing.xl, gap: spacing.md },
  planCard: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.lg, borderWidth: 2, borderColor: 'transparent' },
  planCardSelected: { borderColor: colors.primary, backgroundColor: colors.surfaceElevated },
  planBadge: { position: 'absolute', top: -10, right: spacing.lg, backgroundColor: colors.primary, paddingHorizontal: spacing.md, paddingVertical: 4, borderRadius: radius.pill },
  planBadgeText: { color: colors.onPrimary, fontSize: 10, fontWeight: fontWeight.bold, ...androidFont },
  planLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.borderStrong, alignItems: 'center', justifyContent: 'center' },
  radioSelected: { borderColor: colors.primary },
  radioDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.primary },
  planTitle: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, ...androidFont },
  planSub: { color: colors.success, fontSize: fontSize.xs, marginTop: 2, ...androidFont },
  planRight: { alignItems: 'flex-end' },
  planPrice: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.bold, ...androidFont },
  planPeriod: { color: colors.textTertiary, fontSize: 11, ...androidFont },
  footer: { position: 'absolute', bottom: 0, left: 0, right: 0, paddingHorizontal: spacing.lg, paddingTop: spacing.md, backgroundColor: colors.backgroundElevated, borderTopWidth: 1, borderTopColor: colors.border },
  restore: { alignItems: 'center', paddingVertical: spacing.md },
  restoreText: { color: colors.textSecondary, fontSize: fontSize.sm, ...androidFont },
});
