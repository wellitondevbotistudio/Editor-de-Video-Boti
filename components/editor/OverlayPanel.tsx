// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore } from '@/services/editorState';
import { Button } from '@/components';

interface Props {
  onAction: (msg: string) => void;
}

export function OverlayPanel({ onAction }: Props) {
  const router = useRouter();
  const { config, setSelectedLayerId } = useEditorStore();

  const overlays = config.clips.filter(c => c.isOverlay);

  const handleAdd = () => {
    // Navigate to import screen with a special parameter or state to indicate overlay mode
    // For now, we'll just go to import. we'll update import.tsx to handle adding as overlay
    router.push('/import?mode=overlay');
  };

  return (
    <View style={styles.container}>
      <Button
        title="Adicionar Imagem ou Vídeo"
        icon="add"
        onPress={handleAdd}
        style={{ marginBottom: spacing.lg }}
      />

      <Text style={styles.sectionTitle}>Sobreposições no Projeto</Text>

      {overlays.length === 0 ? (
        <View style={styles.emptyBox}>
          <Text style={styles.emptyText}>Nenhum overlay adicionado ainda.</Text>
        </View>
      ) : (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.list}>
          {overlays.map((o) => (
            <Pressable
              key={o.id}
              onPress={() => setSelectedLayerId(o.id)}
              style={styles.overlayCard}
            >
              <View style={styles.iconCircle}>
                <Ionicons name={o.type === 'video' ? 'videocam' : 'image'} size={20} color={colors.primary} />
              </View>
              <Text style={styles.overlayName} numberOfLines={1}>{o.name}</Text>
            </Pressable>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.md },
  sectionTitle: { color: colors.textSecondary, fontSize: 12, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  emptyBox: { padding: spacing.lg, backgroundColor: colors.surface, borderRadius: radius.md, alignItems: 'center' },
  emptyText: { color: colors.textTertiary, fontSize: fontSize.sm, ...androidFont },
  list: { gap: spacing.md },
  overlayCard: { width: 90, alignItems: 'center', gap: 8 },
  iconCircle: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.surfaceElevated, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.border },
  overlayName: { color: colors.textSecondary, fontSize: 10, textAlign: 'center', ...androidFont },
});
