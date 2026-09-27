// Powered by OnSpace.AI
// Mock data layer (prototype only)

export interface Project {
  id: string;
  title: string;
  duration: string;
  date: string;
  thumb: string;
}

export interface MediaItem {
  id: string;
  type: 'video' | 'photo' | 'audio';
  title: string;
  duration?: string;
  thumb: string;
}

export interface Template {
  id: string;
  title: string;
  category: string;
  clips: number;
  duration: string;
  thumb: string;
  premium?: boolean;
}

export interface AudioTrack {
  id: string;
  name: string;
  category: string;
  duration: string;
}

export interface EffectItem {
  id: string;
  name: string;
  category: string;
  thumb: string;
  premium?: boolean;
}

export interface TransitionItem {
  id: string;
  name: string;
  icon: string;
}

const img = (seed: string, w = 400, h = 600) => `https://picsum.photos/seed/${seed}/${w}/${h}`;

export const recentProjects: Project[] = [
  { id: 'p1', title: 'Viagem à Serra', duration: '01:24', date: 'Hoje', thumb: img('vid1') },
  { id: 'p2', title: 'Reels de Verão', duration: '00:32', date: 'Ontem', thumb: img('vid2') },
  { id: 'p3', title: 'Aniversário Lu', duration: '02:10', date: '12 set', thumb: img('vid3') },
  { id: 'p4', title: 'Tutorial Café', duration: '03:45', date: '08 set', thumb: img('vid4') },
];

export const mediaVideos: MediaItem[] = Array.from({ length: 12 }).map((_, i) => ({
  id: `v${i}`,
  type: 'video',
  title: `Clipe ${i + 1}`,
  duration: ['00:12', '00:34', '01:02', '00:08', '00:45', '00:22'][i % 6],
  thumb: img(`mv${i}`),
}));

export const mediaPhotos: MediaItem[] = Array.from({ length: 12 }).map((_, i) => ({
  id: `ph${i}`,
  type: 'photo',
  title: `Foto ${i + 1}`,
  thumb: img(`mp${i}`),
}));

export const mediaAudio: MediaItem[] = Array.from({ length: 8 }).map((_, i) => ({
  id: `au${i}`,
  type: 'audio',
  title: `Áudio ${i + 1}`,
  duration: ['02:15', '03:04', '01:48', '02:52'][i % 4],
  thumb: img(`ma${i}`),
}));

export const templates: Template[] = [
  { id: 't1', title: 'Vlog Cinematográfico', category: 'Em alta', clips: 6, duration: '00:30', thumb: img('tp1', 500, 700) },
  { id: 't2', title: 'Beat Sync Reels', category: 'Redes', clips: 8, duration: '00:15', thumb: img('tp2', 500, 700), premium: true },
  { id: 't3', title: 'Diário de Viagem', category: 'Vlog', clips: 5, duration: '00:45', thumb: img('tp3', 500, 700) },
  { id: 't4', title: 'Promo Produto', category: 'Redes', clips: 4, duration: '00:20', thumb: img('tp4', 500, 700), premium: true },
  { id: 't5', title: 'Aftermovie Festa', category: 'Em alta', clips: 10, duration: '00:60', thumb: img('tp5', 500, 700) },
  { id: 't6', title: 'Rotina Matinal', category: 'Vlog', clips: 7, duration: '00:35', thumb: img('tp6', 500, 700) },
];

export const templateCategories = ['Em alta', 'Vlog', 'Redes', 'Viagem', 'Comida'];

export const audioTracks: AudioTrack[] = [
  { id: 'a1', name: 'Summer Vibes', category: 'Happy', duration: '02:34' },
  { id: 'a2', name: 'Neon Nights', category: 'Pop', duration: '03:12' },
  { id: 'a3', name: 'Morning Coffee', category: 'Vlog', duration: '02:05' },
  { id: 'a4', name: 'Urban Beat', category: 'Pop', duration: '02:48' },
  { id: 'a5', name: 'Sunset Drive', category: 'Happy', duration: '03:30' },
  { id: 'a6', name: 'Chill Lounge', category: 'Vlog', duration: '02:20' },
];

export const audioCategories = ['Happy', 'Pop', 'Vlog', 'Cinemático', 'Épico'];

export const effects: EffectItem[] = [
  { id: 'e1', name: 'Desfoque', category: 'Básico', thumb: img('ef1', 200, 200) },
  { id: 'e2', name: 'Glitch', category: 'Distorção', thumb: img('ef2', 200, 200), premium: true },
  { id: 'e3', name: 'RGB Split', category: 'Distorção', thumb: img('ef3', 200, 200), premium: true },
  { id: 'e4', name: 'Shake', category: 'Básico', thumb: img('ef4', 200, 200) },
  { id: 'e5', name: 'Vinheta', category: 'Retrô', thumb: img('ef5', 200, 200) },
  { id: 'e6', name: 'Luz Vazada', category: 'Luz', thumb: img('ef6', 200, 200) },
  { id: 'e7', name: 'VHS', category: 'Retrô', thumb: img('ef7', 200, 200), premium: true },
  { id: 'e8', name: 'Bokeh', category: 'Luz', thumb: img('ef8', 200, 200) },
  { id: 'e9', name: 'Pixelado', category: 'Distorção', thumb: img('ef9', 200, 200) },
];

export const effectCategories = ['Tudo', 'Básico', 'Luz', 'Retrô', 'Distorção'];

export const transitions: TransitionItem[] = [
  { id: 'tr1', name: 'Dissolver', icon: 'contrast-outline' },
  { id: 'tr2', name: 'Fade', icon: 'sunny-outline' },
  { id: 'tr3', name: 'Zoom', icon: 'scan-outline' },
  { id: 'tr4', name: 'Girar', icon: 'sync-outline' },
  { id: 'tr5', name: 'Flash', icon: 'flash-outline' },
  { id: 'tr6', name: 'Ondular', icon: 'pulse-outline' },
];

export const fontsList = ['Inter', 'Poppins', 'Montserrat', 'Playfair', 'Roboto Mono', 'Bebas'];
export const filtersList = ['Original', 'Vívido', 'Frio', 'Quente', 'P&B', 'Filme', 'Retrô', 'Suave'];
export const textColors = ['#FFFFFF', '#000000', '#8A2BE2', '#EF4444', '#22C55E', '#F59E0B', '#3B82F6', '#EC4899'];

export const shapes = [
  { id: 's1', name: 'Quadrado', icon: 'square-outline' },
  { id: 's2', name: 'Círculo', icon: 'ellipse-outline' },
  { id: 's3', name: 'Seta', icon: 'arrow-forward-outline' },
  { id: 's4', name: 'Triângulo', icon: 'triangle-outline' },
];

export const overlayCategories = ['Imagem', 'Vídeo', 'Sticker', 'Desenho', 'Formas'];

export const editTools = [
  { id: 'split', name: 'Dividir', icon: 'cut-outline' },
  { id: 'trim', name: 'Cortar', icon: 'crop-outline' },
  { id: 'delete', name: 'Excluir', icon: 'trash-outline', danger: true },
  { id: 'speed', name: 'Velocidade', icon: 'speedometer-outline' },
  { id: 'volume', name: 'Volume', icon: 'volume-high-outline' },
  { id: 'adjust', name: 'Ajustes', icon: 'options-outline' },
  { id: 'duplicate', name: 'Duplicar', icon: 'copy-outline' },
  { id: 'reverse', name: 'Inverter', icon: 'play-back-outline' },
  { id: 'freeze', name: 'Congelar', icon: 'snow-outline' },
  { id: 'crop', name: 'Recortar', icon: 'scan-outline' },
  { id: 'opacity', name: 'Opacidade', icon: 'contrast-outline' },
  { id: 'rotate', name: 'Girar', icon: 'refresh-outline' },
];

export const captionLanguages = ['Português (BR)', 'English (US)', 'Español', 'Français', '日本語'];
