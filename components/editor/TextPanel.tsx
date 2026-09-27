// Powered by OnSpace.AI
import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, TextInput } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Slider, Button, SegmentedControl } from '@/components';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';
import { useEditorStore, LocalTextItem, TextStyle } from '@/services/editorState';

interface Props {
  onAction: (msg: string) => void;
  selectedTextId?: string | null;
}

const FONT_OPTIONS = ['System', 'Arial', 'Courier', 'Georgia', 'Verdana'];

export function TextPanel({ onAction, selectedTextId }: Props) {
  const router = useRouter();
  const { config, updateText, addText } = useEditorStore();

  const selectedText = config.texts.find(t => t.id === selectedTextId);

  const handleUpdate = (partial: Partial<LocalTextItem> | { style: Partial<TextStyle> }) => {
    if (!selectedTextId) return;
    if ('style' in partial) {
      updateText(selectedTextId, { style: { ...selectedText!.style, ...partial.style } } as any);
    } else {
      updateText(selectedTextId, partial as any);
    }
  };

  if (!selectedText) {
    return (
      <View style={styles.empty}>
        <Button title="Adicionar Novo Texto" icon="add" onPress={() => onAction('add_text')} />
      </View>
    );
  }

  return (
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.container}>
      <Text style={styles.sectionTitle}>Conteúdo</Text>
      <TextInput
        style={styles.input}
        value={selectedText.text}
        onChangeText={(text) => handleUpdate({ text })}
        placeholder="Digite seu texto..."
        placeholderTextColor={colors.textTertiary}
        multiline
      />

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Fonte & Estilo</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.fontList}>
        {FONT_OPTIONS.map(f => (
          <Pressable
            key={f}
            onPress={() => handleUpdate({ style: { fontFamily: f } })}
            style={[styles.fontBtn, selectedText.style.fontFamily === f && styles.fontBtnActive]}
          >
            <Text style={[styles.fontLabel, { fontFamily: f }, selectedText.style.fontFamily === f && { color: colors.primaryVariant }]}>Aa</Text>
            <Text style={[styles.fontName, selectedText.style.fontFamily === f && { color: colors.primaryVariant }]}>{f}</Text>
          </Pressable>
        ))}
        <Pressable onPress={() => onAction('import_font')} style={styles.fontBtn}>
           <Ionicons name="add" size={20} color={colors.textSecondary} />
           <Text style={styles.fontName}>Importar</Text>
        </Pressable>
      </ScrollView>

      <View style={{ marginTop: spacing.md }}>
         <Slider
            value={selectedText.style.fontSize}
            min={10} max={200}
            onChange={(v) => handleUpdate({ style: { fontSize: v } })}
            label="Tamanho" showValue
         />
      </View>

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Aparência</Text>
      <View style={styles.row}>
          {['#FFFFFF', '#000000', '#FF3B30', '#FFD60A', '#34C759', '#007AFF', '#AF52DE'].map(c => (
            <Pressable
                key={c}
                onPress={() => handleUpdate({ style: { color: c } })}
                style={[styles.colorCircle, { backgroundColor: c }, selectedText.style.color === c && styles.colorCircleActive]}
            />
          ))}
      </View>

      <View style={{ marginTop: spacing.lg }}>
        <Text style={styles.subLabel}>Alinhamento</Text>
        <SegmentedControl
            options={['Esquerda', 'Centro', 'Direita']}
            value={selectedText.style.textAlign === 'left' ? 'Esquerda' : selectedText.style.textAlign === 'center' ? 'Centro' : 'Direita'}
            onChange={(v) => handleUpdate({ style: { textAlign: v === 'Esquerda' ? 'left' : v === 'Centro' ? 'center' : 'right' } })}
        />
      </View>

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Contorno & Sombra</Text>
      <Slider
          value={selectedText.style.strokeWidth}
          min={0} max={10}
          onChange={(v) => handleUpdate({ style: { strokeWidth: v } })}
          label="Espessura do Contorno" showValue
      />

      <View style={styles.rowBetween}>
        <Text style={styles.subLabel}>Sombra Projetada</Text>
        <Pressable onPress={() => handleUpdate({ style: { shadowEnabled: !selectedText.style.shadowEnabled } })}>
           <Ionicons name={selectedText.style.shadowEnabled ? "checkbox" : "square-outline"} size={24} color={selectedText.style.shadowEnabled ? colors.primary : colors.textTertiary} />
        </Pressable>
      </View>

      <View style={styles.divider} />

      <Text style={styles.sectionTitle}>Fundo do Texto</Text>
      <View style={styles.rowBetween}>
        <Text style={styles.subLabel}>Habilitar Fundo</Text>
        <Pressable onPress={() => handleUpdate({ style: { backgroundEnabled: !selectedText.style.backgroundEnabled } })}>
           <Ionicons name={selectedText.style.backgroundEnabled ? "checkbox" : "square-outline"} size={24} color={selectedText.style.backgroundEnabled ? colors.primary : colors.textTertiary} />
        </Pressable>
      </View>
      {selectedText.style.backgroundEnabled && (
          <Slider
            value={selectedText.style.backgroundOpacity * 100}
            min={0} max={100}
            onChange={(v) => handleUpdate({ style: { backgroundOpacity: v / 100 } })}
            label="Opacidade do Fundo" showValue suffix="%"
          />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: spacing.xl },
  empty: { paddingVertical: spacing.xl, alignItems: 'center' },
  sectionTitle: { color: colors.textSecondary, fontSize: 11, fontWeight: fontWeight.bold, textTransform: 'uppercase', marginBottom: spacing.md, letterSpacing: 1, ...androidFont },
  input: { backgroundColor: colors.surface, borderRadius: radius.md, padding: spacing.md, color: colors.textPrimary, fontSize: 16, minHeight: 80, textAlignVertical: 'top', borderWidth: 1, borderColor: colors.border },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.lg },
  fontList: { gap: spacing.md },
  fontBtn: { width: 70, alignItems: 'center', gap: 4 },
  fontBtnActive: { opacity: 1 },
  fontLabel: { fontSize: 24, color: colors.textSecondary },
  fontName: { fontSize: 10, color: colors.textTertiary, ...androidFont },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  colorCircle: { width: 32, height: 32, borderRadius: 16, borderWidth: 2, borderColor: 'transparent' },
  colorCircleActive: { borderColor: colors.primary },
  subLabel: { color: colors.textSecondary, fontSize: 12, fontWeight: '500', marginBottom: 8, ...androidFont },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md },
});
