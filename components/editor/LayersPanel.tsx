// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore } from '@/services/editorState';

interface Props {
  onAction: (msg: string) => void;
}

export function LayersPanel({ onAction }: Props) {
  const { config, selectedLayerId, setSelectedLayerId, toggleLayerVisibility, toggleLayerLock, moveLayerZ } = useEditorStore();

  const allLayers = [
    ...config.clips.map(c => ({ id: c.id, type: 'video' as const, name: c.name, zIndex: c.zIndex, visible: c.visible, locked: c.locked })),
    ...config.texts.map(t => ({ id: t.id, type: 'text' as const, name: t.name, zIndex: t.zIndex, visible: t.visible, locked: t.locked })),
    ...config.audios.map(a => ({ id: a.id, type: 'audio' as const, name: a.name, zIndex: a.zIndex, visible: a.visible, locked: a.locked })),
  ].sort((a, b) => b.zIndex - a.zIndex);

  const getIcon = (type: string): keyof typeof Ionicons.glyphMap => {
    switch (type) {
      case 'video': return 'videocam-outline';
      case 'text': return 'text-outline';
      case 'audio': return 'musical-note-outline';
      default: return 'layers-outline';
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Camadas</Text>
        <View style={styles.headerActions}>
          <Pressable
            onPress={() => selectedLayerId && moveLayerZ(selectedLayerId, 'front')}
            style={[styles.headerBtn, !selectedLayerId && styles.disabled]}
          >
            <Ionicons name="arrow-up-circle-outline" size={20} color={colors.textPrimary} />
          </Pressable>
          <Pressable
            onPress={() => selectedLayerId && moveLayerZ(selectedLayerId, 'back')}
            style={[styles.headerBtn, !selectedLayerId && styles.disabled]}
          >
            <Ionicons name="arrow-down-circle-outline" size={20} color={colors.textPrimary} />
          </Pressable>
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} style={styles.list}>
        {allLayers.map((layer) => {
          const isSelected = selectedLayerId === layer.id;
          return (
            <Pressable
              key={layer.id}
              onPress={() => setSelectedLayerId(layer.id)}
              style={[styles.layerItem, isSelected && styles.layerItemSelected]}
            >
              <Ionicons name="reorder-two-outline" size={20} color={colors.textTertiary} />
              <View style={styles.iconBox}>
                <Ionicons name={getIcon(layer.type)} size={18} color={isSelected ? colors.primaryVariant : colors.textSecondary} />
              </View>
              <Text style={[styles.layerName, isSelected && styles.textSelected]} numberOfLines={1}>
                {layer.name}
              </Text>

              <View style={styles.actions}>
                <Pressable onPress={() => toggleLayerLock(layer.id)} style={styles.actionBtn}>
                  <Ionicons name={layer.locked ? "lock-closed" : "lock-open-outline"} size={18} color={layer.locked ? colors.primary : colors.textTertiary} />
                </Pressable>
                <Pressable onPress={() => toggleLayerVisibility(layer.id)} style={styles.actionBtn}>
                  <Ionicons name={layer.visible ? "eye-outline" : "eye-off-outline"} size={18} color={layer.visible ? colors.primary : colors.textTertiary} />
                </Pressable>
              </View>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, minHeight: 300 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing.md },
  title: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.bold, textTransform: 'uppercase', letterSpacing: 1, ...androidFont },
  headerActions: { flexDirection: 'row', gap: spacing.sm },
  headerBtn: { padding: 4 },
  disabled: { opacity: 0.3 },
  list: { flex: 1 },
  layerItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    marginBottom: 6,
    gap: spacing.sm,
    borderWidth: 1,
    borderColor: 'transparent'
  },
  layerItemSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft
  },
  iconBox: {
    width: 32,
    height: 32,
    borderRadius: 6,
    backgroundColor: colors.background,
    alignItems: 'center',
    justifyContent: 'center',
  },
  layerName: { flex: 1, color: colors.textSecondary, fontSize: fontSize.md, ...androidFont },
  textSelected: { color: colors.textPrimary, fontWeight: fontWeight.semibold },
  actions: { flexDirection: 'row', gap: 4 },
  actionBtn: { padding: 6 },
});
