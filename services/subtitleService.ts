import { SubtitleSegment, SubtitleStyle } from './editorState';

/**
 * Format milliseconds to HH:MM:SS,ms for SRT
 */
function formatTimeSRT(ms: number): string {
  const date = new Date(ms);
  const hours = Math.floor(ms / 3600000).toString().padStart(2, '0');
  const minutes = date.getUTCMinutes().toString().padStart(2, '0');
  const seconds = date.getUTCSeconds().toString().padStart(2, '0');
  const milliseconds = date.getUTCMilliseconds().toString().padStart(3, '0');
  return `${hours}:${minutes}:${seconds},${milliseconds}`;
}

/**
 * Format milliseconds to H:MM:SS.cs for ASS
 */
function formatTimeASS(ms: number): string {
  const date = new Date(ms);
  const hours = Math.floor(ms / 3600000);
  const minutes = date.getUTCMinutes().toString().padStart(2, '0');
  const seconds = date.getUTCSeconds().toString().padStart(2, '0');
  const centiseconds = Math.floor(date.getUTCMilliseconds() / 10).toString().padStart(2, '0');
  return `${hours}:${minutes}:${seconds}.${centiseconds}`;
}

export function generateSRT(segments: SubtitleSegment[]): string {
  return segments
    .filter(s => s.enabled)
    .map((s, index) => {
      return `${index + 1}\n${formatTimeSRT(s.startTime)} --> ${formatTimeSRT(s.endTime)}\n${s.text}\n`;
    })
    .join('\n');
}

export function generateASS(segments: SubtitleSegment[], style: SubtitleStyle): string {
  const color = style.color.replace('#', '').split('').reverse().join(''); // Simplified BGR
  const strokeColor = style.strokeColor.replace('#', '');
  const bgOpacity = Math.floor(style.backgroundOpacity * 255).toString(16).padStart(2, '0');

  // ASS Header and Styles
  let ass = `[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,${style.fontFamily},${style.fontSize},&H00${color},&H000000FF,&H00${strokeColor},&H${bgOpacity}000000,0,0,0,0,100,100,0,0,${style.backgroundEnabled ? 3 : 1},${style.strokeWidth},1,2,10,10,${1080 - (style.positionY / 100 * 1080)},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
`;

  // Add segments
  segments
    .filter(s => s.enabled)
    .forEach(s => {
      ass += `Dialogue: 0,${formatTimeASS(s.startTime)},${formatTimeASS(s.endTime)},Default,,0,0,0,,${s.text}\n`;
    });

  return ass;
}
