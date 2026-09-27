import React from 'react';
import { View, StyleSheet } from 'react-native';
import { GestureDetector, Gesture } from 'react-native-gesture-handler';
import Animated, { useSharedValue, useAnimatedStyle } from 'react-native-reanimated';

export const Timeline = () => {
  const position = useSharedValue(0);

  const panGesture = Gesture.Pan()
    .onChange((event) => {
      position.value += event.changeX;
      if (position.value < 0) position.value = 0; // Limite inicial
    });

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: position.value }],
  }));

  return (
    <View style={styles.container}>
      <View style={styles.track}>
        {/* Futuros blocos de vídeo e áudio entrarão aqui */}
      </View>
      <GestureDetector gesture={panGesture}>
        <Animated.View style={[styles.cursor, animatedStyle]} />
      </GestureDetector>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { 
    height: 80, 
    backgroundColor: '#151718', 
    justifyContent: 'center', 
    marginVertical: 20 
  },
  track: { 
    height: 50, 
    backgroundColor: '#2A2D30', 
    borderRadius: 8, 
    marginHorizontal: 10 
  },
  cursor: { 
    width: 4, 
    height: 80, 
    backgroundColor: '#0a7ea4', 
    position: 'absolute', 
    left: 10 
  },
});