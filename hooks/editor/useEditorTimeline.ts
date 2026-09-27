import { useCallback } from 'react';
import { useEditorStore, LocalMediaItem, LocalAudioItem, LocalTextItem, SubtitleSegment } from '@/services/editorState';
import { globalToLocalTime } from '@/services/timelineService';

export function useEditorTimeline() {
  const store = useEditorStore();

  const addClips = useCallback((items: LocalMediaItem[]) => store.addClips(items), [store]);
  const addOverlays = useCallback((items: LocalMediaItem[]) => store.addOverlays(items), [store]);
  const deleteClip = useCallback((id: string) => store.deleteClip(id), [store]);
  const splitClip = useCallback((id: string, splitPointMs: number) => store.splitClip(id, splitPointMs), [store]);
  const reorderClips = useCallback((startIndex: number, endIndex: number) => store.reorderClips(startIndex, endIndex), [store]);
  const trimClip = useCallback((id: string, trimStart: number, trimEnd: number) => store.trimClip(id, trimStart, trimEnd), [store]);
  const duplicateClip = useCallback((id: string) => store.duplicateClip(id), [store]);

  const addAudio = useCallback((item: Omit<LocalAudioItem, 'id' | 'zIndex' | 'visible' | 'locked'>) => store.addAudio(item), [store]);
  const deleteAudio = useCallback((id: string) => store.deleteAudio(id), [store]);
  const duplicateAudio = useCallback((id: string) => store.duplicateAudio(id), [store]);

  const addText = useCallback((item: Omit<LocalTextItem, 'id' | 'zIndex' | 'visible' | 'locked' | 'name' | 'style' | 'animation' | 'keyframes' | 'transform' | 'opacity'>) => store.addText(item), [store]);
  const deleteText = useCallback((id: string) => store.deleteText(id), [store]);

  const addSubtitles = useCallback((segments: SubtitleSegment[]) => store.addSubtitles(segments), [store]);
  const deleteSubtitle = useCallback((id: string) => store.deleteSubtitle(id), [store]);

  const undo = useCallback(() => store.undo(), [store]);
  const redo = useCallback(() => store.redo(), [store]);

  // DELETE UNIVERSAL PARA A NOVA BARRA DE FERRAMENTAS
  const deleteSelected = useCallback((selectedSubtitleId?: string | null) => {
    const layerId = store.selectedLayerId;
    if (layerId) {
      if (store.config.clips.some(c => c.id === layerId)) {
        store.deleteClip(layerId);
        return true;
      }
      if (store.config.texts.some(t => t.id === layerId)) {
        store.deleteText(layerId);
        return true;
      }
      if (store.config.audios.some(a => a.id === layerId)) {
        store.deleteAudio(layerId);
        return true;
      }
    }
    if (selectedSubtitleId) {
      store.deleteSubtitle(selectedSubtitleId);
      return true;
    }
    return false;
  }, [store]);

  // DUPLICATE UNIVERSAL PARA A NOVA BARRA DE FERRAMENTAS
  const duplicateSelected = useCallback(() => {
    const layerId = store.selectedLayerId;
    if (layerId) {
      if (store.config.clips.some(c => c.id === layerId)) {
        store.duplicateClip(layerId);
        return true;
      }
      if (store.config.audios.some(a => a.id === layerId)) {
        store.duplicateAudio(layerId);
        return true;
      }
      // Você poderia adicionar textos aqui se quisesse
    }
    return false;
  }, [store]);

  // SPLIT UNIVERSAL (Corta o vídeo ou áudio exato debaixo do playhead)
  const splitAtCurrentTime = useCallback((currentTimeMs: number) => {
    const layerId = store.selectedLayerId;

    if (layerId) {
      // 1. Tentar Dividir Clipe Selecionado (Main ou Overlay)
      const clip = store.config.clips.find(c => c.id === layerId);
      if (clip) {
        const localTime = currentTimeMs - clip.startTime;
        if (localTime > 100 && localTime < clip.duration - 100) {
           store.splitClip(clip.id, localTime);
           return true;
        }
      }

      // 2. Tentar Dividir Áudio Selecionado
      const audio = store.config.audios.find(a => a.id === layerId);
      if (audio) {
        const localTime = currentTimeMs - audio.startTime;
        if (localTime > 100 && localTime < audio.duration - 100) {
           const currentAbsoluteStart = (audio.trimStart || 0) + localTime;
           const p1 = { ...audio, id: `aud_${Date.now()}_1`, duration: localTime, trimEnd: currentAbsoluteStart };
           const p2 = { ...audio, id: `aud_${Date.now()}_2`, duration: audio.duration - localTime, trimStart: currentAbsoluteStart, startTime: audio.startTime + localTime };
           const newAudios = store.config.audios.filter(a => a.id !== layerId);
           newAudios.push(p1, p2);
           store.updateConfig({ audios: newAudios });
           return true;
        }
      }
    }

    // 3. Se nada estritamente compatível com "split" estiver selecionado, corta a timeline principal
    const clipInfo = globalToLocalTime(currentTimeMs, store.config.clips);
    if (clipInfo.activeClip && !clipInfo.activeClip.isOverlay) {
       const localTime = clipInfo.clipElapsedTimelineMs;
       if (localTime > 100 && localTime < clipInfo.activeClip.duration - 100) {
          store.splitClip(clipInfo.activeClip.id, localTime);
          return true;
       }
    }

    return false;
  }, [store]);

  return {
    clips: store.config.clips || [],
    audios: store.config.audios || [],
    texts: store.config.texts || [],
    subtitles: store.config.subtitles || [],
    historyIndex: store.historyIndex,
    historyLength: store.history?.length || 0,
    selectedLayerId: store.selectedLayerId,
    setSelectedLayerId: store.setSelectedLayerId,
    addClips,
    addOverlays,
    deleteClip,
    splitClip,
    reorderClips,
    trimClip,
    duplicateClip,
    addAudio,
    deleteAudio,
    duplicateAudio,
    addText,
    deleteText,
    addSubtitles,
    deleteSubtitle,
    deleteSelected,
    duplicateSelected,
    splitAtCurrentTime,
    undo,
    redo,
  };
}
