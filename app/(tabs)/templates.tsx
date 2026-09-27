// Powered by OnSpace.AI
import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, TextInput } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { SegmentedControl } from '@/components';
import { templates, templateCategories } from '@/services/mockData';
import { colors, spacing, fontSize, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export default function TemplatesScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [cat, setCat] = useState('Em alta');
  const [query, setQuery] = useState('');

  const list = useMemo(
    () => templates.filter((t) => (cat === 'Em alta' ? true : t.category === cat)).filter((t) => t.title.toLowerCase().includes(query.toLowerCase())),
    [cat, query],
  );

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: spacing.xxxl }}>
        <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
          <Text style={styles.title}>Modelos</Text>
          <Text style={styles.subtitle}>Comece com um estilo pronto</Text>
        </View>

        <View style={styles.search}>
          <Ionicons name="search" size={18} color={colors.textTertiary} />
          <TextInput value={query} onChangeText={setQuery} placeholder="Buscar modelos" placeholderTextColor={colors.textTertiary} style={styles.searchInput} />
        </View>

        <View style={styles.cats}>
          <SegmentedControl options={templateCategories} value={cat} onChange={setCat} scrollable />
        </View>

        <View style={styles.grid}>
          {list.map((t) => (
            <Pressable key={t.id} onPress={() => router.push('/import')} style={({ pressed }) => [styles.card, pressed && { opacity: 0.92 }]}>
              <Image source={{ uri: t.thumb }} style={styles.cardImg} contentFit="cover" transition={200} />
              <View style={styles.overlay}>
                {t.premium ? (
                  <View style={styles.premiumBadge}>
                    <Ionicons name="diamond" size={10} color={colors.onPrimary} />
                    <Text style={styles.premiumText}>PRO</Text>
                  </View>
                ) : null}
                <View style={styles.cardFooter}>
                  <Text style={styles.cardTitle} numberOfLines={1}>{t.title}</Text>
                  <View style={styles.cardMeta}>
                    <Ionicons name="film-outline" size={12} color={colors.textSecondary} />
                    <Text style={styles.cardMetaText}>{t.clips} clipes</Text>
                    <Text style={styles.cardMetaText}>· {t.duration}</Text>
                  </View>
                </View>
                <View style={styles.useBtn}>
                  <Text style={styles.useText}>Usar</Text>
                </View>
              </View>
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: spacing.lg, paddingBottom: spacing.md },
  title: { color: colors.textPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, ...androidFont },
  subtitle: { color: colors.textSecondary, fontSize: fontSize.md, marginTop: 4, ...androidFont },
  search: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, backgroundColor: colors.surface, borderRadius: radius.md, paddingHorizontal: spacing.md, height: 46, marginHorizontal: spacing.lg },
  searchInput: { flex: 1, color: colors.textPrimary, fontSize: fontSize.md, ...androidFont },
  cats: { marginTop: spacing.md, paddingLeft: spacing.lg },
  grid: { flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: spacing.lg, gap: spacing.md, marginTop: spacing.md },
  card: { width: '47.5%', aspectRatio: 0.7, borderRadius: radius.lg, overflow: 'hidden', backgroundColor: colors.surface },
  cardImg: { width: '100%', height: '100%' },
  overlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.25)', padding: spacing.md, justifyContent: 'flex-end' },
  premiumBadge: { position: 'absolute', top: spacing.sm, left: spacing.sm, flexDirection: 'row', alignItems: 'center', gap: 3, backgroundColor: colors.primary, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.sm },
  premiumText: { color: colors.onPrimary, fontSize: 9, fontWeight: fontWeight.bold, ...androidFont },
  cardFooter: {},
  cardTitle: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.bold, ...androidFont },
  cardMeta: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 4 },
  cardMetaText: { color: colors.textSecondary, fontSize: 11, ...androidFont },
  useBtn: { marginTop: spacing.sm, backgroundColor: 'rgba(124,58,237,0.9)', borderRadius: radius.pill, paddingVertical: 8, alignItems: 'center' },
  useText: { color: colors.onPrimary, fontSize: fontSize.sm, fontWeight: fontWeight.semibold, ...androidFont },
});
