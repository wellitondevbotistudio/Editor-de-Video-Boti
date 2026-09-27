
// Powered by OnSpace.AI
import React, { useMemo, useRef, useState } from 'react';
import { View, Text, StyleSheet, PanResponder, LayoutChangeEvent } from 'react-native';
import { colors, radius, spacing, fontSize, fontWeight } from '@/constants/theme';
import { androidFont } from '@/constants/styles';

interface SliderProps {
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onChange: (v: number) => void;
  label?: string;
  showValue?: boolean;
  suffix?: string;
}

export function Slider({ value, min = 0, max = 100, step = 1, onChange, label, showValue, suffix = '' }: SliderProps) {
  const [width, setWidth] = useState(0);
  const widthRef = useRef(0);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const responder = useMemo(() => {
    const compute = (x: number) => {
      const w = widthRef.current || 1;
      const ratio = Math.max(0, Math.min(1, x / w));
      let v = min + ratio * (max - min);
      if (step) v = Math.round(v / step) * step;
      v = Math.max(min, Math.min(max, v));
      onChangeRef.current(v);
    };

    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (e) => compute(e.nativeEvent.locationX),
      onPanResponderMove: (e) => compute(e.nativeEvent.locationX),
    });
  }, [min, max, step]);

  const pct = Math.max(0, Math.min(1, (value - min) / (max - min)));

  return (
    <View>
      {label ? (
        <View style={styles.header}>
          <Text style={styles.label}>{label}</Text>
          {showValue ? <Text style={styles.value}>{`${Math.round(value)}${suffix}`}</Text> : null}
        </View>
      ) : null}
      <View style={styles.touch} {...responder.panHandlers}>
        <View
          style={styles.track}
          onLayout={(e: LayoutChangeEvent) => {
            const w = e.nativeEvent.layout.width;
            setWidth(w);
            widthRef.current = w;
          }}
        >
          <View style={[styles.fill, { width: `${pct * 100}%` }]} />
          <View style={[styles.thumb, { left: Math.max(0, pct * width - 11) }]} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.sm },
  label: { color: colors.textSecondary, fontSize: fontSize.sm, ...androidFont },
  value: { color: colors.primaryVariant, fontSize: fontSize.sm, fontWeight: fontWeight.semibold, ...androidFont },
  touch: { paddingVertical: spacing.sm, justifyContent: 'center' },
  track: { height: 6, borderRadius: radius.pill, backgroundColor: colors.track, justifyContent: 'center' },
  fill: { height: 6, borderRadius: radius.pill, backgroundColor: colors.primary },
  thumb: {
    position: 'absolute',
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#FFFFFF',
    borderWidth: 3,
    borderColor: colors.primary,
    top: -8,
  },
});
