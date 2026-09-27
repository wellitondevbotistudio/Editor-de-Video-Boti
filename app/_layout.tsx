// Powered by OnSpace.AI
import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { AlertProvider } from '@/template';
import { colors } from '@/constants/theme';

export default function RootLayout() {
  return (
    <AlertProvider>
      <SafeAreaProvider>
        <GestureHandlerRootView style={{ flex: 1, backgroundColor: colors.background }}>
          <StatusBar style="light" />
          <Stack
            screenOptions={{
              headerShown: false,
              contentStyle: { backgroundColor: colors.background },
              animation: 'slide_from_right',
            }}
          >
            <Stack.Screen name="index" />
            <Stack.Screen name="onboarding" />
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="import" options={{ animation: 'slide_from_bottom' }} />
            <Stack.Screen name="editor" />
            <Stack.Screen name="export" options={{ animation: 'slide_from_bottom' }} />
            <Stack.Screen name="player" options={{ animation: 'fade' }} />
            <Stack.Screen name="settings" />
            <Stack.Screen name="captions" />
          </Stack>
        </GestureHandlerRootView>
      </SafeAreaProvider>
    </AlertProvider>
  );
}
