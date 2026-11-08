/**
 * One texture database: virtualized list with lazy previews, search, attribute editing,
 * export / replace / delete, multi-select (long press) and .dat rebuild.
 */
import { FlashList } from '@shopify/flash-list';
import { Image } from 'expo-image';
import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { CheckSquare, Download, Plus, Recycle, Trash2, X } from 'lucide-react-native';
import { memo, useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type TexDbData, type TexEntry } from '../../modules/gta-native';
import { pickFiles } from '../components/FilePicker';
import { alert, ask, run } from '../components/Host';
import { Button, Field, IconButton, Input, Loading, Segmented, T, Tap, Toggle } from '../components/ui';
import { bytes, useT } from '../i18n';
import { appDir, baseName, pending, stripExt } from '../lib/paths';
import { useTheme } from '../lib/theme';

const IMAGE_EXTS = ['.png', '.jpg', '.jpeg', '.webp', '.bmp'];

const Thumb = memo(function Thumb({ path, e, size }: { path: string; e: TexEntry; size: number }) {
  const c = useTheme();
  const key = `${path}|${e.name}|${e.key}|${size}`;
  const [got, setGot] = useState<{ key: string; uri: string | null } | null>(null);
  const uri = got?.key === key ? got.uri : null;
  useEffect(() => {
    let alive = true;
    if (e.fmt !== 'alias') G.texdbPreview(path, e.name, size).then((u) => alive && setGot({ key, uri: u ? `file://${u}` : null })).catch(() => {});
    return () => {
      alive = false;
    };
  }, [key, path, e.name, e.fmt, size]);
  const px = size === 64 ? 48 : 220;
  // Checkerboard-ish backdrop so transparent textures stay visible.
  return (
    <View style={{ width: px, height: px, borderRadius: 10, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
      {uri ? <Image source={{ uri }} style={{ width: px, height: px }} contentFit="contain" recyclingKey={uri} transition={0} /> : null}
    </View>
  );
});

function attrsOf(line: string) {
  const out: Record<string, string> = {};
  for (const m of line.matchAll(/ (\w+)=(\S+)/g)) out[m[1]] = m[2];
  return out;
}

export default function TexDbScreen() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const { path } = useLocalSearchParams<{ path: string }>();
  const name = baseName(path);
  const [data, setData] = useState<TexDbData | null>(null);
  const [q, setQ] = useState('');
  const query = useDeferredValue(q.trim().toLowerCase());
  const [open, setOpen] = useState<TexEntry | null>(null);
  const [sel, setSel] = useState<Set<string> | null>(null);

  const load = useCallback((reload = false) => G.texdbOpen(path, reload).then(setData).catch((e) => alert(L.common.error, String(e?.message ?? e))), [path, L]);
  useFocusEffect(useCallback(() => {
    load();
  }, [load]));

  const list = useMemo(() => {
    if (!data) return [];
    return query ? data.entries.filter((e) => e.name.toLowerCase().includes(query)) : data.entries;
  }, [data, query]);

  const startImport = async (fixedName?: string) => {
    const files = await pickFiles({ exts: IMAGE_EXTS, multi: !fixedName });
    if (!files?.length) return;
    pending.texImport = { path, items: files.map((f) => ({ file: f, name: fixedName ?? stripExt(baseName(f)) })) };
    setOpen(null);
    router.push('/tex-import');
  };

  const exportNames = async (names: string[]) => {
    const dir = appDir(`export/${name}`);
    const n = await run(() => G.texdbExport(path, names, dir));
    if (n != null) alert(L.common.done, L.common.exportedTo(n, dir.replace(G.storageRoot(), '')));
  };

  const remove = async (names: string[]) => {
    if (!(await ask(L.common.delete, L.tex.removeAsk(names.length), L.common.delete, true))) return;
    await run(() => G.texdbRemove(path, names));
    setOpen(null);
    setSel(null);
    load();
  };

  const compact = async () => {
    if (!(await ask(L.tex.compact, L.tex.compactAsk, L.tex.compact))) return;
    const saved = await run(() => G.texdbCompact(path));
    if (saved != null) alert(L.common.done, L.tex.compactDone(bytes(saved)));
    load();
  };

  const toggleSel = (n: string) => {
    const s = new Set(sel ?? []);
    if (s.has(n)) s.delete(n);
    else s.add(n);
    setSel(s.size ? s : null);
  };

  if (!data) return <Loading />;
  return (
    <View style={{ flex: 1 }}>
      <Stack.Screen options={{
        title: sel ? L.common.selected(sel.size) : name,
        headerRight: () => sel ? (
          <View style={{ flexDirection: 'row' }}>
            <IconButton icon={CheckSquare} label={L.common.all} onPress={() => setSel(new Set(list.filter((e) => e.fmt !== 'alias').map((e) => e.name)))} />
            <IconButton icon={Download} label={L.common.export} onPress={() => exportNames([...sel])} />
            <IconButton icon={Trash2} label={L.common.delete} color={c.danger} onPress={() => remove([...sel])} />
            <IconButton icon={X} label={L.common.close} onPress={() => setSel(null)} />
          </View>
        ) : <IconButton icon={Plus} label={L.common.import} onPress={() => startImport()} />,
      }} />
      <View style={{ paddingHorizontal: 16, paddingBottom: 8, gap: 8 }}>
        <Input value={q} onChange={setQ} placeholder={L.tex.filter} />
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <T dim size={12.5} style={{ flex: 1 }}>
            {L.tex.textures(list.length)} · .dat {bytes(data.datSize)}{data.wasted > 0 ? ` · ${L.tex.wasted(bytes(data.wasted))}` : ''}
          </T>
          {data.wasted > 0 ? <Button small kind="soft" icon={Recycle} title={L.tex.compact} onPress={compact} /> : null}
        </View>
      </View>
      <FlashList
        data={list}
        keyExtractor={(e) => e.name}
        extraData={sel}
        contentContainerStyle={{ paddingBottom: insets.bottom + 16 }}
        renderItem={({ item }) => {
          const on = sel?.has(item.name);
          return (
            <Tap onPress={() => (sel ? toggleSel(item.name) : setOpen(item))} onLongPress={() => toggleSel(item.name)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 7, backgroundColor: on ? c.accentSoft : undefined }}>
              <Thumb path={path} e={item} size={64} />
              <View style={{ flex: 1, gap: 2 }}>
                <T semi numberOfLines={1}>{item.name}</T>
                <T dim size={12} numberOfLines={1}>
                  {item.fmt === 'alias' ? `${L.tex.alias}: ${attrsOf(item.line).affiliate ?? item.line.split('"')[3] ?? ''}` : `${item.w}×${item.h} · ${item.fmt}${item.mips ? ' · mips' : ''}`}
                </T>
              </View>
            </Tap>
          );
        }}
      />
      {open ? <Details path={path} e={open} onClose={() => setOpen(null)} onChanged={() => load()} onExport={() => exportNames([open.name])}
        onReplace={() => startImport(open.name)} onRemove={() => remove([open.name])} /> : null}
    </View>
  );
}

function Details({ path, e, onClose, onChanged, onExport, onReplace, onRemove }: {
  path: string; e: TexEntry; onClose: () => void; onChanged: () => void; onExport: () => void; onReplace: () => void; onRemove: () => void;
}) {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const a0 = attrsOf(e.line);
  const [alphamode, setAlphamode] = useState(a0.alphamode ?? '0');
  const [camnorm, setCamnorm] = useState(a0.camnorm === '1');
  const [streammode, setStreammode] = useState(a0.streammode === '1');
  const [line, setLine] = useState(e.line);
  const changed = alphamode !== (a0.alphamode ?? '0') || camnorm !== (a0.camnorm === '1') || streammode !== (a0.streammode === '1');
  const save = async () => {
    const l = await run(() => G.texdbSetAttrs(path, e.name, { alphamode, camnorm: camnorm ? '1' : '0', streammode: streammode ? '1' : '0' }));
    if (l) {
      setLine(l);
      onChanged();
    }
  };
  return (
    <Modal transparent visible animationType="slide" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <Pressable onPress={onClose} style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'flex-end' }}>
        <Pressable style={{ backgroundColor: c.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, maxHeight: '88%', paddingBottom: insets.bottom + 12 }}>
          <ScrollView contentContainerStyle={{ paddingVertical: 16, gap: 4 }}>
            <View style={{ alignItems: 'center', gap: 8, paddingHorizontal: 16 }}>
              <Thumb path={path} e={e} size={256} />
              <T semi size={17} center>{e.name}</T>
              {e.fmt !== 'alias' ? <T dim size={13}>{`${e.w}×${e.h} · ${e.fmt}${e.mips ? ' · mips' : ''}`}</T> : null}
            </View>
            {e.fmt !== 'alias' ? (
              <>
                <Field label={L.tex.alphamode} hint={L.tex.alphaHint}>
                  <Segmented value={alphamode} onChange={setAlphamode} options={['0', '1', '2'].map((v) => ({ value: v, label: v }))} />
                </Field>
                <Toggle title={L.tex.camnorm} hint={L.tex.camnormHint} value={camnorm} onChange={setCamnorm} />
                <Toggle title={L.tex.streammode} hint={L.tex.streamHint} value={streammode} onChange={setStreammode} />
                {changed ? <View style={{ paddingHorizontal: 16 }}><Button title={L.tex.saveAttrs} onPress={save} /></View> : null}
              </>
            ) : null}
            <View style={{ paddingHorizontal: 16, paddingTop: 8 }}>
              <T dim mono size={11.5}>{line}</T>
            </View>
            <View style={{ flexDirection: 'row', gap: 8, paddingHorizontal: 16, paddingTop: 12 }}>
              {e.fmt !== 'alias' ? <Button flex small kind="soft" icon={Download} title={L.tex.exportPng} onPress={onExport} /> : null}
              <Button flex small kind="soft" title={L.tex.replacePng} onPress={onReplace} />
            </View>
            <View style={{ paddingHorizontal: 16, paddingTop: 8 }}>
              <Button small kind="danger" icon={Trash2} title={L.common.delete} onPress={onRemove} />
            </View>
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
