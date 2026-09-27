import { Keyframe, KeyframeProperty, Transform, LocalTextItem, TextAnimationPreset, LocalMediaItem } from './editorState';

export function getInterpolatedValue(
  keyframes: Keyframe[],
  property: KeyframeProperty,
  time: number,
  defaultValue: number
): number {
  const propertyKeyframes = keyframes
    .filter((k) => k.property === property)
    .sort((a, b) => a.time - b.time);

  if (propertyKeyframes.length === 0) return defaultValue;

  let prev: Keyframe | null = null;
  let next: Keyframe | null = null;

  for (const k of propertyKeyframes) {
    if (k.time <= time) {
      prev = k;
    } else {
      next = k;
      break;
    }
  }

  if (!prev && next) return next.value;
  if (prev && !next) return prev.value;
  if (prev && next) {
    const range = next.time - prev.time;
    if (range === 0) return prev.value;
    const progress = (time - prev.time) / range;
    return prev.value + (next.value - prev.value) * progress;
  }

  return defaultValue;
}

export function getInterpolatedTransform(
  keyframes: Keyframe[] | undefined,
  baseTransform: Transform,
  itemStartTime: number,
  currentTime: number
): Transform {
  const localTime = currentTime - itemStartTime;
  const kfs = keyframes || [];

  return {
    ...baseTransform,
    x: getInterpolatedValue(kfs, 'x', localTime, baseTransform.x),
    y: getInterpolatedValue(kfs, 'y', localTime, baseTransform.y),
    scale: getInterpolatedValue(kfs, 'scale', localTime, baseTransform.scale),
    rotation: getInterpolatedValue(kfs, 'rotation', localTime, baseTransform.rotation),
    opacity: getInterpolatedValue(kfs, 'opacity', localTime, baseTransform.opacity),
  };
}

export function getInterpolatedAdjustments(
  item: LocalMediaItem,
  currentTime: number
): LocalMediaItem['adjustments'] {
  const localTime = currentTime - item.startTime;
  const kfs = item.keyframes || [];

  return {
    brightness: getInterpolatedValue(kfs, 'brightness', localTime, item.adjustments.brightness),
    contrast: getInterpolatedValue(kfs, 'contrast', localTime, item.adjustments.contrast),
    saturation: getInterpolatedValue(kfs, 'saturation', localTime, item.adjustments.saturation),
  };
}

export interface TextPartState {
  text: string;
  opacity: number;
  offsetY: number;
  scale: number;
}

export function getTextPartsState(
  item: LocalTextItem,
  currentTime: number
): TextPartState[] {
  const localTime = currentTime - item.startTime;
  const anim = item.animation;
  if (!anim || anim.presetIn === 'none') {
    return [{ text: item.text, opacity: 1, offsetY: 0, scale: 1 }];
  }

  const parts = anim.mode === 'word' ? item.text.split(' ') : anim.mode === 'letter' ? item.text.split('') : [item.text];
  const results: TextPartState[] = [];

  parts.forEach((p, i) => {
    const partStart = i * anim.delay;
    const partEnd = partStart + anim.duration;

    let opacity = 1;
    let offsetY = 0;
    let scale = 1;

    if (localTime < partStart) {
      opacity = 0;
    } else if (localTime < partEnd) {
      const progress = (localTime - partStart) / anim.duration;
      switch (anim.presetIn) {
        case 'fade':
          opacity = progress;
          break;
        case 'slide_up':
          opacity = progress;
          offsetY = 20 * (1 - progress);
          break;
        case 'slide_down':
          opacity = progress;
          offsetY = -20 * (1 - progress);
          break;
        case 'zoom':
          opacity = progress;
          scale = progress;
          break;
        case 'pop':
          opacity = progress;
          scale = progress > 0.8 ? 1 + (1 - progress) : progress * 1.2;
          break;
      }
    }

    results.push({
      text: anim.mode === 'word' ? p + ' ' : p,
      opacity,
      offsetY,
      scale
    });
  });

  return results;
}

export function getInterpolatedVFXIntensity(
  vfx: { intensity: number, keyframes?: { time: number; value: number }[] },
  itemStartTime: number,
  currentTime: number
): number {
  if (!vfx.keyframes || vfx.keyframes.length === 0) return vfx.intensity;

  const localTime = currentTime - itemStartTime;
  const kfs = [...vfx.keyframes].sort((a, b) => a.time - b.time);

  let prev: { time: number; value: number } | null = null;
  let next: { time: number; value: number } | null = null;

  for (const k of kfs) {
    if (k.time <= localTime) {
      prev = k;
    } else {
      next = k;
      break;
    }
  }

  if (!prev && next) return next.value;
  if (prev && !next) return prev.value;
  if (prev && next) {
    const range = next.time - prev.time;
    if (range === 0) return prev.value;
    const progress = (localTime - prev.time) / range;
    return prev.value + (next.value - prev.value) * progress;
  }

  return vfx.intensity;
}
