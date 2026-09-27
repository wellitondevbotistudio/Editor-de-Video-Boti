// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore } from '@/services/editorState';
import { Button } from '@/components';

interface Props {
  onAction: (msg: string) => void;
}

const BUILTIN_STICKERS = [
  { id: 's1', uri: 'https://cdn-icons-png.flaticon.com/512/742/742751.png' },
  { id: 's2', uri: 'https://cdn-icons-png.flaticon.com/512/742/742752.png' },
  { id: 's3', uri: 'https://cdn-icons-png.flaticon.com/512/2107/2107845.png' },
  { id: 's4', uri: 'https://cdn-icons-png.flaticon.com/512/1828/1828884.png' },
  { id: 's5', uri: 'https://cdn-icons-png.flaticon.com/512/742/742914.png' },
  { id: 's6', uri: 'https://cdn-icons-png.flaticon.com/512/742/742761.png' },
  { id: 's7', uri: 'https://cdn-icons-png.flaticon.com/512/742/742750.png' },
  { id: 's8', uri: 'https://cdn-icons-png.flaticon.com/512/1023/1023656.png' },
];

export function StickerPanel({ onAction }: Props) {
  const router = useRouter();
  const { addOverlays } = useEditorStore();

  const addSticker = async (uri: string, name: string) => {
    await addOverlays([{
      id: `sticker_${Date.now()}`,
      uri,
      type: 'photo',
      name,
      duration: 3000,
      originalDuration: 3000,
      startTime: 0,
      zIndex: 10,
      visible: true,
      locked: false,
      trimStart: 0,
      trimEnd: 3000,
      speed: 1.0,
      reverse: false,
      isOverlay: true,
      isGIF: false,
      keyframes: [],
      effects: [],
      adjustments: { brightness: 0, contrast: 0, saturation: 0 },
      filter: 'Original',
      nextTransition: null,
      transform: { x: 50, y: 50, scale: 0.5, rotation: 0, opacity: 1, flipH: false, flipV: false }
    } as any]);
    onAction(`Sticker ${name} adicionado`);
  };

  return (
    <View style={styles.container}>
      <Button
        title="Importar Sticker Próprio"
        variant="secondary"
        icon="image-outline"
        onPress={() => router.push('/import?mode=overlay')}
        style={{ marginBottom: spacing.lg }}
      />

      <Text style={styles.sectionTitle}>Biblioteca de Stickers</Text>
      <View style={styles.grid}>
        {BUILTIN_STICKERS.map((s, i) => (
          <Pressable
            key={s.id}
            onPress={() => addSticker(s.uri, `Sticker ${i+1}`)}
            style={({ pressed }) => [styles.stickerBtn, pressed && { opacity: 0.7 }]}
          >
            <Image source={{ uri: s.uri }} style={styles.stickerImg} contentFit="contain" />
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  sectionTitle: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, justifyContent: 'space-between' },
  stickerBtn: {
    width: '22%',
    aspectRatio: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.border
  },
  stickerImg: { width: '80%', height: '80%' },
});
