// Powered by OnSpace.AI
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAlert } from '@/template';
import { colors, gradients, spacing, fontSize, fontWeight, radius, shadow } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { loadAllProjectsLocal, saveProjectLocal, deleteProjectLocal, ProjectData } from '@/services/projectService';
import { PromptModal } from '@/components/ui/PromptModal';
import { useEditorStore } from '@/services/editorState';

export default function ProjectsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { showAlert } = useAlert();

  const [projects, setProjects] = useState<ProjectData[]>([]);
  const [promptVisible, setPromptVisible] = useState(false);
  const [selectedProject, setSelectedProject] = useState<ProjectData | null>(null);
  const { setProject } = useEditorStore();

  const fetchProjects = async () => {
    const list = await loadAllProjectsLocal();
    setProjects(list);
  };

  useEffect(() => {
    fetchProjects();
  }, []);

  const handleRenameSubmit = async (newName: string) => {
    if (!selectedProject || !newName.trim()) {
      setPromptVisible(false);
      return;
    }
    const updated = { ...selectedProject, title: newName.trim() };
    await saveProjectLocal(updated);
    setPromptVisible(false);
    fetchProjects();
  };

  const openMenu = (p: ProjectData) => {
    showAlert(p.title, undefined, [
      {
        text: 'Renomear',
        onPress: () => {
          setSelectedProject(p);
          setPromptVisible(true);
        },
      },
      {
        text: 'Duplicar',
        onPress: async () => {
          const duplicated: ProjectData = {
            ...p,
            id: `p_${Date.now()}`,
            title: `${p.title} (Cópia)`,
            date: 'Hoje',
          };
          await saveProjectLocal(duplicated);
          fetchProjects();
        },
      },
      {
        text: 'Excluir',
        style: 'destructive',
        onPress: async () => {
          await deleteProjectLocal(p.id);
          fetchProjects();
        },
      },
      { text: 'Cancelar', style: 'cancel' },
    ]);
  };

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: spacing.xxxl }}>
        <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
          <Pressable hitSlop={10} style={styles.iconBtn}>
            <Ionicons name="menu" size={24} color={colors.textPrimary} />
          </Pressable>
          <Text style={styles.headerTitle}>Projetos</Text>
          <View style={styles.headerActions}>
            <Pressable hitSlop={10} onPress={() => router.push('/(tabs)/premium')} style={styles.iconBtn}>
              <Ionicons name="diamond-outline" size={22} color={colors.primaryVariant} />
            </Pressable>
            <Pressable hitSlop={10} onPress={() => router.push('/settings')} style={styles.iconBtn}>
              <Ionicons name="settings-outline" size={22} color={colors.textPrimary} />
            </Pressable>
          </View>
        </View>

        <Pressable
          onPress={async () => {
            const newProj: ProjectData = {
              id: `p_${Date.now()}`,
              title: `Projeto ${projects.length + 1}`,
              duration: '00:00',
              date: 'Hoje',
              thumb: 'https://picsum.photos/seed/vidnew/400/600',
              config: JSON.stringify({ clips: [], texts: [], audios: [] }),
            };
            await saveProjectLocal(newProj);
            await fetchProjects();
            setProject(newProj);
            router.push('/editor');
          }}
          style={({ pressed }) => [pressed && { opacity: 0.9 }]}
        >
          <LinearGradient colors={gradients.primary} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[styles.newProject, shadow.glow]}>
            <View style={styles.newIcon}>
              <Ionicons name="add" size={26} color={colors.onPrimary} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.newTitle}>Novo projeto</Text>
              <Text style={styles.newSub}>Comece do zero com suas mídias</Text>
            </View>
            <Ionicons name="chevron-forward" size={22} color={colors.onPrimary} />
          </LinearGradient>
        </Pressable>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Projetos recentes</Text>
          <Text style={styles.count}>{projects.length}</Text>
        </View>

        {projects.map((p) => (
          <Pressable
            key={p.id}
            onPress={() => {
              setProject(p);
              router.push('/editor');
            }}
            style={({ pressed }) => [styles.projectCard, pressed && { opacity: 0.92 }]}
          >
            <View style={styles.thumbWrap}>
              <Image source={{ uri: p.thumb }} style={styles.thumb} contentFit="cover" transition={200} />
              <View style={styles.durationBadge}>
                <Ionicons name="play" size={10} color={colors.onPrimary} />
                <Text style={styles.durationText}>{p.duration}</Text>
              </View>
            </View>
            <View style={styles.projectInfo}>
              <Text style={styles.projectTitle} numberOfLines={1}>{p.title}</Text>
              <Text style={styles.projectDate}>{p.date}</Text>
            </View>
            <Pressable hitSlop={12} onPress={() => openMenu(p)} style={styles.menuBtn}>
              <Ionicons name="ellipsis-horizontal" size={20} color={colors.textSecondary} />
            </Pressable>
          </Pressable>
        ))}
      </ScrollView>

      <PromptModal
        visible={promptVisible}
        title="Renomear Projeto"
        defaultValue={selectedProject?.title || ''}
        onClose={() => setPromptVisible(false)}
        onSubmit={handleRenameSubmit}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing.lg, paddingBottom: spacing.md },
  headerTitle: { color: colors.textPrimary, fontSize: fontSize.xxl, fontWeight: fontWeight.bold, ...androidFont },
  headerActions: { flexDirection: 'row', gap: spacing.xs },
  iconBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  newProject: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginHorizontal: spacing.lg, padding: spacing.lg, borderRadius: radius.xl, marginTop: spacing.sm },
  newIcon: { width: 48, height: 48, borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center' },
  newTitle: { color: colors.onPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.bold, ...androidFont },
  newSub: { color: 'rgba(255,255,255,0.8)', fontSize: fontSize.sm, marginTop: 2, ...androidFont },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing.lg, marginTop: spacing.xl, marginBottom: spacing.md },
  sectionTitle: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.semibold, ...androidFont },
  count: { color: colors.textTertiary, fontSize: fontSize.sm, ...androidFont },
  projectCard: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginHorizontal: spacing.lg, marginBottom: spacing.md, backgroundColor: colors.surface, borderRadius: radius.lg, padding: spacing.sm },
  thumbWrap: { width: 96, height: 64, borderRadius: radius.md, overflow: 'hidden' },
  thumb: { width: '100%', height: '100%' },
  durationBadge: { position: 'absolute', bottom: 4, right: 4, flexDirection: 'row', alignItems: 'center', gap: 3, backgroundColor: 'rgba(0,0,0,0.65)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6 },
  durationText: { color: colors.onPrimary, fontSize: 10, fontWeight: fontWeight.medium, ...androidFont },
  projectInfo: { flex: 1 },
  projectTitle: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, ...androidFont },
  projectDate: { color: colors.textTertiary, fontSize: fontSize.sm, marginTop: 3, ...androidFont },
  menuBtn: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
});
