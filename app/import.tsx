// Powered by OnSpace.AI
import React, { useState } from 'react';
import { View, Text, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import * as DocumentPicker from 'expo-document-picker';
import * as Font from 'expo-font';
import { Button, SegmentedControl } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, LocalMediaItem, LocalAudioItem } from '@/services/editorState';
import { validateFile, safeCopyToInternal, FileValidationError } from '@/services/fileValidationService';
import { useAlert } from '@/template';

const ERROR_MESSAGES: Record<FileValidationError, string> = {
  FILE_NOT_FOUND: 'Arquivo não encontrado.',
  FILE_TOO_LARGE: 'O arquivo é muito grande (máximo 500MB).',
  UNSUPPORTED_FORMAT: 'Formato não suportado pelo editor.',
  CORRUPTED_OR_INVALID: 'Arquivo corrompido ou inválido.',
  INVALID_DIMENSIONS: 'Resolução muito alta ou inválida.',
  INVALID_DURATION: 'Duração do arquivo é inválida.',
  MISSING_STREAM: 'O arquivo não contém trilhas de áudio/vídeo válidas.',
  COPY_FAILED: 'Falha ao processar o arquivo internamente.'
};

export default function ImportScreen() {
  const router = useRouter();
  const { mode } = useLocalSearchParams<{ mode: string }>();
  const insets = useSafeAreaInsets();
  const { showAlert } = useAlert();
  const { addClips, addAudio, addOverlays } = useEditorStore();
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState('Vídeo/Foto');

  const pickMedia = async () => {
    try {
      const permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permissionResult.granted) {
        showAlert('Permissão Negada', 'Precisamos de permissão para acessar suas mídias.');
        return;
      }

      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.All,
        allowsMultipleSelection: true,
        quality: 1,
      });

      if (result.canceled || !result.assets) return;

      setLoading(true);
      const importedItems: any[] = [];
      let successCount = 0;
      let failCount = 0;

      for (const asset of result.assets) {
        const validation = await validateFile(asset.uri, undefined, {
            mimeType: asset.mimeType || undefined,
            filename: asset.fileName || undefined,
            size: (asset as any).fileSize || 0
        });

        if (validation.isValid && validation.metadata) {
          const destUri = await safeCopyToInternal(validation.metadata);
          if (destUri) {
            const meta = validation.metadata;
            importedItems.push({
              id: `clip_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
              uri: destUri,
              type: meta.type === 'video' ? 'video' : 'photo',
              name: meta.name,
              duration: meta.type === 'photo' ? 3000 : meta.durationMs,
              originalDuration: meta.durationMs || 3000,
              trimStart: 0,
              trimEnd: meta.type === 'photo' ? 3000 : meta.durationMs,
              width: meta.width,
              height: meta.height,
              zIndex: 0,
              visible: true,
              locked: false,
              startTime: 0,
              isGIF: meta.type === 'gif',
              speed: 1.0,
              reverse: false
            });
            successCount++;
          } else {
            failCount++;
          }
        } else {
          failCount++;
        }
      }

      if (successCount > 0) {
        if (mode === 'overlay') await addOverlays(importedItems);
        else await addClips(importedItems);
      }

      setLoading(false);
      if (failCount > 0) {
        showAlert('Importação Concluída', `${successCount} arquivos importados. ${failCount} arquivos falharam.`);
      }
      if (successCount > 0) router.replace('/editor');

    } catch (error) {
      setLoading(false);
      showAlert('Erro', 'Erro inesperado ao importar mídia.');
    }
  };

  const pickAudio = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: 'audio/*',
        copyToCacheDirectory: true,
        multiple: true
      });

      if (result.canceled || !result.assets) return;

      setLoading(true);
      let successCount = 0;
      let failCount = 0;

      for (const asset of result.assets) {
        const validation = await validateFile(asset.uri, 'audio', {
            mimeType: asset.mimeType || undefined,
            filename: asset.name || undefined,
            size: asset.size || 0
        });

        if (validation.isValid && validation.metadata) {
          const destUri = await safeCopyToInternal(validation.metadata);
          if (destUri) {
            const meta = validation.metadata;
            const newAudio: Omit<LocalAudioItem, 'id' | 'zIndex' | 'visible' | 'locked'> = {
              uri: destUri,
              name: meta.name,
              startTime: 0,
              duration: meta.durationMs,
              originalDuration: meta.durationMs,
              trimStart: 0,
              trimEnd: meta.durationMs,
              volume: 1.0,
              muted: false,
              fadeIn: 0,
              fadeOut: 0,
            };
            await addAudio(newAudio);
            successCount++;
          } else {
            failCount++;
          }
        } else {
          failCount++;
        }
      }

      setLoading(false);
      if (failCount > 0) {
        showAlert('Importação de Áudio', `${successCount} arquivos importados.`);
      }
      if (successCount > 0) router.replace('/editor');

    } catch (error) {
      setLoading(false);
      showAlert('Erro', 'Erro ao importar áudio.');
    }
  };

  const pickFont = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['font/ttf', 'font/otf', 'application/x-font-ttf', 'application/x-font-otf'],
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets) return;

      setLoading(true);
      const asset = result.assets[0];
      const validation = await validateFile(asset.uri, 'font', {
          mimeType: asset.mimeType || undefined,
          filename: asset.name || undefined,
          size: asset.size || 0
      });

      if (validation.isValid && validation.metadata) {
        const destUri = await safeCopyToInternal(validation.metadata);
        if (destUri) {
          // Register font with Expo Font so it's usable in the app session
          const fontName = validation.metadata.name.split('.')[0];
          await Font.loadAsync({ [fontName]: destUri });

          setLoading(false);
          showAlert('Sucesso', `Fonte ${fontName} importada com sucesso! Agora você pode selecioná-la no painel de texto.`);
          router.replace('/editor');
        } else {
          throw new Error('COPY_FAILED');
        }
      } else {
        setLoading(false);
        showAlert('Erro', ERROR_MESSAGES[validation.error || 'UNSUPPORTED_FORMAT']);
      }
    } catch (error) {
      setLoading(false);
      showAlert('Erro', 'Erro ao importar fonte.');
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
        <Pressable hitSlop={12} onPress={() => router.back()} style={styles.iconBtn}>
          <Ionicons name="close" size={24} color={colors.textPrimary} />
        </Pressable>
        <Text style={styles.headerTitle}>{mode === 'overlay' ? 'Adicionar Sobreposição' : mode === 'font' ? 'Importar Fonte' : 'Importar'}</Text>
        <View style={styles.iconBtn} />
      </View>

      <View style={styles.tabs}>
        {mode !== 'font' && <SegmentedControl options={['Vídeo/Foto', 'Áudio']} value={tab} onChange={setTab} />}
      </View>

      <View style={styles.content}>
        {loading ? (
          <View style={styles.loadingWrap}>
            <ActivityIndicator size="large" color={colors.primary} />
            <Text style={styles.loadingText}>Validando e importando arquivos...</Text>
          </View>
        ) : (
          <View style={styles.hintBox}>
            <View style={styles.iconCircle}>
              <Ionicons
                name={mode === 'font' ? "text-outline" : tab === 'Áudio' ? "musical-notes-outline" : "layers-outline"}
                size={40}
                color={colors.primaryVariant}
              />
            </View>
            <Text style={styles.hintTitle}>
              {mode === 'font' ? 'Selecione uma Fonte' : tab === 'Áudio' ? 'Selecione Música' : 'Selecione Mídia'}
            </Text>
            <Text style={styles.hintSub}>
              {mode === 'font' ? 'Suportamos arquivos .ttf e .otf' : 'Suportamos MP4, MOV, JPG, PNG, GIF e MP3.'}
            </Text>
            <Button
              title={mode === 'font' ? "Escolher Fonte" : tab === 'Áudio' ? "Escolher Áudio" : "Abrir Galeria"}
              icon={mode === 'font' ? "text-outline" : tab === 'Áudio' ? "document-text-outline" : "library-outline"}
              onPress={mode === 'font' ? pickFont : tab === 'Áudio' ? pickAudio : pickMedia}
              style={styles.pickBtn}
            />
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing.lg, paddingBottom: spacing.md },
  iconBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.semibold, ...androidFont },
  tabs: { paddingHorizontal: spacing.lg, marginTop: spacing.md },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: spacing.xl },
  loadingWrap: { alignItems: 'center', gap: spacing.sm },
  loadingText: { color: colors.textPrimary, fontSize: fontSize.md, fontWeight: 'bold', ...androidFont, textAlign: 'center' },
  hintBox: { alignItems: 'center', backgroundColor: colors.surface, borderRadius: radius.xl, padding: spacing.xl, width: '100%', borderWidth: 1, borderColor: colors.border },
  iconCircle: { width: 80, height: 80, borderRadius: 40, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center', marginBottom: spacing.lg },
  hintTitle: { color: colors.textPrimary, fontSize: fontSize.xl, fontWeight: fontWeight.bold, marginBottom: spacing.sm, ...androidFont },
  hintSub: { color: colors.textSecondary, fontSize: fontSize.md, textAlign: 'center', lineHeight: 20, marginBottom: spacing.xl, ...androidFont },
  pickBtn: { width: '100%' },
});
