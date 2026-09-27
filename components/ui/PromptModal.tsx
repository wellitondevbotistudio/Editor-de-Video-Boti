import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Modal, TextInput, Pressable } from 'react-native';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface PromptModalProps {
  visible: boolean;
  title: string;
  defaultValue: string;
  onClose: () => void;
  onSubmit: (text: string) => void;
}

export function PromptModal({ visible, title, defaultValue, onClose, onSubmit }: PromptModalProps) {
  const [text, setText] = useState(defaultValue);

  useEffect(() => {
    if (visible) {
      setText(defaultValue);
    }
  }, [visible, defaultValue]);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={styles.alertBox}>
          <Text style={styles.title}>{title}</Text>
          <TextInput
            style={styles.input}
            value={text}
            onChangeText={setText}
            placeholder="Nome do projeto"
            placeholderTextColor={colors.textTertiary}
            autoFocus
          />
          <View style={styles.btnRow}>
            <Pressable style={[styles.btn, styles.cancelBtn]} onPress={onClose}>
              <Text style={styles.cancelText}>Cancelar</Text>
            </Pressable>
            <Pressable style={[styles.btn, styles.submitBtn]} onPress={() => onSubmit(text)}>
              <Text style={styles.submitText}>Salvar</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', alignItems: 'center', padding: spacing.xl },
  alertBox: { backgroundColor: colors.backgroundElevated, borderRadius: radius.xl, padding: spacing.xl, width: '100%', maxWidth: 320, borderWidth: 1, borderColor: colors.border },
  title: { color: colors.textPrimary, fontSize: fontSize.lg, fontWeight: fontWeight.bold, marginBottom: spacing.md, textAlign: 'center', ...androidFont },
  input: { backgroundColor: colors.surface, color: colors.textPrimary, borderRadius: radius.md, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, fontSize: fontSize.md, marginBottom: spacing.lg, borderWidth: 1, borderColor: colors.borderStrong, ...androidFont },
  btnRow: { flexDirection: 'row', gap: spacing.md, justifyContent: 'flex-end' },
  btn: { paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderRadius: radius.md, minWidth: 80, alignItems: 'center' },
  cancelBtn: { backgroundColor: colors.surface },
  submitBtn: { backgroundColor: colors.primary },
  cancelText: { color: colors.textSecondary, fontSize: fontSize.md, fontWeight: fontWeight.medium, ...androidFont },
  submitText: { color: colors.onPrimary, fontSize: fontSize.md, fontWeight: fontWeight.semibold, ...androidFont },
});
