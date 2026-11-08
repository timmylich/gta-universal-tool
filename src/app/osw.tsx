/**
 * One audio pack: play tracks, queue replacements (mp3), append tracks, then rebuild the pack once.
 */
import { FlashList } from '@shopify/flash-list';
import { useAudioPlayer, useAudioPlayerStatus } from 'expo-audio';
import { Stack, useLocalSearchParams } from 'expo-router';
import { Download, Pause, Play, Plus, Replace, Undo2 } from 'lucide-react-native';
import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type Track } from '../../modules/gta-native';
import { pickFiles } from '../components/FilePicker';
import { alert, run } from '../components/Host';
import { Button, IconButton, Loading, T } from '../components/ui';
import { bytes, useT } from '../i18n';
import { appDir, baseName } from '../lib/paths';
import { useTheme } from '../lib/theme';

export default function Osw() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const { path } = useLocalSearchParams<{ path: string }>();
  const [tracks, setTracks] = useState<Track[] | null>(null);
  const [repl, setRepl] = useState<Record<number, string>>({});
  const [added, setAdded] = useState<string[]>([]);
  const [playing, setPlaying] = useState<number | null>(null);
  const player = useAudioPlayer(null);
  const status = useAudioPlayerStatus(player);

  const load = useCallback(() => G.oswTracks(path).then(setTracks).catch((e) => alert(L.common.error, String(e?.message ?? e))), [path, L]);
  useEffect(() => {
    load();
  }, [load]);

  const play = async (i: number) => {
    if (playing === i && status.playing) {
      player.pause();
      setPlaying(null);
      return;
    }
    const file = repl[i] ?? (await G.oswPreview(path, i));
    player.replace({ uri: `file://${file}` });
    player.play();
    setPlaying(i);
  };

  const replace = async (i: number) => {
    const f = await pickFiles({ exts: ['.mp3'] });
    if (f?.[0]) setRepl({ ...repl, [i]: f[0] });
  };

  const exportTrack = async (i: number, t: Track) => {
    const dest = `${appDir(`export/${baseName(path)}`)}/${t.name}`;
    const r = await run(() => G.oswExtract(path, i, dest));
    if (r) alert(L.common.done, r.replace(G.storageRoot(), ''));
  };

  const apply = async () => {
    player.pause();
    setPlaying(null);
    const n = await run(() => G.oswApply(path, Object.fromEntries(Object.entries(repl)), added));
    if (n == null) return;
    setRepl({});
    setAdded([]);
    alert(L.common.done, L.audio.applied);
    load();
  };

  const pendingCount = Object.keys(repl).length + added.length;
  if (!tracks) return <Loading />;
  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{
        title: baseName(path).replace(/\.osw$/i, ''),
        headerRight: () => <IconButton icon={Plus} label={L.audio.addTrack} onPress={async () => {
          const f = await pickFiles({ exts: ['.mp3'], multi: true });
          if (f?.length) setAdded([...added, ...f]);
        }} />,
      }} />
      <T dim size={12.5} style={{ paddingHorizontal: 16, paddingBottom: 6 }}>{L.audio.tracks(tracks.length)}</T>
      <FlashList
        data={tracks}
        keyExtractor={(t) => t.name}
        extraData={[repl, playing, status.playing]}
        contentContainerStyle={{ paddingBottom: insets.bottom + 90 }}
        ListFooterComponent={added.length ? (
          <View style={{ paddingHorizontal: 16, paddingTop: 10, gap: 4 }}>
            {added.map((a) => <T key={a} color={c.ok} size={13}>+ {baseName(a)}</T>)}
          </View>
        ) : null}
        renderItem={({ item, index }) => {
          const r = repl[index];
          return (
            <View style={{ flexDirection: 'row', alignItems: 'center', paddingLeft: 6, paddingRight: 8, paddingVertical: 2, backgroundColor: r ? c.accentSoft : undefined }}>
              <IconButton icon={playing === index && status.playing ? Pause : Play} label={L.audio.play} color={c.accent} onPress={() => play(index)} />
              <View style={{ flex: 1 }}>
                <T numberOfLines={1}>{`${index + 1}. ${item.name}`}</T>
                <T dim size={12} numberOfLines={1}>{r ? `${L.audio.pending}: ${baseName(r)}` : bytes(item.size)}</T>
              </View>
              {r ? <IconButton icon={Undo2} label={L.common.cancel} onPress={() => {
                const n = { ...repl };
                delete n[index];
                setRepl(n);
              }} /> : <IconButton icon={Download} label={L.common.export} onPress={() => exportTrack(index, item)} />}
              <IconButton icon={Replace} label={L.audio.replaceWith} onPress={() => replace(index)} />
            </View>
          );
        }}
      />
      {pendingCount ? (
        <View style={{ position: 'absolute', left: 16, right: 16, bottom: insets.bottom + 16 }}>
          <Button title={L.audio.apply(pendingCount)} onPress={apply} />
        </View>
      ) : null}
    </View>
  );
}
