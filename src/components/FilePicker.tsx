/**
 * Built-in file browser over shared storage (the app has "All files access", so no SAF copies are needed).
 * `pickFiles({ exts, multi })` / `pickDir()` resolve to absolute paths, or null when cancelled.
 */
import { FlashList } from '@shopify/flash-list';
import { ArrowUp, Check, File as FileIcon, Folder, X } from 'lucide-react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { BackHandler, Modal, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type DirItem } from '../../modules/gta-native';
import { bytes, useT } from '../i18n';
import { getSettings, setSettings } from '../lib/store';
import { useTheme } from '../lib/theme';
import { Button, IconButton, T, Tap } from './ui';

type Req = { mode: 'files' | 'dir'; exts?: string[]; multi?: boolean; start?: string; resolve: (v: string[] | null) => void };
let open: ((r: Req) => void) | null = null;

export function pickFiles(o: { exts?: string[]; multi?: boolean; start?: string }): Promise<string[] | null> {
  return new Promise((resolve) => open?.({ mode: 'files', ...o, resolve }));
}

export function pickDir(start?: string): Promise<string | null> {
  return new Promise((resolve) => open?.({ mode: 'dir', start, resolve: (v) => resolve(v?.[0] ?? null) }));
}

const parent = (p: string) => p.replace(/\/[^/]+\/?$/, '') || '/';

export function FilePickerHost() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const [req, setReq] = useState<Req | null>(null);
  const [dir, setDir] = useState('');
  const [items, setItems] = useState<DirItem[]>([]);
  const [sel, setSel] = useState<Set<string>>(new Set());
  const root = useMemo(() => G.storageRoot(), []);

  useEffect(() => {
    open = (r) => {
      setReq(r);
      setSel(new Set());
      setDir(r.start ?? getSettings().lastDir ?? root);
    };
    return () => {
      open = null;
    };
  }, [root]);

  useEffect(() => {
    if (!req || !dir) return;
    let alive = true;
    G.listDir(dir).then((list) => {
      if (!alive) return;
      const ext = req.exts?.map((e) => e.toLowerCase());
      const shown = list.filter((i) => !i.name.startsWith('.') && (i.dir || (req.mode === 'files' && (!ext || ext.some((e) => i.name.toLowerCase().endsWith(e))))));
      shown.sort((a, b) => (a.dir === b.dir ? a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }) : a.dir ? -1 : 1));
      setItems(shown);
    }).catch(() => setItems([]));
    return () => {
      alive = false;
    };
  }, [req, dir]);

  const finish = useCallback((v: string[] | null) => {
    if (v) setSettings({ lastDir: dir });
    req?.resolve(v);
    setReq(null);
  }, [req, dir]);

  const goUp = useCallback(() => {
    if (dir === root || dir === '/') finish(null);
    else setDir(parent(dir));
  }, [dir, root, finish]);

  useEffect(() => {
    if (!req) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      goUp();
      return true;
    });
    return () => sub.remove();
  }, [req, goUp]);

  const tap = (i: DirItem) => {
    const p = `${dir}/${i.name}`;
    if (i.dir) return setDir(p);
    if (!req?.multi) return finish([p]);
    const s = new Set(sel);
    if (s.has(p)) s.delete(p);
    else s.add(p);
    setSel(s);
  };

  return (
    <Modal visible={!!req} animationType="slide" onRequestClose={goUp} statusBarTranslucent navigationBarTranslucent>
      <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top, paddingBottom: insets.bottom }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 6, paddingVertical: 6, gap: 4 }}>
          <IconButton icon={X} label={L.common.close} onPress={() => finish(null)} />
          <IconButton icon={ArrowUp} label={L.browser.up} onPress={goUp} />
          <T dim size={13} numberOfLines={2} style={{ flex: 1 }}>{dir.replace(root, '') || '/'}</T>
        </View>
        <FlashList
          data={items}
          keyExtractor={(i) => i.name}
          extraData={sel}
          ListEmptyComponent={<T dim center style={{ padding: 30 }}>{L.browser.empty}</T>}
          renderItem={({ item }) => {
            const on = sel.has(`${dir}/${item.name}`);
            const Icon = item.dir ? Folder : FileIcon;
            return (
              <Tap onPress={() => tap(item)} style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: 18, paddingVertical: 12, backgroundColor: on ? c.accentSoft : undefined }}>
                <Icon size={22} color={item.dir ? c.accent : c.textDim} />
                <View style={{ flex: 1 }}>
                  <T numberOfLines={1}>{item.name}</T>
                  {!item.dir ? <T dim size={12}>{bytes(item.size)}</T> : null}
                </View>
                {on ? <Check size={20} color={c.accent} /> : null}
              </Tap>
            );
          }}
        />
        <View style={{ padding: 12, gap: 8 }}>
          {req?.mode === 'dir' ? <Button title={L.browser.useFolder} onPress={() => finish([dir])} /> : null}
          {req?.mode === 'files' && req.multi ? (
            <Button title={L.browser.pickN(sel.size)} disabled={!sel.size} onPress={() => finish([...sel])} />
          ) : null}
        </View>
      </View>
    </Modal>
  );
}
