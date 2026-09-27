import { FFmpegKit, FFmpegKitConfig, ReturnCode } from 'ffmpeg-kit-react-native';
import * as FileSystem from 'expo-file-system';
import * as MediaLibrary from 'expo-media-library';
import { EditorProjectConfig, LocalMediaItem, Keyframe, KeyframeProperty } from './editorState';
import { generateASS } from './subtitleService';
import { generateMultiTextASS } from './textAnimationService';

export interface ExportOptions {
  resolution: string;
  fps: string;
  quality: string;
}

export interface ExportProgress {
  percent: number;
  message: string;
}

function buildVFXExpression(keyframes: { time: number; value: number }[] | undefined, baseVal: number, formatter: (v: number) => number): string {
  const kfs = (keyframes || []).sort((a, b) => a.time - b.time);
  if (kfs.length === 0) return formatter(baseVal).toFixed(3);

  const buildRecursive = (index: number): string => {
    const current = kfs[index];
    const t1 = current.time / 1000;
    const v1 = formatter(current.value);
    if (index === kfs.length - 1) return v1.toFixed(3);
    const next = kfs[index + 1];
    const t2 = next.time / 1000;
    const v2 = formatter(next.value);
    const slope = (v2 - v1) / (t2 - t1);
    const interp = `${v1.toFixed(3)} + ${slope.toFixed(4)} * (t - ${t1.toFixed(3)})`;
    return `if(lt(t,${t2.toFixed(3)}),${interp},${buildRecursive(index + 1)})`;
  };

  let expr = buildRecursive(0);
  const tStart = kfs[0].time / 1000;
  const vStart = formatter(kfs[0].value);
  return `if(lt(t,${tStart.toFixed(3)}),${vStart.toFixed(3)},${expr})`;
}

function buildExpression(keyframes: Keyframe[] | undefined, property: KeyframeProperty, baseVal: number, width: number, height: number): string {
  const kfs = (keyframes || [])
    .filter(k => k.property === property)
    .map(k => ({ time: k.time, value: k.value }));

  const formatter = (v: number) => {
    if (property === 'x') return (v / 100 * width);
    if (property === 'y') return (v / 100 * height);
    if (property === 'rotation') return (v * Math.PI / 180);
    return v;
  };
  return buildVFXExpression(kfs, baseVal, formatter);
}

function getClipFilters(clip: LocalMediaItem, width: number, height: number, fps: string, isOverlay = false): string {
  let filters: string[] = [];

  // 1. Crop
  if (clip.crop) {
    const cx = clip.crop.x * width;
    const cy = clip.crop.y * height;
    const cw = clip.crop.width * width;
    const ch = clip.crop.height * height;
    filters.push(`crop=${Math.max(1, cw).toFixed(0)}:${Math.max(1, ch).toFixed(0)}:${cx.toFixed(0)}:${cy.toFixed(0)}`);
  }

  // 2. Scale & Pad (Base)
  if (!isOverlay) {
    filters.push(`scale=${width}:${height}:force_original_aspect_ratio=decrease,pad=${width}:${height}:(ow-ih)/2:(oh-ih)/2,setsar=1`);
  }

  // Ensure same FPS for xfade compatibility
  filters.push(`fps=${fps}`);
  if (!isOverlay) filters.push('format=yuv420p');

  // 3. Speed
  if (clip.speed !== 1.0) {
    filters.push(`setpts=${(1/clip.speed).toFixed(4)}*PTS`);
  }

  // 4. Reverse
  if (clip.reverse) {
    filters.push('reverse');
  }

  // 5. Flip
  if (clip.transform?.flipH) filters.push('hflip');
  if (clip.transform?.flipV) filters.push('vflip');

  // 6. Color Grading (Dynamic)
  const bExpr = buildVFXExpression(clip.keyframes?.filter(k => k.property === 'brightness'), clip.adjustments?.brightness || 0, (v) => v / 100);
  const cExpr = buildVFXExpression(clip.keyframes?.filter(k => k.property === 'contrast'), clip.adjustments?.contrast || 0, (v) => 1.0 + (v / 100));
  const sExpr = buildVFXExpression(clip.keyframes?.filter(k => k.property === 'saturation'), clip.adjustments?.saturation || 0, (v) => 1.0 + (v / 100));
  filters.push(`eq=brightness='${bExpr}':contrast='${cExpr}':saturation='${sExpr}'`);

  // 7. Presets
  if (clip.filter && clip.filter !== 'Original') {
    switch (clip.filter) {
      case 'Vívido': filters.push('eq=saturation=1.3:contrast=1.1'); break;
      case 'Frio': filters.push('colorbalance=bt=0.15:gt=0.05'); break;
      case 'Quente': filters.push('colorbalance=rt=0.15:gt=0.05'); break;
      case 'P&B': filters.push('hue=s=0'); break;
      case 'Filme': filters.push('curves=vintage'); break;
      case 'Retrô': filters.push('colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131'); break;
      case 'Suave': filters.push('boxblur=2:1'); break;
    }
  }

  // 8. VFX
  if (clip.effects && clip.effects.length > 0) {
    clip.effects.forEach(effect => {
      if (!effect.enabled) return;
      switch (effect.type) {
        case 'blur':
          const blurExpr = buildVFXExpression(effect.keyframes || [], effect.intensity, (v) => Math.max(1, Math.round(20 * (v / 100))));
          filters.push(`boxblur=${blurExpr}:1`);
          break;
        case 'glow':
          const glowExpr = buildVFXExpression(effect.keyframes || [], effect.intensity, (v) => 2 * (v / 100));
          filters.push(`unsharp=5:5:${glowExpr}:5:5:${glowExpr}`);
          break;
        case 'vignette':
          const vigExpr = buildVFXExpression(effect.keyframes || [], effect.intensity, (v) => (Math.PI / 3) * (v / 100));
          filters.push(`vignette=angle=${vigExpr}`);
          break;
      }
    });
  }

  return filters.join(',');
}

export async function exportVideo(
  config: EditorProjectConfig,
  options: ExportOptions,
  onProgress: (progress: ExportProgress) => void
): Promise<string> {
  const { clips, audios, texts, subtitles, subtitleStyle, canvas } = config;
  if (clips.length === 0) throw new Error('Nenhum clipe para exportar.');

  for (const clip of clips) {
    if (!clip.visible) continue;
    const info = await FileSystem.getInfoAsync(clip.uri);
    if (!info.exists) throw new Error(`O arquivo "${clip.name}" não foi encontrado.`);
  }

  const timestamp = Date.now();
  const outputFilename = `export_${timestamp}.mp4`;
  const outputPath = `${FileSystem.cacheDirectory}${outputFilename}`;
  const width = canvas.width;
  const height = canvas.height;

  const assSubContent = generateASS(subtitles, subtitleStyle);
  const subPath = `${FileSystem.cacheDirectory}sub_${timestamp}.ass`;
  await FileSystem.writeAsStringAsync(subPath, assSubContent);

  const assTextContent = generateMultiTextASS(texts.filter(t => t.visible));
  const textPath = `${FileSystem.cacheDirectory}text_${timestamp}.ass`;
  await FileSystem.writeAsStringAsync(textPath, assTextContent);

  let inputs = '';
  let filters: string[] = [];

  const sequentialClips = clips.filter(c => !c.isOverlay && c.visible);
  const overlayClips = clips.filter(c => c.isOverlay && c.visible).sort((a, b) => a.zIndex - b.zIndex);

  if (sequentialClips.length === 0) throw new Error('Nenhum clipe visível na trilha principal.');

  // 1. Main Sequence Inputs & Filters
  sequentialClips.forEach((clip, index) => {
    const ss = (clip.trimStart || 0) / 1000;
    const t = ((clip.trimEnd - clip.trimStart) || clip.duration) / 1000;
    if (clip.type === 'photo') {
      inputs += `-loop 1 -t ${t} -i "${clip.uri}" `;
    } else {
      inputs += `-ss ${ss} -t ${t} -i "${clip.uri}" `;
    }
    filters.push(`[${index}:v]${getClipFilters(clip, width, height, options.fps, false)}[v${index}]`);
  });

  // 2. Transitions Logic
  let currentMainLabel = 'v0';
  let currentOffset = (sequentialClips[0].trimEnd - sequentialClips[0].trimStart) / 1000 / sequentialClips[0].speed;

  for (let i = 0; i < sequentialClips.length - 1; i++) {
    const clipA = sequentialClips[i];
    const clipB = sequentialClips[i+1];
    const transition = clipA.nextTransition;
    const nextLabel = `v_trans${i}`;

    if (transition && transition.type !== 'none') {
      const clipDurSec = (clipA.trimEnd - clipA.trimStart) / 1000 / clipA.speed;
      const transDur = Math.min(transition.duration / 1000, clipDurSec / 2);
      const transType = transition.type === 'dissolve' ? 'fade' : transition.type;
      const offset = currentOffset - transDur;
      filters.push(`[${currentMainLabel}][v${i+1}]xfade=transition=${transType}:duration=${transDur.toFixed(3)}:offset=${Math.max(0, offset).toFixed(3)}[${nextLabel}]`);
      currentMainLabel = nextLabel;
      currentOffset = offset + ((clipB.trimEnd - clipB.trimStart) / 1000 / clipB.speed);
    } else {
      const concatLabel = `v_concat${i}`;
      filters.push(`[${currentMainLabel}][v${i+1}]concat=n=2:v=1:a=0[${concatLabel}]`);
      currentMainLabel = concatLabel;
      currentOffset += ((clipB.trimEnd - clipB.trimStart) / 1000 / clipB.speed);
    }
  }

  // 3. Overlays
  let lastVideoLabel = currentMainLabel;
  overlayClips.forEach((overlay, index) => {
    const overlayIdx = sequentialClips.length + index;
    const ss = (overlay.trimStart || 0) / 1000;
    const t = ((overlay.trimEnd - overlay.trimStart) || overlay.duration) / 1000;

    if (overlay.isGIF) inputs += `-ignore_loop 0 -i "${overlay.uri}" `;
    else if (overlay.type === 'photo') inputs += `-loop 1 -t ${t} -i "${overlay.uri}" `;
    else inputs += `-ss ${ss} -t ${t} -i "${overlay.uri}" `;

    const transform = overlay.transform;
    const scaleExpr = buildExpression(overlay.keyframes, 'scale', transform.scale, width, height);
    const rotateExpr = buildExpression(overlay.keyframes, 'rotation', transform.rotation, width, height);
    const opacityExpr = buildExpression(overlay.keyframes, 'opacity', transform.opacity, width, height);

    const hasScaleAnims = (overlay.keyframes || []).some(k => k.property === 'scale');
    const fixedScale = hasScaleAnims ? Math.max(...(overlay.keyframes || []).map(k => k.value)) : transform.scale;
    const ovW = Math.round(width * fixedScale);
    const ovH = Math.round(height * fixedScale);

    filters.push(`[${overlayIdx}:v]${getClipFilters(overlay, ovW, ovH, options.fps, true)},rotate=${rotateExpr}:c=none:ow='iw':oh='ih',format=rgba,colorchannelmixer=aa=${opacityExpr}[ov_prep${index}]`);

    const xExpr = `(${buildExpression(overlay.keyframes, 'x', transform.x, width, height)}) - (w/2)`;
    const yExpr = `(${buildExpression(overlay.keyframes, 'y', transform.y, width, height)}) - (h/2)`;

    const nextLabel = `v_with_ov${index}`;
    const start = overlay.startTime / 1000;
    const end = (overlay.startTime + overlay.duration) / 1000;
    filters.push(`[${lastVideoLabel}][ov_prep${index}]overlay=x='${xExpr}':y='${yExpr}':enable='between(t,${start},${end})'${overlay.isGIF ? ':shortest=1' : ''}[${nextLabel}]`);
    lastVideoLabel = nextLabel;
  });

  // 4. Robust Audio Stream Processing
  const audioLabels: string[] = [];
  sequentialClips.forEach((clip, i) => {
    const durSec = ((clip.trimEnd - clip.trimStart) || clip.duration) / 1000 / (clip.speed || 1.0);
    const label = `a_prep${i}`;
    audioLabels.push(`[${label}]`);

    if (clip.type === 'photo') {
      filters.push(`anullsrc=r=44100:cl=stereo,atrim=0:${durSec.toFixed(3)}[${label}]`);
    } else {
      let audioFilter = `[${i}:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo`;
      if (clip.speed !== 1.0) {
        if (clip.speed < 0.5) audioFilter += `,atempo=0.5,atempo=${(clip.speed / 0.5).toFixed(2)}`;
        else if (clip.speed > 2.0) audioFilter += `,atempo=2.0,atempo=${(clip.speed / 2.0).toFixed(2)}`;
        else audioFilter += `,atempo=${clip.speed.toFixed(2)}`;
      }
      if (clip.reverse) audioFilter += `,areverse`;
      filters.push(`${audioFilter}[${label}]`);
    }
  });

  const concatInputs = audioLabels.join('');
  filters.push(`${concatInputs}concat=n=${sequentialClips.length}:v=0:a=1[a_concat]`);
  let lastAudioLabel = 'a_concat';

  const visibleAudios = audios.filter(a => a.visible);
  visibleAudios.forEach((audio, index) => {
    const audioIdx = sequentialClips.length + overlayClips.length + index;
    inputs += `-ss ${(audio.trimStart || 0) / 1000} -t ${(audio.duration || 0) / 1000} -i "${audio.uri}" `;
    let audioFilters = `volume=${(audio.muted ? 0 : audio.volume).toFixed(2)}`;
    if (audio.fadeIn > 0) audioFilters += `,afade=t=in:st=0:d=${(audio.fadeIn / 1000).toFixed(3)}`;
    if (audio.fadeOut > 0) audioFilters += `,afade=t=out:st=${((audio.duration - audio.fadeOut)/1000).toFixed(3)}:d=${(audio.fadeOut / 1000).toFixed(3)}`;
    filters.push(`[${audioIdx}:a]${audioFilters},adelay=${Math.round(audio.startTime * 44.1)}|${Math.round(audio.startTime * 44.1)}[aud${index}]`);
    filters.push(`[${lastAudioLabel}][aud${index}]amix=inputs=2:duration=first[am${index}]`);
    lastAudioLabel = `am${index}`;
  });

  // 5. Subtitles & Text Overlay Composition
  let finalVideoLabel = lastVideoLabel;
  if (subtitles.length > 0) {
    filters.push(`[${finalVideoLabel}]subtitles='${subPath}'[v_sub]`);
    finalVideoLabel = 'v_sub';
  }
  if (texts.some(t => t.visible)) {
    filters.push(`[${finalVideoLabel}]subtitles='${textPath}'[v_text_anim]`);
    finalVideoLabel = 'v_text_anim';
  }

  const filterComplex = filters.join('; ');
  const finalCommand = `${inputs}-filter_complex "${filterComplex}" -map "[${finalVideoLabel}]" -map "[${lastAudioLabel}]" -c:v libx264 -preset ultrafast -crf 23 -r ${options.fps} -c:a aac -b:a 128k -y "${outputPath}"`;

  return new Promise((resolve, reject) => {
    FFmpegKitConfig.enableStatisticsCallback(stats => {
      const totalDuration = sequentialClips.reduce((acc, c) => acc + (c.duration), 0);
      const pct = Math.min(99, Math.round((stats.getTime() / totalDuration) * 100));
      if (pct > 0) onProgress({ percent: pct, message: `Exportando... ${pct}%` });
    });
    FFmpegKit.execute(finalCommand).then(async (session) => {
      const returnCode = await session.getReturnCode();
      if (ReturnCode.isSuccess(returnCode)) {
        try {
          const permission = await MediaLibrary.requestPermissionsAsync();
          if (permission.granted) await MediaLibrary.createAssetAsync(outputPath);
        } catch (e) {}
        resolve(outputPath);
      } else {
        const logs = await session.getAllLogsAsString();
        reject(new Error(`FFmpeg Error: ${logs.slice(-500)}`));
      }
    });
  });
}
