import { LocalTextItem, TextAnimationPreset } from './editorState';

function formatTimeASS(ms: number): string {
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000).toString().padStart(2, '0');
  const seconds = Math.floor((ms % 60000) / 1000).toString().padStart(2, '0');
  const centiseconds = Math.floor((ms % 1000) / 10).toString().padStart(2, '0');
  return `${hours}:${minutes}:${seconds}.${centiseconds}`;
}

function hexToBGR(hex: string): string {
  if (!hex || hex.length < 7) return 'FFFFFF';
  const r = hex.substring(1, 3);
  const g = hex.substring(3, 5);
  const b = hex.substring(5, 7);
  return `${b}${g}${r}`;
}

function getASSTags(preset: TextAnimationPreset, delay: number, dur: number): string {
  const d = Math.round(delay);
  const t = Math.round(dur);
  switch (preset) {
    case 'fade':
      return `\\alpha&HFF&\\t(${d},${d + t},\\alpha&H00&)`;
    case 'zoom':
      return `\\fscx0\\fscy0\\t(${d},${d + t},\\fscx100\\fscy100)`;
    case 'pop':
      return `\\fscx0\\fscy0\\t(${d},${d + Math.round(0.8 * t)},\\fscx120\\fscy120)\\t(${d + Math.round(0.8 * t)},${d + t},\\fscx100\\fscy100)`;
    default:
      return '';
  }
}

export function generateMultiTextASS(items: LocalTextItem[]): string {
  let styles = '';
  let events = '';

  items.forEach((item, index) => {
    const s = item.style;
    const color = hexToBGR(s.color);
    const outlineColor = hexToBGR(s.strokeColor);
    const backColor = hexToBGR(s.shadowEnabled ? s.shadowColor : s.backgroundColor);

    const primaryAlpha = Math.floor((1 - (item.opacity || 1)) * 255).toString(16).padStart(2, '0').toUpperCase();
    const backAlpha = Math.floor((1 - (s.backgroundEnabled ? s.backgroundOpacity : (s.shadowEnabled ? 0.5 : 1))) * 255).toString(16).padStart(2, '0').toUpperCase();

    const styleName = `S${index}`;
    const borderStyle = s.backgroundEnabled ? 3 : 1;
    const shadowSize = s.shadowEnabled ? s.shadowBlur : 0;
    const spacing = s.letterSpacing || 0;
    const bold = s.fontWeight === 'bold' ? -1 : 0;

    styles += `Style: ${styleName},${s.fontFamily},${s.fontSize},&H${primaryAlpha}${color},&H000000FF,&H00${outlineColor},&H${backAlpha}${backColor},${bold},0,0,0,100,100,${spacing},0,${borderStyle},${s.strokeWidth},${shadowSize},2,10,10,10,1\n`;

    const anim = item.animation || { mode: 'full', presetIn: 'none', presetOut: 'none', duration: 500, delay: 100 };
    const parts = anim.mode === 'word' ? item.text.split(' ') : anim.mode === 'letter' ? item.text.split('') : [item.text];

    const startTime = formatTimeASS(item.startTime);
    const endTime = formatTimeASS(item.startTime + item.duration);

    let textWithTags = '';
    if (anim.presetIn === 'none' || !anim.presetIn) {
      textWithTags = item.text;
    } else {
      parts.forEach((p, i) => {
        const partDelay = i * anim.delay;
        const tag = getASSTags(anim.presetIn, partDelay, anim.duration);
        textWithTags += `{${tag}}${anim.mode === 'word' ? p + ' ' : p}`;
      });
    }

    const x = (item.position.x / 100) * 1920;
    const y = (item.position.y / 100) * 1080;
    events += `Dialogue: ${item.zIndex},${startTime},${endTime},${styleName},,0,0,0,,{\\pos(${x.toFixed(0)},${y.toFixed(0)})}${textWithTags}\n`;
  });

  return `[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
${styles}

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
${events}`;
}
