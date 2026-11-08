/**
 * One IMG archive: search, add/replace files (case-insensitive match keeps the stored name),
 * warning before adding new entries, export, delete, rebuild.
 */
import { FlashList } from '@shopify/flash-list';
import { Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { CheckSquare, Download, Plus, Recycle, Trash2, X } from 'lucide-react-native';
import { useCallback, useDeferredValue, useMemo, useState } from 'react';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type ImgData } from '../../modules/gta-native';
import { pickFiles } from '../components/FilePicker';
import { alert, ask, run } from '../components/Host';
import { confirmNewEntries } from '../lib/imgConfirm';
import { Button, IconButton, Input, Loading, T, Tap } from '../components/ui';
import { bytes, useT } from '../i18n';
import { appDir, baseName } from '../lib/paths';
import { useTheme } from '../lib/theme';

export default function ImgView() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const { path } = useLocalSearchParams<{ path: string }>();
  const [data, setData] = useState<ImgData | null>(null);
  const [q, setQ] = useState('');
  const query = useDeferredValue(q.trim().toLowerCase());
  const [sel, setSel] = useState<Set<string> | null>(null);

  const load = useCallback((reload = false) => G.imgOpen(path, reload).then(setData).catch((e) => alert(L.common.error, String(e?.message ?? e))), [path, L]);
  useFocusEffect(useCallback(() => {
    load();
  }, [load]));

  const list = useMemo(() => (data ? (query ? data.entries.filter((e) => e.name.toLowerCase().includes(query)) : data.entries) : []), [data, query]);
  const waste = data ? data.fileSize - data.used : 0;

  const add = async () => {
    const files = await pickFiles({ multi: true });
    if (!files?.length) return;
    if (!(await confirmNewEntries(path, files.map(baseName)))) return;
    const r = await run(() => G.imgImport(path, files));
    if (r) alert(L.common.done, L.img.result(r.added.length, r.replaced.length, r.failed.length) + (r.failed.length ? `\n\n${r.failed.join('\n')}` : ''));
    load();
  };

  const exportNames = async (names: string[]) => {
    const dir = appDir(`export/${baseName(path)}`);
    const n = await run(() => G.imgExport(path, names, dir));
    if (n != null) alert(L.common.done, L.common.exportedTo(n, dir.replace(G.storageRoot(), '')));
  };

  const remove = async (names: string[]) => {
    if (!(await ask(L.common.delete, L.img.removeAsk(names.length), L.common.delete, true))) return;
    await run(() => G.imgRemove(path, names));
    setSel(null);
    load();
  };

  const rebuild = async () => {
    if (!(await ask(L.img.rebuild, L.img.rebuildAsk, L.img.rebuild))) return;
    const saved = await run(() => G.imgRebuild(path));
    if (saved != null) alert(L.common.done, L.img.rebuildDone(bytes(saved)));
    load();
  };

  const actions = (name: string) => alert(name, undefined, [
    { text: L.common.export, onPress: () => exportNames([name]) },
    { text: L.img.replaceFile, onPress: async () => {
      const f = await pickFiles({});
      if (!f?.[0]) return;
      await run(() => G.imgReplace(path, name, f[0]));
      load();
    } },
    { text: L.common.delete, style: 'destructive', onPress: () => remove([name]) },
    { text: L.common.cancel, style: 'cancel' },
  ]);

  const toggle = (n: string) => {
    const s = new Set(sel ?? []);
    if (s.has(n)) s.delete(n);
    else s.add(n);
    setSel(s.size ? s : null);
  };

  if (!data) return <Loading />;
  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{
        title: sel ? L.common.selected(sel.size) : baseName(path),
        headerRight: () => sel ? (
          <View style={{ flexDirection: 'row' }}>
            <IconButton icon={CheckSquare} label={L.common.all} onPress={() => setSel(new Set(list.map((e) => e.name)))} />
            <IconButton icon={Download} label={L.common.export} onPress={() => exportNames([...sel])} />
            <IconButton icon={Trash2} label={L.common.delete} color={c.danger} onPress={() => remove([...sel])} />
            <IconButton icon={X} label={L.common.close} onPress={() => setSel(null)} />
          </View>
        ) : <IconButton icon={Plus} label={L.img.addFiles} onPress={add} />,
      }} />
      <View style={{ paddingHorizontal: 16, paddingBottom: 8, gap: 8 }}>
        <Input value={q} onChange={setQ} placeholder={L.common.search} />
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <T dim size={12.5} style={{ flex: 1 }}>
            {L.img.entries(list.length)} · {bytes(data.fileSize)}{waste > 0 ? ` · ${L.img.rebuildHint(bytes(waste))}` : ''}
          </T>
          <Button small kind="soft" icon={Recycle} title={L.img.rebuild} onPress={rebuild} />
        </View>
      </View>
      <FlashList
        data={list}
        keyExtractor={(e) => e.name}
        extraData={sel}
        contentContainerStyle={{ paddingBottom: insets.bottom + 16 }}
        renderItem={({ item }) => (
          <Tap onPress={() => (sel ? toggle(item.name) : actions(item.name))} onLongPress={() => toggle(item.name)}
            style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 11, backgroundColor: sel?.has(item.name) ? c.accentSoft : undefined }}>
            <T style={{ flex: 1 }} numberOfLines={1}>{item.name}</T>
            <T dim size={12.5}>{bytes(item.size)}</T>
          </Tap>
        )}
      />
    </View>
  );
}
