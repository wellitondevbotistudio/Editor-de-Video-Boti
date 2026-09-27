// Powered by OnSpace.AI
import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated, Easing } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { colors, gradients, spacing, fontSize, fontWeight, shadow } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export default function SplashScreen() {
  const router = useRouter();
  const scale = useRef(new Animated.Value(0.7)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const spin = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(scale, { toValue: 1, friction: 5, useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 1, duration: 600, useNativeDriver: true }),
    ]).start();

    Animated.loop(
      Animated.timing(spin, { toValue: 1, duration: 1200, easing: Easing.linear, useNativeDriver: true }),
    ).start();

    const timer = setTimeout(() => router.replace('/onboarding'), 2200);
    return () => clearTimeout(timer);
  }, [opacity, router, scale, spin]);

  const rotate = spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] });

  return (
    <View style={styles.container}>
      <Animated.View style={{ opacity, transform: [{ scale }], alignItems: 'center' }}>
        <LinearGradient colors={gradients.primary} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[styles.logo, shadow.glow]}>
          <Ionicons name="play" size={44} color={colors.onPrimary} style={{ marginLeft: 6 }} />
        </LinearGradient>
        <Text style={styles.name}>Editor de Vídeo</Text>
        <Text style={styles.tagline}>Crie histórias incríveis</Text>
      </Animated.View>

      <Animated.View style={[styles.loader, { transform: [{ rotate }] }]}>
        <Ionicons name="sync-outline" size={22} color={colors.primaryVariant} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background, alignItems: 'center', justifyContent: 'center' },
  logo: { width: 104, height: 104, borderRadius: 30, alignItems: 'center', justifyContent: 'center', marginBottom: spacing.xl },
  name: { color: colors.textPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, letterSpacing: 0.5, ...androidFont },
  tagline: { color: colors.textSecondary, fontSize: fontSize.md, marginTop: spacing.sm, ...androidFont },
  loader: { position: 'absolute', bottom: 72 },
});