// Powered by OnSpace.AI
import React, { useRef, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, Dimensions } from 'react-native';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '@/components';
import { colors, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

const SLIDES = [
  {
    image: require('@/assets/images/onboarding-1.png'),
    title: 'Edite como um profissional',
    subtitle: 'Timeline multi-track, cortes precisos e controle total do seu vídeo na palma da mão.',
  },
  {
    image: require('@/assets/images/onboarding-2.png'),
    title: 'Efeitos e transições',
    subtitle: 'Centenas de efeitos, filtros e transições para dar vida às suas histórias.',
  },
  {
    image: require('@/assets/images/onboarding-3.png'),
    title: 'Exporte e compartilhe',
    subtitle: 'Exporte em 4K sem marca d’água e compartilhe direto nas suas redes favoritas.',
  },
];

export default function Onboarding() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [index, setIndex] = useState(0);
  const scrollRef = useRef<ScrollView>(null);
  const width = Dimensions.get('window').width;

  const goNext = () => {
    if (index < SLIDES.length - 1) {
      const next = index + 1;
      setIndex(next);
      scrollRef.current?.scrollTo({ x: next * width, animated: true });
    } else {
      router.replace('/(tabs)');
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.topBar, { paddingTop: insets.top + spacing.sm }]}>
        <Pressable onPress={() => router.replace('/(tabs)')} hitSlop={12} style={styles.skip}>
          <Text style={styles.skipText}>Pular</Text>
        </Pressable>
      </View>

      <ScrollView
        ref={scrollRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={(e) => setIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
      >
        {SLIDES.map((slide, i) => (
          <View key={i} style={[styles.slide, { width }]}>
            <Image source={slide.image} style={styles.image} contentFit="cover" transition={250} />
            <View style={styles.textBlock}>
              <Text style={styles.title}>{slide.title}</Text>
              <Text style={styles.subtitle}>{slide.subtitle}</Text>
            </View>
          </View>
        ))}
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}>
        <View style={styles.dots}>
          {SLIDES.map((_, i) => (
            <View key={i} style={[styles.dot, i === index && styles.dotActive]} />
          ))}
        </View>
        <Button title={index === SLIDES.length - 1 ? 'Começar' : 'Continuar'} onPress={goNext} icon="arrow-forward" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  topBar: { paddingHorizontal: spacing.lg, alignItems: 'flex-end', position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10 },
  skip: { paddingVertical: spacing.sm, paddingHorizontal: spacing.md },
  skipText: { color: colors.textSecondary, fontSize: fontSize.md, fontWeight: fontWeight.medium, ...androidFont },
  slide: { flex: 1 },
  image: { width: '100%', height: '62%' },
  textBlock: { paddingHorizontal: spacing.xl, marginTop: spacing.xl },
  title: { color: colors.textPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, textAlign: 'center', ...androidFont },
  subtitle: { color: colors.textSecondary, fontSize: fontSize.base, lineHeight: 24, textAlign: 'center', marginTop: spacing.md, ...androidFont },
  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: spacing.sm, marginBottom: spacing.xl },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.surfaceHigh },
  dotActive: { width: 24, backgroundColor: colors.primary },
});
