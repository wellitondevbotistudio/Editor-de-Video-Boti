import { create } from 'zustand';
import { ProjectData, saveProjectLocal } from './projectService';

export type AspectRatio = '16:9' | '9:16' | '1:1' | '4:5' | '4:3';

export interface ProjectCanvas {
  aspectRatio: AspectRatio;
  width: number;
  height: number;
}

export interface VFXEffect {
  id: string;
  type: 'blur' | 'glow' | 'vignette' | string;
  enabled: boolean;
  intensity: number; // 0 to 100
  keyframes?: VFXKeyframe[];
}

export interface VFXKeyframe {
  id: string;
  time: number;
  value: number;
}

export type KeyframeProperty = 'x' | 'y' | 'scale' | 'rotation' | 'opacity' | 'brightness' | 'contrast' | 'saturation';

export interface Keyframe {
  id: string;
  time: number; // local time in ms relative to item start
  property: KeyframeProperty;
  value: number;
}

export interface Transform {
  x: number; // 0 to 100
  y: number; // 0 to 100
  scale: number;
  rotation: number;
  opacity: number;
  flipH?: boolean;
  flipV?: boolean;
}

export interface CropArea {
  x: number; // 0 to 1
  y: number;
  width: number;
  height: number;
}

export interface LayerMetadata {
  zIndex: number;
  visible: boolean;
  locked: boolean;
  name: string;
}

export interface LocalMediaItem extends LayerMetadata {
  id: string;
  uri: string;
  type: 'video' | 'photo';
  duration: number; // ativa (ms)
  originalDuration: number;
  trimStart: number;
  trimEnd: number;
  width?: number;
  height?: number;
  adjustments: {
    brightness: number;
    contrast: number;
    saturation: number;
  };
  filter: string;
  nextTransition: {
    type: string;
    duration: number;
  } | null;
  effects: VFXEffect[];
  isOverlay: boolean;
  isGIF: boolean;
  startTime: number; // global ms
  transform: Transform;
  keyframes: Keyframe[];
  speed: number;
  reverse: boolean;
  crop?: CropArea;
}

export interface LocalAudioItem extends LayerMetadata {
  id: string;
  uri: string;
  startTime: number;
  duration: number;
  originalDuration: number;
  trimStart: number;
  trimEnd: number;
  volume: number;
  muted: boolean;
  fadeIn: number;
  fadeOut: number;
}

export type TextAnimationMode = 'full' | 'word' | 'letter';
export type TextAnimationPreset = 'none' | 'fade' | 'slide_up' | 'slide_down' | 'slide_left' | 'slide_right' | 'zoom' | 'pop';

export interface TextStyle {
  fontFamily: string;
  fontSize: number;
  color: string;
  fontWeight: string;
  textAlign: 'left' | 'center' | 'right';
  letterSpacing: number;
  lineHeight: number;
  strokeColor: string;
  strokeWidth: number;
  shadowEnabled: boolean;
  shadowColor: string;
  shadowBlur: number;
  backgroundEnabled: boolean;
  backgroundColor: string;
  backgroundOpacity: number;
}

export interface LocalTextItem extends LayerMetadata {
  id: string;
  text: string;
  startTime: number;
  duration: number;
  position: { x: number; y: number };
  opacity: number;
  transform: Transform;
  keyframes: Keyframe[];
  style: TextStyle;
  animation: {
    mode: TextAnimationMode;
    presetIn: TextAnimationPreset;
    presetOut: TextAnimationPreset;
    duration: number;
    delay: number;
  };
}

export interface SubtitleSegment {
  id: string;
  text: string;
  startTime: number;
  endTime: number;
  enabled: boolean;
}

export interface SubtitleStyle {
  fontFamily: string;
  fontSize: number;
  color: string;
  strokeColor: string;
  strokeWidth: number;
  positionY: number;
  backgroundEnabled: boolean;
  backgroundColor: string;
  backgroundOpacity: number;
}

export interface EditorProjectConfig {
  canvas: ProjectCanvas;
  clips: LocalMediaItem[];
  texts: LocalTextItem[];
  audios: LocalAudioItem[];
  subtitles: SubtitleSegment[];
  subtitleStyle: SubtitleStyle;
}

interface EditorStore {
  currentProject: ProjectData | null;
  config: EditorProjectConfig;
  history: EditorProjectConfig[];
  historyIndex: number;
  selectedLayerId: string | null;

  setProject: (project: ProjectData) => void;
  updateConfig: (config: Partial<EditorProjectConfig>, saveToHistory?: boolean) => Promise<void>;
  setSelectedLayerId: (id: string | null) => void;

  // Actions
  toggleLayerVisibility: (id: string) => Promise<void>;
  toggleLayerLock: (id: string) => Promise<void>;
  moveLayerZ: (id: string, action: 'front' | 'back' | 'up' | 'down') => Promise<void>;

  addClips: (items: LocalMediaItem[]) => Promise<void>;
  addOverlays: (items: LocalMediaItem[]) => Promise<void>;
  deleteClip: (id: string) => Promise<void>;
  splitClip: (id: string, splitPointMs: number) => Promise<void>;
  reorderClips: (startIndex: number, endIndex: number) => Promise<void>;
  trimClip: (id: string, trimStart: number, trimEnd: number) => Promise<void>;
  duplicateClip: (id: string) => Promise<void>;
  updateClipAdjustments: (id: string, adjustments: Partial<LocalMediaItem['adjustments']>) => Promise<void>;
  updateClipFilter: (id: string, filter: string) => Promise<void>;
  updateClipTransition: (id: string, transition: LocalMediaItem['nextTransition']) => Promise<void>;
  updateClipEffects: (id: string, effects: VFXEffect[]) => Promise<void>;
  updateClipTransform: (id: string, transform: Partial<Transform>) => Promise<void>;
  updateClipEditing: (id: string, partial: Partial<Pick<LocalMediaItem, 'speed' | 'reverse' | 'crop'>>) => Promise<void>;
  freezeFrame: (id: string, timelinePos: number) => Promise<void>;

  addKeyframe: (layerId: string, localTime: number, property: KeyframeProperty, value: number) => Promise<void>;
  deleteKeyframe: (layerId: string, keyframeId: string) => Promise<void>;
  clearAnimation: (layerId: string) => Promise<void>;

  addVFXKeyframe: (clipId: string, vfxId: string, localTime: number, value: number) => Promise<void>;
  deleteVFXKeyframe: (clipId: string, vfxId: string, keyframeId: string) => Promise<void>;
  clearVFXAnimation: (clipId: string, vfxId: string) => Promise<void>;

  updateCanvas: (aspectRatio: AspectRatio) => Promise<void>;
  addAudio: (item: Omit<LocalAudioItem, 'id' | 'zIndex' | 'visible' | 'locked'>) => Promise<void>;
  updateAudio: (id: string, partial: Partial<LocalAudioItem>) => Promise<void>;
  deleteAudio: (id: string) => Promise<void>;
  duplicateAudio: (id: string) => Promise<void>;

  addText: (item: Omit<LocalTextItem, 'id' | 'zIndex' | 'visible' | 'locked' | 'name' | 'style' | 'animation' | 'keyframes' | 'transform' | 'opacity'>) => Promise<void>;
  updateText: (id: string, partial: Partial<LocalTextItem>) => Promise<void>;
  deleteText: (id: string) => Promise<void>;

  addSubtitles: (segments: SubtitleSegment[]) => Promise<void>;
  updateSubtitle: (id: string, partial: Partial<SubtitleSegment>) => Promise<void>;
  deleteSubtitle: (id: string) => Promise<void>;
  clearSubtitles: () => Promise<void>;
  updateSubtitleStyle: (style: Partial<SubtitleStyle>) => Promise<void>;
  addManualSubtitle: (startTime: number) => Promise<void>;

  undo: () => Promise<void>;
  redo: () => Promise<void>;
}

const DEFAULT_TEXT_STYLE: TextStyle = {
  fontFamily: 'System',
  fontSize: 48,
  color: '#FFFFFF',
  fontWeight: '600',
  textAlign: 'center',
  letterSpacing: 0,
  lineHeight: 1.2,
  strokeColor: '#000000',
  strokeWidth: 0,
  shadowEnabled: false,
  shadowColor: '#000000',
  shadowBlur: 4,
  backgroundEnabled: false,
  backgroundColor: '#000000',
  backgroundOpacity: 0.5,
};

const DEFAULT_SUBTITLE_STYLE: SubtitleStyle = {
  fontFamily: 'Arial',
  fontSize: 48,
  color: '#FFFFFF',
  strokeColor: '#000000',
  strokeWidth: 2,
  positionY: 85,
  backgroundEnabled: false,
  backgroundColor: '#000000',
  backgroundOpacity: 0.5,
};

export const CANVAS_PRESETS: Record<AspectRatio, { w: number; h: number }> = {
  '16:9': { w: 1920, h: 1080 },
  '9:16': { w: 1080, h: 1920 },
  '1:1': { w: 1080, h: 1080 },
  '4:5': { w: 1080, h: 1350 },
  '4:3': { w: 1440, h: 1080 }
};

const DEFAULT_CANVAS: ProjectCanvas = {
  aspectRatio: '16:9',
  width: 1920,
  height: 1080
};

const DEFAULT_TRANSFORM: Transform = {
  x: 50,
  y: 50,
  scale: 1,
  rotation: 0,
  opacity: 1,
  flipH: false,
  flipV: false
};

export const useEditorStore = create<EditorStore>((set, get) => ({
  currentProject: null,
  config: { canvas: DEFAULT_CANVAS, clips: [], texts: [], audios: [], subtitles: [], subtitleStyle: DEFAULT_SUBTITLE_STYLE },
  history: [],
  historyIndex: -1,
  selectedLayerId: null,

  setSelectedLayerId: (id) => set({ selectedLayerId: id }),

  setProject: (project) => {
    let parsedConfig: EditorProjectConfig = {
      canvas: DEFAULT_CANVAS,
      clips: [], texts: [], audios: [], subtitles: [], subtitleStyle: DEFAULT_SUBTITLE_STYLE
    };
    if (project.config) {
      try {
        const parsed = JSON.parse(project.config);
        parsedConfig = {
          canvas: parsed.canvas || DEFAULT_CANVAS,
          clips: (parsed.clips || []).map((c: any, i: number) => ({
            ...c,
            zIndex: c.zIndex ?? 0,
            visible: c.visible ?? true,
            locked: c.locked ?? false,
            name: c.name || `Clip ${i+1}`,
            isOverlay: c.isOverlay ?? false,
            isGIF: c.isGIF ?? false,
            startTime: c.startTime ?? 0,
            transform: { ...DEFAULT_TRANSFORM, ...c.transform },
            keyframes: c.keyframes || [],
            effects: (c.effects || []).map((e: any) => ({ ...e, keyframes: e.keyframes || [] })),
            speed: c.speed ?? 1.0,
            reverse: c.reverse ?? false,
            adjustments: c.adjustments || { brightness: 0, contrast: 0, saturation: 0 }
          })),
          texts: (parsed.texts || []).map((t: any, i: number) => ({
            ...t,
            zIndex: t.zIndex ?? 10,
            visible: t.visible ?? true,
            locked: t.locked ?? false,
            name: t.name || `Texto ${i+1}`,
            transform: { ...DEFAULT_TRANSFORM, ...t.transform },
            keyframes: t.keyframes || [],
            style: { ...DEFAULT_TEXT_STYLE, ...t.style },
            animation: t.animation || { mode: 'full', presetIn: 'none', presetOut: 'none', duration: 500, delay: 100 }
          })),
          audios: (parsed.audios || []).map((a: any, i: number) => ({
            ...a,
            zIndex: a.zIndex ?? 0,
            visible: a.visible ?? true,
            locked: a.locked ?? false,
            name: a.name || `Áudio ${i+1}`,
            fadeIn: a.fadeIn ?? 0,
            fadeOut: a.fadeOut ?? 0
          })),
          subtitles: parsed.subtitles || [],
          subtitleStyle: parsed.subtitleStyle || DEFAULT_SUBTITLE_STYLE,
        };
      } catch (e) {
        console.error('Failed to parse project config JSON', e);
      }
    }
    set({
      currentProject: project,
      config: parsedConfig,
      history: [parsedConfig],
      historyIndex: 0,
      selectedLayerId: null
    });
  },

  updateConfig: async (newPartialConfig, saveToHistory = true) => {
    const { currentProject, config, history, historyIndex } = get();
    if (!currentProject) return;

    const updatedConfig = { ...config, ...newPartialConfig };
    const stringified = JSON.stringify(updatedConfig);

    let totalMs = 0;
    const sequentialClips = updatedConfig.clips.filter(c => !c.isOverlay);
    sequentialClips.forEach((c, idx) => {
      totalMs += (c.duration || 3000);
      if (c.nextTransition && c.nextTransition.type !== 'none' && idx < sequentialClips.length - 1) {
        if (c.nextTransition.type === 'dissolve') {
          totalMs -= c.nextTransition.duration;
        }
      }
    });

    updatedConfig.clips.filter(c => c.isOverlay).forEach(c => {
      const end = c.startTime + c.duration;
      if (end > totalMs) totalMs = end;
    });

    updatedConfig.audios.forEach(a => {
        const end = a.startTime + a.duration;
        if (end > totalMs) totalMs = end;
    });
    updatedConfig.texts.forEach(t => {
      const end = t.startTime + t.duration;
      if (end > totalMs) totalMs = end;
    });
    updatedConfig.subtitles.forEach(s => {
      if (s.endTime > totalMs) totalMs = s.endTime;
    });

    const totalSeconds = Math.floor(Math.max(0, totalMs) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const durationStr = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;

    const updatedProject: ProjectData = {
      ...currentProject,
      duration: durationStr,
      config: stringified,
      thumb: updatedConfig.clips.filter(c => !c.isOverlay)[0]?.uri || updatedConfig.clips[0]?.uri || currentProject.thumb
    };

    // Update state immediately to keep UI responsive
    if (saveToHistory) {
      const newHistory = history.slice(0, historyIndex + 1);
      newHistory.push(updatedConfig);
      if (newHistory.length > 30) newHistory.shift();
      set({
        currentProject: updatedProject,
        config: updatedConfig,
        history: newHistory,
        historyIndex: newHistory.length - 1
      });
    } else {
      set({ currentProject: updatedProject, config: updatedConfig });
    }

    // Save to SQLite in background to avoid blocking UI (especially on Web)
    saveProjectLocal(updatedProject).catch(e => {
        console.error('Failed to save project to SQLite', e);
    });
  },

  // --- LAYER ACTIONS ---
  toggleLayerVisibility: async (id) => {
    const { config } = get();
    const clips = config.clips.map(c => c.id === id ? { ...c, visible: !c.visible } : c);
    const texts = config.texts.map(t => t.id === id ? { ...t, visible: !t.visible } : t);
    const audios = config.audios.map(a => a.id === id ? { ...a, visible: !a.visible } : a);
    await get().updateConfig({ clips, texts, audios });
  },

  toggleLayerLock: async (id) => {
    const { config } = get();
    const clips = config.clips.map(c => c.id === id ? { ...c, locked: !c.locked } : c);
    const texts = config.texts.map(t => t.id === id ? { ...t, locked: !t.locked } : t);
    const audios = config.audios.map(a => a.id === id ? { ...a, locked: !a.locked } : a);
    await get().updateConfig({ clips, texts, audios });
  },

  moveLayerZ: async (id, action) => {
    const { config } = get();
    const allVisuals = [...config.clips, ...config.texts];
    const target = allVisuals.find(v => v.id === id);
    if (!target) return;

    let newClips = [...config.clips];
    let newTexts = [...config.texts];

    const updateItem = (itemId: string, newZ: number) => {
      newClips = newClips.map(c => c.id === itemId ? { ...c, zIndex: newZ } : c);
      newTexts = newTexts.map(t => t.id === itemId ? { ...t, zIndex: newZ } : t);
    };

    const zIndices = allVisuals.map(v => v.zIndex).sort((a, b) => a - b);
    const maxZ = zIndices[zIndices.length - 1] ?? 0;
    const minZ = zIndices[0] ?? 0;

    switch (action) {
      case 'front': updateItem(id, maxZ + 1); break;
      case 'back': updateItem(id, minZ - 1); break;
      case 'up': updateItem(id, target.zIndex + 1); break;
      case 'down': updateItem(id, target.zIndex - 1); break;
    }
    await get().updateConfig({ clips: newClips, texts: newTexts });
  },

  // --- VIDEO ACTIONS ---
  addClips: async (items) => {
    const { config } = get();
    const sanitized = items.map((c, i) => ({
      ...c,
      originalDuration: c.originalDuration || c.duration,
      trimStart: 0,
      trimEnd: c.duration,
      adjustments: { brightness: 0, contrast: 0, saturation: 0 },
      filter: 'Original',
      nextTransition: { type: 'none', duration: 500 },
      effects: [],
      zIndex: 0,
      visible: true,
      locked: false,
      name: c.name || `Clip ${config.clips.filter(cc => !cc.isOverlay).length + i + 1}`,
      isOverlay: false,
      isGIF: c.uri.toLowerCase().endsWith('.gif'),
      startTime: 0,
      transform: DEFAULT_TRANSFORM,
      keyframes: [],
      speed: 1.0,
      reverse: false
    }));
    await get().updateConfig({ clips: [...config.clips, ...sanitized] });
  },

  addOverlays: async (items) => {
    const { config } = get();
    const sanitized = items.map((c, i) => ({
      ...c,
      originalDuration: c.originalDuration || c.duration,
      trimStart: 0,
      trimEnd: c.duration,
      adjustments: { brightness: 0, contrast: 0, saturation: 0 },
      filter: 'Original',
      nextTransition: null,
      effects: [],
      zIndex: 5 + config.clips.filter(cc => cc.isOverlay).length + i,
      visible: true,
      locked: false,
      name: c.name || `Overlay ${config.clips.filter(cc => cc.isOverlay).length + i + 1}`,
      isOverlay: true,
      isGIF: c.uri.toLowerCase().endsWith('.gif'),
      startTime: 0,
      transform: DEFAULT_TRANSFORM,
      keyframes: [],
      speed: 1.0,
      reverse: false
    }));
    await get().updateConfig({ clips: [...config.clips, ...sanitized] });
    set({ selectedLayerId: sanitized[0].id });
  },

  deleteClip: async (id) => {
    const { config } = get();
    await get().updateConfig({ clips: config.clips.filter(c => c.id !== id) });
    if (get().selectedLayerId === id) set({ selectedLayerId: null });
  },

  splitClip: async (id, splitPointMs) => {
    const { config } = get();
    const index = config.clips.findIndex(c => c.id === id);
    if (index === -1) return;
    const targetClip = config.clips[index];
    if (targetClip.isOverlay) return;

    const currentAbsoluteStart = (targetClip.trimStart || 0) + (splitPointMs * targetClip.speed);
    const part1: LocalMediaItem = {
      ...targetClip,
      id: `clip_${Date.now()}_p1`,
      duration: splitPointMs,
      trimEnd: currentAbsoluteStart,
      name: `${targetClip.name} (P1)`,
      keyframes: (targetClip.keyframes || []).filter(k => k.time < splitPointMs)
    };
    const part2: LocalMediaItem = {
      ...targetClip,
      id: `clip_${Date.now()}_p2`,
      duration: targetClip.duration - splitPointMs,
      trimStart: currentAbsoluteStart,
      name: `${targetClip.name} (P2)`,
      keyframes: (targetClip.keyframes || [])
        .filter(k => k.time >= splitPointMs)
        .map(k => ({ ...k, time: k.time - splitPointMs }))
    };
    const updatedClips = [...config.clips];
    updatedClips.splice(index, 1, part1, part2);
    await get().updateConfig({ clips: updatedClips });
  },

  duplicateClip: async (id) => {
    const { config } = get();
    const index = config.clips.findIndex(c => c.id === id);
    if (index === -1) return;
    const clip = config.clips[index];
    const copy = {
        ...clip,
        id: `clip_${Date.now()}_copy_${Math.random().toString(36).substr(2, 4)}`,
        name: `${clip.name} (Cópia)`,
        keyframes: (clip.keyframes || []).map(k => ({ ...k, id: `kf_${Date.now()}_${Math.random()}` })),
        effects: (clip.effects || []).map(e => ({ ...e, id: `vfx_${Date.now()}_${Math.random()}`, keyframes: (e.keyframes || []).map(k => ({ ...k, id: `vfxkf_${Date.now()}_${Math.random()}` })) }))
    };
    const updatedClips = [...config.clips];
    updatedClips.splice(index + 1, 0, copy);
    await get().updateConfig({ clips: updatedClips });
  },

  reorderClips: async (startIndex, endIndex) => {
    const { config } = get();
    const sequentialClips = config.clips.filter(c => !c.isOverlay);
    const overlays = config.clips.filter(c => c.isOverlay);
    if (startIndex < 0 || startIndex >= sequentialClips.length || endIndex < 0 || endIndex >= sequentialClips.length) return;
    const newSequential = [...sequentialClips];
    const [removed] = newSequential.splice(startIndex, 1);
    newSequential.splice(endIndex, 0, removed);
    await get().updateConfig({ clips: [...newSequential, ...overlays] });
  },

  trimClip: async (id, trimStart, trimEnd) => {
    const { config } = get();
    const updatedClips = config.clips.map(c => {
      if (c.id === id) {
        const newDur = (trimEnd - trimStart) / c.speed;
        return { ...c, trimStart, trimEnd, duration: newDur };
      }
      return c;
    });
    await get().updateConfig({ clips: updatedClips });
  },

  updateClipAdjustments: async (id, adjustments) => {
    const { config } = get();
    const updatedClips = config.clips.map(c =>
      c.id === id ? { ...c, adjustments: { ...c.adjustments, ...adjustments } } : c
    );
    await get().updateConfig({ clips: updatedClips });
  },

  updateClipFilter: async (id, filter) => {
    const updatedClips = get().config.clips.map(c =>
      c.id === id ? { ...c, filter } : c
    );
    await get().updateConfig({ clips: updatedClips });
  },

  updateClipTransition: async (id, nextTransition) => {
    const updatedClips = get().config.clips.map(c =>
      c.id === id ? { ...c, nextTransition } : c
    );
    await get().updateConfig({ clips: updatedClips });
  },

  updateClipEffects: async (id, effects) => {
    const updatedClips = get().config.clips.map(c =>
      c.id === id ? { ...c, effects } : c
    );
    await get().updateConfig({ clips: updatedClips });
  },

  updateClipTransform: async (id, transform) => {
    const updatedClips = get().config.clips.map(c =>
      c.id === id ? { ...c, transform: { ...c.transform, ...transform } } : c
    );
    const updatedTexts = get().config.texts.map(t =>
      t.id === id ? { ...t, transform: { ...t.transform, ...transform } } : t
    );
    await get().updateConfig({ clips: updatedClips, texts: updatedTexts });
  },

  updateClipEditing: async (id, partial) => {
    const { config } = get();
    const updatedClips = config.clips.map(c => {
      if (c.id === id) {
        let updated = { ...c, ...partial };
        if (partial.speed) {
          // Recalculate duration based on speed
          const activeDuration = c.trimEnd - c.trimStart;
          updated.duration = activeDuration / partial.speed;
        }
        return updated;
      }
      return c;
    });
    await get().updateConfig({ clips: updatedClips });
  },

  freezeFrame: async (id, timelinePos) => {
    // Para simplificar, congelamos transformando em 'photo' de 3s no ponto atual
    // Em uma versão real capturaríamos um frame do player.
    // Aqui faremos um split e inseriremos um clipe com a mesma URI mas tipo foto.
    const { config, splitClip } = get();
    const idx = config.clips.findIndex(c => c.id === id);
    if (idx === -1) return;
    const clip = config.clips[idx];

    const freeze: LocalMediaItem = {
        ...clip,
        id: `freeze_${Date.now()}`,
        type: 'photo',
        duration: 3000,
        originalDuration: 3000,
        name: `${clip.name} (Congelado)`,
        keyframes: [],
        effects: []
    };

    const newClips = [...config.clips];
    newClips.splice(idx + 1, 0, freeze);
    await get().updateConfig({ clips: newClips });
  },

  // --- KEYFRAME ACTIONS ---
  addKeyframe: async (layerId, localTime, property, value) => {
    const { config } = get();
    const updateItemKeyframes = (items: any[]) => items.map(item => {
      if (item.id === layerId) {
        const kfs = item.keyframes || [];
        const existingIdx = kfs.findIndex((k: any) => k.time === localTime && k.property === property);
        let newKfs = [...kfs];
        if (existingIdx !== -1) {
          newKfs[existingIdx] = { ...newKfs[existingIdx], value };
        } else {
          newKfs.push({ id: `kf_${Date.now()}_${Math.random()}`, time: localTime, property, value });
        }
        return { ...item, keyframes: newKfs };
      }
      return item;
    });
    await get().updateConfig({ clips: updateItemKeyframes(config.clips), texts: updateItemKeyframes(config.texts) });
  },

  deleteKeyframe: async (layerId, keyframeId) => {
    const { config } = get();
    const filterKfs = (items: any[]) => items.map(item => {
      if (item.id === layerId) {
        return { ...item, keyframes: (item.keyframes || []).filter((k: any) => k.id !== keyframeId) };
      }
      return item;
    });
    await get().updateConfig({ clips: filterKfs(config.clips), texts: filterKfs(config.texts) });
  },

  clearAnimation: async (layerId) => {
    const { config } = get();
    const clear = (items: any[]) => items.map(item => item.id === layerId ? { ...item, keyframes: [] } : item);
    await get().updateConfig({ clips: clear(config.clips), texts: clear(config.texts) });
  },

  addVFXKeyframe: async (clipId, vfxId, localTime, value) => {
    const { config } = get();
    const updatedClips = config.clips.map(clip => {
      if (clip.id === clipId) {
        const updatedEffects = (clip.effects || []).map(vfx => {
          if (vfx.id === vfxId) {
            const kfs = vfx.keyframes || [];
            const existingIdx = kfs.findIndex(k => k.time === localTime);
            let newKfs = [...kfs];
            if (existingIdx !== -1) {
              newKfs[existingIdx] = { ...newKfs[existingIdx], value };
            } else {
              newKfs.push({ id: `vfxkf_${Date.now()}_${Math.random()}`, time: localTime, value });
            }
            return { ...vfx, keyframes: newKfs };
          }
          return vfx;
        });
        return { ...clip, effects: updatedEffects };
      }
      return clip;
    });
    await get().updateConfig({ clips: updatedClips });
  },

  deleteVFXKeyframe: async (clipId, vfxId, keyframeId) => {
    const { config } = get();
    const updatedClips = config.clips.map(clip => {
      if (clip.id === clipId) {
        const updatedEffects = (clip.effects || []).map(vfx => {
          if (vfx.id === vfxId) {
            return { ...vfx, keyframes: (vfx.keyframes || []).filter(k => k.id !== keyframeId) };
          }
          return vfx;
        });
        return { ...clip, effects: updatedEffects };
      }
      return clip;
    });
    await get().updateConfig({ clips: updatedClips });
  },

  clearVFXAnimation: async (clipId, vfxId) => {
    const { config } = get();
    const updatedClips = config.clips.map(clip => {
      if (clip.id === clipId) {
        const updatedEffects = (clip.effects || []).map(vfx => {
          if (vfx.id === vfxId) return { ...vfx, keyframes: [] };
          return vfx;
        });
        return { ...clip, effects: updatedEffects };
      }
      return clip;
    });
    await get().updateConfig({ clips: updatedClips });
  },

  updateCanvas: async (aspectRatio) => {
    const preset = CANVAS_PRESETS[aspectRatio];
    await get().updateConfig({
      canvas: {
        aspectRatio,
        width: preset.w,
        height: preset.h
      }
    });
  },

  addAudio: async (item) => {
    const { config } = get();
    const newAudio: LocalAudioItem = {
      ...item,
      id: `audio_${Date.now()}`,
      zIndex: 0,
      visible: true,
      locked: false,
      fadeIn: 0,
      fadeOut: 0,
      name: (item as any).name || `Áudio ${config.audios.length + 1}`
    };
    await get().updateConfig({ audios: [...config.audios, newAudio] });
  },

  updateAudio: async (id, partial) => {
    const updatedAudios = get().config.audios.map(a => a.id === id ? { ...a, ...partial } : a);
    await get().updateConfig({ audios: updatedAudios });
  },

  deleteAudio: async (id) => {
    await get().updateConfig({ audios: get().config.audios.filter(a => a.id !== id) });
    if (get().selectedLayerId === id) set({ selectedLayerId: null });
  },

  duplicateAudio: async (id) => {
    const target = get().config.audios.find(a => a.id === id);
    if (!target) return;
    const copy = { ...target, id: `audio_${Date.now()}_copy`, startTime: target.startTime + 1000 };
    await get().updateConfig({ audios: [...get().config.audios, copy] });
  },

  addText: async (item) => {
    const { config } = get();
    const newText: LocalTextItem = {
      ...item,
      id: `text_${Date.now()}`,
      zIndex: 10 + config.texts.length,
      visible: true,
      locked: false,
      name: item.text.substring(0, 10) || `Texto ${config.texts.length + 1}`,
      transform: DEFAULT_TRANSFORM,
      keyframes: [],
      style: DEFAULT_TEXT_STYLE,
      opacity: 1,
      animation: { mode: 'full', presetIn: 'none', presetOut: 'none', duration: 500, delay: 100 }
    };
    await get().updateConfig({ texts: [...config.texts, newText] });
    set({ selectedLayerId: newText.id });
  },

  updateText: async (id, partial) => {
    const updatedTexts = get().config.texts.map(t => t.id === id ? { ...t, ...partial } : t);
    await get().updateConfig({ texts: updatedTexts });
  },

  deleteText: async (id) => {
    await get().updateConfig({ texts: get().config.texts.filter(t => t.id !== id) });
    if (get().selectedLayerId === id) set({ selectedLayerId: null });
  },

  addSubtitles: async (segments) => {
    await get().updateConfig({ subtitles: segments });
  },

  updateSubtitle: async (id, partial) => {
    const updated = get().config.subtitles.map(s => s.id === id ? { ...s, ...partial } : s);
    await get().updateConfig({ subtitles: updated });
  },

  deleteSubtitle: async (id) => {
    await get().updateConfig({ subtitles: get().config.subtitles.filter(s => s.id !== id) });
  },

  clearSubtitles: async () => {
    await get().updateConfig({ subtitles: [] });
  },

  updateSubtitleStyle: async (style) => {
    await get().updateConfig({ subtitleStyle: { ...get().config.subtitleStyle, ...style } });
  },

  addManualSubtitle: async (startTime) => {
    const { config } = get();
    const newSub: SubtitleSegment = {
      id: `sub_${Date.now()}`,
      text: 'Nova Legenda',
      startTime,
      endTime: startTime + 2000,
      enabled: true
    };
    await get().updateConfig({ subtitles: [...config.subtitles, newSub].sort((a, b) => a.startTime - b.startTime) });
  },

  undo: async () => {
    const { history, historyIndex } = get();
    if (historyIndex > 0) {
      const prevConfig = history[historyIndex - 1];
      set({ config: prevConfig, historyIndex: historyIndex - 1 });
      await get().updateConfig(prevConfig, false);
    }
  },
  redo: async () => {
    const { history, historyIndex } = get();
    if (historyIndex < history.length - 1) {
      const nextConfig = history[historyIndex + 1];
      set({ config: nextConfig, historyIndex: historyIndex + 1 });
      await get().updateConfig(nextConfig, false);
    }
  }
}));
