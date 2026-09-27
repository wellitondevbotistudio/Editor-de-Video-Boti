// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, fontWeight, radius } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

export type SelectionContext = 'none' | 'main_clip' | 'overlay' | 'audio' | 'text' | 'subtitle';

export interface Tool {
  id: string;
  name: string;
  icon: keyof typeof Ionicons.glyphMap;
  isDanger?: boolean;
}

const TOOL_DEFINITIONS: Record<string, Tool> = {
  split: { id: 'split', name: 'Dividir', icon: 'cut-outline' },
  delete: { id: 'delete', name: 'Excluir', icon: 'trash-outline', isDanger: true },
  duplicate: { id: 'duplicate', name: 'Duplicar', icon: 'copy-outline' },
  edit: { id: 'edit', name: 'Editar', icon: 'options-outline' },
  canvas: { id: 'canvas', name: 'Formato', icon: 'resize-outline' },
  layers: { id: 'layers', name: 'Camadas', icon: 'layers-outline' },
  audio: { id: 'audio', name: 'Áudio', icon: 'musical-notes-outline' },
  text: { id: 'text', name: 'Texto', icon: 'text-outline' },
  captions: { id: 'captions', name: 'Legendas', icon: 'chatbox-ellipses-outline' },
  overlay: { id: 'overlay', name: 'Sobrepor', icon: 'albums-outline' },
  effects: { id: 'effects', name: 'Efeitos', icon: 'sparkles-outline' },
  transition: { id: 'transition', name: 'Transição', icon: 'swap-horizontal-outline' },
  color: { id: 'color', name: 'Cor', icon: 'color-palette-outline' },
  animation: { id: 'animation', name: 'Animação', icon: 'flash-outline' },
  transform: { id: 'transform', name: 'Transformar', icon: 'move-outline' },
  text_anim: { id: 'text_anim', name: 'Animação', icon: 'text-outline' },
  stickers: { id: 'stickers', name: 'Stickers', icon: 'happy-outline' },
};

const CONTEXT_MENUS: Record<SelectionContext, string[]> = {
  none: ['edit', 'audio', 'text', 'overlay', 'effects', 'canvas', 'color', 'captions', 'layers'],
  main_clip: ['split', 'edit', 'animation', 'transform', 'color', 'duplicate', 'delete'],
  overlay: ['split', 'edit', 'animation', 'transform', 'color', 'duplicate', 'delete'],
  audio: ['split', 'audio', 'duplicate', 'delete'],
  text: ['edit', 'text_anim', 'transform', 'duplicate', 'delete'],
  subtitle: ['captions', 'delete'],
};

interface ToolbarProps {
  active?: string;
  selectionContext: SelectionContext;
  onSelect: (id: string) => void;
}

export function Toolbar({ active, selectionContext, onSelect }: ToolbarProps) {
  const toolsToShow = CONTEXT_MENUS[selectionContext].map(id => TOOL_DEFINITIONS[id]);

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.content}>
      {toolsToShow.map((t) => {
        const selected = active === t.id;
        const isDelete = t.isDanger;

        return (
          <Pressable
            key={t.id}
            onPress={() => onSelect(t.id)}
            style={({ pressed }) => [styles.item, pressed && { opacity: 0.7 }]}
          >
            <View
              style={[
                styles.iconBox,
                selected && styles.iconBoxActive,
                isDelete && styles.iconBoxDelete,
              ]}
            >
              <Ionicons
                name={t.icon as any}
                size={22}
                color={
                  isDelete
                    ? colors.error
                    : selected
                    ? colors.primaryVariant
                    : colors.textSecondary
                }
              />
            </View>
            <Text
              style={[
                styles.label,
                selected && { color: colors.textPrimary },
                isDelete && { color: colors.error },
              ]}
            >
              {t.name}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.lg, gap: spacing.md },
  item: { alignItems: 'center', width: 66 },
  iconBox: {
    width: 52,
    height: 44,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 5,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  iconBoxActive: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  iconBoxDelete: { backgroundColor: 'rgba(239, 68, 68, 0.15)', borderColor: colors.error },
  label: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.medium, ...androidFont },
});
