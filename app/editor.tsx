// Powered by OnSpace.AI
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { View, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomSheet } from '@/components';
import { useAlert } from '@/template';
import { Timeline } from '@/components/editor/Timeline';
import { Toolbar, SelectionContext } from '@/components/editor/Toolbar';
import { EditorHeader } from '@/components/editor/EditorHeader';
import { EditorCanvas } from '@/components/editor/EditorCanvas';
import { EditPanel } from '@/components/editor/EditPanel';
import { AudioPanel } from '@/components/editor/AudioPanel';
import { TextPanel } from '@/components/editor/TextPanel';
import { SubtitlePanel } from '@/components/editor/SubtitlePanel';
import { OverlayPanel } from '@/components/editor/OverlayPanel';
import { VFXPanel } from '@/components/editor/VFXPanel';
import { TransitionPanel } from '@/components/editor/TransitionPanel';
import { ColorPanel } from '@/components/editor/ColorPanel';
import { CanvasPanel } from '@/components/editor/CanvasPanel';
import { LayersPanel } from '@/components/editor/LayersPanel';
import { TransformPanel } from '@/components/editor/TransformPanel';
import { AnimationPanel } from '@/components/editor/AnimationPanel';
import { StickerPanel } from '@/components/editor/StickerPanel';
import { TextAnimationPanel } from '@/components/editor/TextAnimationPanel';
import { colors, spacing } from '@/constants/theme';
import { useEditorStore } from '@/services/editorState';
import { useEditorPlayback } from '@/hooks/editor/useEditorPlayback';
import { useEditorAudio } from '@/hooks/editor/useEditorAudio';
import { useEditorTimeline } from '@/hooks/editor/useEditorTimeline';

const PANEL_TITLES: Record<string, string> = {
  canvas: 'Formato',
  layers: 'Camadas',
  edit: 'Editar',
  audio: 'Áudio',
  text: 'Texto',
  overlay: 'Sobrepor',
  effects: 'Efeitos',
  transition: 'Transição',
  color: 'Ajustes de Cor',
  captions: 'Legendas',
  transform: 'Transformar',
  animation: 'Animação',
  stickers: 'Stickers',
  text_anim: 'Animação de Texto',
};

export default function EditorScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { showAlert } = useAlert();

  const { currentProject, config } = useEditorStore();
  const {
    clips,
    texts,
    subtitles,
    audios,
    historyIndex,
    historyLength,
    selectedLayerId,
    setSelectedLayerId,
    reorderClips,
    trimClip,
    addText,
    deleteSelected,
    duplicateSelected,
    splitAtCurrentTime,
    undo,
    redo,
  } = useEditorTimeline();

  const playback = useEditorPlayback();
  useEditorAudio({
    currentTime: playback.currentTime,
    isPlaying: playback.isPlaying,
  });

  const [panel, setPanel] = useState<string | null>(null);
  const [selectedSubtitleId, setSelectedSubtitleId] = useState<string | null>(null);

  useEffect(() => {
    if (!currentProject) {
      router.replace('/(tabs)');
    }
  }, [currentProject]);

  if (!currentProject) return null;

  const handleSeek = useCallback((timeMs: number) => {
    playback.seek(timeMs);
  }, [playback]);

  const handleSeekBegin = useCallback(() => {
    playback.setIsSeeking(true);
  }, [playback]);

  const handleSeekEnd = useCallback(() => {
    playback.setIsSeeking(false);
  }, [playback]);

  const selectedClipId = clips.find(c => c.id === selectedLayerId)?.id || null;
  const selectedTextId = texts.find(t => t.id === selectedLayerId)?.id || null;
  const selectedAudioId = audios.find(a => a.id === selectedLayerId)?.id || null;

  const selectionContext: SelectionContext = useMemo(() => {
    if (selectedSubtitleId) return 'subtitle';
    if (selectedClipId) {
       const clip = clips.find(c => c.id === selectedClipId);
       return clip?.isOverlay ? 'overlay' : 'main_clip';
    }
    if (selectedTextId) return 'text';
    if (selectedAudioId) return 'audio';
    return 'none';
  }, [selectedSubtitleId, selectedClipId, selectedTextId, selectedAudioId, clips]);

  const handleToolbarSelect = useCallback((toolId: string) => {
    // 1. Ações Diretas
    if (toolId === 'delete') {
      const deleted = deleteSelected(selectedSubtitleId);
      if (deleted) {
        setSelectedSubtitleId(null);
      } else {
        showAlert('Excluir', 'Selecione um elemento na timeline para excluir.');
      }
      return;
    }

    if (toolId === 'split') {
      const splitOk = splitAtCurrentTime(playback.currentTime);
      if (!splitOk) {
        showAlert('Dividir', 'Posicione o playhead sobre um clipe ou áudio válido para dividir.');
      }
      return;
    }

    if (toolId === 'duplicate') {
      const dupOk = duplicateSelected();
      if (!dupOk) {
        showAlert('Duplicar', 'Selecione um clipe ou áudio para duplicar.');
      }
      return;
    }

    // Ação "Edit" sem seleção ativa seleciona a mídia principal atual! (Estilo CapCut)
    if (toolId === 'edit' && selectionContext === 'none') {
       const active = playback.activeClipInfo.activeClip;
       if (active) setSelectedLayerId(active.id);
       return;
    }

    // 2. Abertura de Painéis
    setPanel(toolId);
  }, [deleteSelected, selectedSubtitleId, splitAtCurrentTime, playback.currentTime, duplicateSelected, selectionContext, playback.activeClipInfo.activeClip, setSelectedLayerId, showAlert]);

  // Limpa o painel se a seleção sumir/mudar (opcional)
  useEffect(() => {
    if (selectionContext === 'none' && (panel === 'animation' || panel === 'transform' || panel === 'text_anim')) {
      setPanel(null);
    }
  }, [selectionContext]);

  return (
    <View style={styles.container}>
      <EditorHeader title={currentProject?.title} />

      <EditorCanvas
        canvas={config.canvas}
        activeClipInfo={playback.activeClipInfo}
        currentTime={playback.currentTime}
        duration={playback.duration}
        isPlaying={playback.isPlaying}
        isSeeking={playback.isSeeking}
        clips={clips}
        texts={texts}
        subtitles={subtitles}
        subtitleStyle={config.subtitleStyle}
        historyIndex={historyIndex}
        historyLength={historyLength}
        onTogglePlay={playback.togglePlay}
        onUndo={undo}
        onRedo={redo}
        onPlaybackStatusUpdate={playback.onPlayerStatusUpdate}
      />

      <View style={styles.timelineWrap}>
        <Timeline
          clips={clips}
          texts={texts}
          subtitles={subtitles}
          selectedClipId={selectedClipId}
          selectedTextId={selectedTextId}
          selectedAudioId={selectedAudioId}
          selectedSubtitleId={selectedSubtitleId}
          onSelectClip={(id) => { setSelectedLayerId(id); setSelectedSubtitleId(null); setPanel(null); }}
          onSelectText={(id) => { setSelectedLayerId(id); setSelectedSubtitleId(null); setPanel(null); }}
          onSelectAudio={(id) => { setSelectedLayerId(id); setSelectedSubtitleId(null); setPanel(null); }}
          onSelectSubtitle={(id) => { setSelectedSubtitleId(id); setSelectedLayerId(null); setPanel(null); }}
          onSelectTransition={(id) => { setSelectedLayerId(id); setPanel('transition'); }}
          position={playback.currentTime}
          onSeek={handleSeek}
          onSeekBegin={handleSeekBegin}
          onSeekEnd={handleSeekEnd}
          onAdd={() => router.push('/import')}
          onMoveLeft={(idx) => reorderClips(idx, idx - 1)}
          onMoveRight={(idx) => reorderClips(idx, idx + 1)}
          onTrimClip={trimClip}
        />
      </View>

      <View style={[styles.toolbarWrap, { paddingBottom: insets.bottom + spacing.sm }]}>
        <Toolbar active={panel ?? undefined} selectionContext={selectionContext} onSelect={handleToolbarSelect} />
      </View>

      <BottomSheet visible={!!panel} onClose={() => setPanel(null)} title={panel ? PANEL_TITLES[panel] : ''} scroll>
        {panel === 'canvas' ? <CanvasPanel onAction={(m) => showAlert('Canvas', m)} /> : null}
        {panel === 'layers' ? <LayersPanel onAction={(m) => showAlert('Camadas', m)} /> : null}
        {panel === 'edit' ? <EditPanel onAction={(m) => showAlert('Edição', m)} selectedClipId={selectedClipId} timelinePosition={playback.currentTime} /> : null}
        {panel === 'audio' ? <AudioPanel onAction={(m) => showAlert('Ação', m)} /> : null}
        {panel === 'text' ? <TextPanel onAction={async (m) => {
            if (m === 'add_text') await addText({ text: 'Novo Texto', startTime: playback.currentTime, duration: 3000, position: { x: 50, y: 50 } });
            if (m === 'import_font') router.push('/import?mode=font');
        }} selectedTextId={selectedTextId} /> : null}
        {panel === 'captions' ? <SubtitlePanel onAction={(m) => showAlert('Legenda', m)} timelinePosition={playback.currentTime} /> : null}
        {panel === 'overlay' ? <OverlayPanel onAction={(m) => showAlert('Ação', m)} /> : null}
        {panel === 'effects' ? <VFXPanel onAction={(m) => showAlert('Efeito', m)} selectedClipId={selectedClipId} timelinePosition={playback.currentTime} /> : null}
        {panel === 'transition' ? <TransitionPanel onAction={(m) => showAlert('Transição', m)} selectedClipId={selectedClipId} /> : null}
        {panel === 'color' ? <ColorPanel onAction={(m) => showAlert('Cor', m)} selectedClipId={selectedClipId} timelinePosition={playback.currentTime} /> : null}
        {panel === 'transform' ? <TransformPanel onAction={(m) => showAlert('Transformação', m)} selectedLayerId={selectedLayerId} timelinePosition={playback.currentTime} /> : null}
        {panel === 'animation' ? <AnimationPanel onAction={(m) => showAlert('Animação', m)} selectedLayerId={selectedLayerId} /> : null}
        {panel === 'stickers' ? <StickerPanel onAction={(m) => showAlert('Sticker', m)} /> : null}
        {panel === 'text_anim' ? <TextAnimationPanel onAction={(m) => showAlert('Animação de Texto', m)} selectedTextId={selectedTextId} /> : null}
      </BottomSheet>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  timelineWrap: {
    height: 175,
    backgroundColor: colors.backgroundElevated,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },
  toolbarWrap: {
    backgroundColor: colors.background,
    paddingTop: spacing.md,
  },
});
