/** Root navigator: theme, global overlays (dialogs, busy spinner, file browser, donation prompt). */
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SystemUI from 'expo-system-ui';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { G } from '../../modules/gta-native';
import { DonationPrompt } from '../components/Donation';
import { FilePickerHost } from '../components/FilePicker';
import { Host } from '../components/Host';
import { useT } from '../i18n';
import { useSettings } from '../lib/store';
import { useTheme } from '../lib/theme';

export default function RootLayout() {
  const c = useTheme();
  const L = useT();
  const backup = useSettings((s) => s.backup);
  useEffect(() => {
    G.setBackupDir(backup ? `${G.storageRoot()}/GTAUniversalTool/backup` : null);
  }, [backup]);
  useEffect(() => {
    SystemUI.setBackgroundColorAsync(c.bg).catch(() => {});
  }, [c.bg]);
  const t = (title: string) => ({ title });
  return (
    <SafeAreaProvider>
      <StatusBar style={c.dark ? 'light' : 'dark'} />
      <Stack screenOptions={{
        headerStyle: { backgroundColor: c.bg }, headerTintColor: c.text, headerTitleStyle: { fontWeight: '600', fontSize: 19 },
        headerShadowVisible: false, contentStyle: { backgroundColor: c.bg }, animation: 'ios_from_right',
      }}>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="textures" options={t(L.tex.title)} />
        <Stack.Screen name="texdb" options={t('')} />
        <Stack.Screen name="tex-import" options={t(L.tex.importTitle)} />
        <Stack.Screen name="img" options={t(L.img.title)} />
        <Stack.Screen name="imgview" options={t('')} />
        <Stack.Screen name="audio" options={t(L.audio.title)} />
        <Stack.Screen name="osw" options={t('')} />
        <Stack.Screen name="adapt" options={t(L.adapt.title)} />
        <Stack.Screen name="settings" options={t(L.settings.title)} />
        <Stack.Screen name="support" options={t(L.home.support)} />
      </Stack>
      <FilePickerHost />
      <Host />
      <DonationPrompt />
    </SafeAreaProvider>
  );
}
