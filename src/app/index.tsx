/** Home: storage permission, game folder, tools. */
import { router, useFocusEffect } from 'expo-router';
import { AudioLines, Box, FolderOpen, Heart, Image as ImageIcon, Settings as Cog, Wand2 } from 'lucide-react-native';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, PermissionsAndroid, Platform, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G } from '../../modules/gta-native';
import { pickDir } from '../components/FilePicker';
import { Button, Card, Divider, IconButton, Row, T } from '../components/ui';
import { useT } from '../i18n';
import { setSettings, useSettings } from '../lib/store';
import { useTheme } from '../lib/theme';

function useStorageAccess() {
  const [ok, setOk] = useState(() => G.hasStorageAccess());
  // Android 8–10 has no "All files access": the classic storage permission is asked at runtime instead.
  const [legacy, setLegacy] = useState(Platform.OS === 'android' && Number(Platform.Version) < 30);
  useEffect(() => {
    if (legacy) PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE).then((r) => setLegacy(r !== 'granted')).catch(() => {});
  }, [legacy]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => s === 'active' && setOk(G.hasStorageAccess()));
    return () => sub.remove();
  }, []);
  return ok && !legacy;
}

export default function Home() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const access = useStorageAccess();
  const gameDir = useSettings((s) => s.gameDir);
  const [found, setFound] = useState<string[] | null>(null);
  const [choosing, setChoosing] = useState(false);
  const root = G.storageRoot();

  const scan = useCallback(() => {
    setFound(null);
    G.findGameDirs().then(setFound).catch(() => setFound([]));
  }, []);

  useFocusEffect(useCallback(() => {
    if (access && (!gameDir || choosing)) scan();
  }, [access, gameDir, choosing, scan]));

  const choose = (d: string) => {
    setSettings({ gameDir: d });
    setChoosing(false);
  };

  const tools = [
    { icon: ImageIcon, title: L.home.textures, sub: L.home.texturesHint, href: '/textures' },
    { icon: Box, title: L.home.img, sub: L.home.imgHint, href: '/img' },
    { icon: AudioLines, title: L.home.audio, sub: L.home.audioHint, href: '/audio' },
    { icon: Wand2, title: L.home.adapt, sub: L.home.adaptHint, href: '/adapt' },
  ] as const;

  return (
    <ScrollView style={{ backgroundColor: c.bg }} contentContainerStyle={{ paddingTop: insets.top + 12, paddingBottom: insets.bottom + 24, paddingHorizontal: 16, gap: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 6 }}>
        <View style={{ flex: 1 }}>
          <T bold size={24}>{L.home.title}</T>
          <T dim size={13.5}>{L.home.subtitle}</T>
        </View>
        <IconButton icon={Heart} color={c.accent} label={L.home.support} onPress={() => router.push('/support')} />
        <IconButton icon={Cog} label={L.home.settings} onPress={() => router.push('/settings')} />
      </View>

      {!access ? (
        <Card style={{ padding: 18, gap: 10 }}>
          <T semi size={17}>{L.perm.title}</T>
          <T dim>{L.perm.text}</T>
          <Button title={L.perm.grant} onPress={() => G.openStorageSettings()} />
        </Card>
      ) : !gameDir || choosing ? (
        <Card style={{ paddingVertical: 8 }}>
          <View style={{ padding: 16, paddingBottom: 8, gap: 6 }}>
            <T semi size={17}>{L.pick.title}</T>
            <T dim size={13.5}>{L.pick.text}</T>
          </View>
          {found === null ? (
            <View style={{ flexDirection: 'row', gap: 10, padding: 16, alignItems: 'center' }}>
              <ActivityIndicator color={c.accent} />
              <T dim>{L.pick.scanning}</T>
            </View>
          ) : found.length === 0 ? <T dim style={{ paddingHorizontal: 16, paddingVertical: 8 }}>{L.pick.nothing}</T> : (
            found.map((d) => <Row key={d} icon={FolderOpen} title={d.split('/').pop()!} sub={d.replace(root, '')} onPress={() => choose(d)} active={d === gameDir} />)
          )}
          <View style={{ padding: 16, flexDirection: 'row', gap: 10 }}>
            <Button flex kind="soft" title={L.pick.manual} onPress={async () => {
              const d = await pickDir(root);
              if (d) choose(d);
            }} />
            {gameDir ? <Button kind="ghost" title={L.common.cancel} onPress={() => setChoosing(false)} /> : null}
          </View>
        </Card>
      ) : (
        <>
          <Card>
            <Row icon={FolderOpen} title={gameDir.split('/').pop()!} sub={`${L.home.gameDir}: ${gameDir.replace(root, '')}`}
              right={<Button small kind="soft" title={L.home.change} onPress={() => setChoosing(true)} />} />
          </Card>
          <Card>
            {tools.map((t, i) => (
              <View key={t.href}>
                {i ? <Divider /> : null}
                <Row icon={t.icon} title={t.title} sub={t.sub} onPress={() => router.push(t.href)} />
              </View>
            ))}
          </Card>
        </>
      )}
    </ScrollView>
  );
}
