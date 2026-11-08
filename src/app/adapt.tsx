/**
 * PC mods → mobile.
 * Models mode: several .dff and .txd at once (one .txd may serve many models); textures get unique
 * "prefix_random" names, models are rewritten, unused textures dropped; optional import into a texdb and an IMG.
 * Retexture mode: only .txd; textures replace the ones with the same names wherever they are in the databases.
 */
import { FileBox, Images, Plus, X } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type AdaptInfo, type ImgInfo, type RetexItem, type TexDbInfo, type TexImportOptions } from '../../modules/gta-native';
import { pickFiles } from '../components/FilePicker';
import { alert, run } from '../components/Host';
import { ImportOptions } from '../components/ImportOptions';
import { Button, Card, Divider, Field, Input, Segmented, T, Tap, Toggle } from '../components/ui';
import { useT } from '../i18n';
import { confirmNewEntries } from '../lib/imgConfirm';
import { appDir, baseName, stripExt } from '../lib/paths';
import { getSettings, setSettings, useSettings } from '../lib/store';
import { useTheme } from '../lib/theme';

const uniq = (a: string[]) => [...new Set(a)];

function Files({ icon: Icon, title, files, onAdd, onRemove, addLabel }: {
  icon: typeof FileBox; title: string; files: string[]; onAdd: () => void; onRemove: (f: string) => void; addLabel: string;
}) {
  const c = useTheme();
  return (
    <View style={{ paddingVertical: 10, gap: 8 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 16 }}>
        <Icon size={20} color={c.accent} />
        <T semi style={{ flex: 1 }}>{`${title}${files.length ? ` · ${files.length}` : ''}`}</T>
        <Button small kind="soft" icon={Plus} title={addLabel} onPress={onAdd} />
      </View>
      {files.length ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, paddingHorizontal: 16 }}>
          {files.map((f) => (
            <Tap key={f} onPress={() => onRemove(f)} label={`${baseName(f)} ×`}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: c.cardAlt, borderRadius: 10, paddingLeft: 10, paddingRight: 6, paddingVertical: 5 }}>
              <T size={13}>{baseName(f)}</T>
              <X size={14} color={c.textDim} />
            </Tap>
          ))}
        </View>
      ) : null}
    </View>
  );
}

function Lines({ title, items, color, max = 30 }: { title: string; items: string[]; color?: string; max?: number }) {
  const c = useTheme();
  if (!items.length) return null;
  const shown = items.slice(0, max).join('\n') + (items.length > max ? `\n… +${items.length - max}` : '');
  return (
    <View style={{ paddingHorizontal: 16, paddingVertical: 8, gap: 3 }}>
      <T semi size={13.5}>{`${title}: ${items.length}`}</T>
      <T mono size={12} color={color ?? c.textDim}>{shown}</T>
    </View>
  );
}

const stamp = () => new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');

export default function Adapt() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const gameDir = useSettings((s) => s.gameDir);
  const prefix = useSettings((s) => s.prefix);
  const [mode, setMode] = useState<'models' | 'retex'>('models');
  const [dffs, setDffs] = useState<string[]>([]);
  const [txds, setTxds] = useState<string[]>([]);
  const [info, setInfo] = useState<{ key: string; models?: AdaptInfo; retex?: RetexItem[] } | null>(null);
  const [dbs, setDbs] = useState<TexDbInfo[]>([]);
  const [imgs, setImgs] = useState<ImgInfo[]>([]);
  const [db, setDb] = useState<string | null>(null);
  const [img, setImg] = useState<string | null>(null);
  const [toDb, setToDb] = useState(true);
  const [toImg, setToImg] = useState(false);
  const [addMissing, setAddMissing] = useState(false);
  const [o, setO] = useState<TexImportOptions>(() => getSettings().texImport);

  useEffect(() => {
    if (!gameDir) return;
    G.texdbList(gameDir).then((l) => {
      setDbs(l);
      setDb((cur) => cur ?? l.find((d) => d.name === 'gta3')?.path ?? l[0]?.path ?? null);
    }).catch(() => {});
    G.imgList(gameDir).then((l) => {
      setImgs(l);
      setImg((cur) => cur ?? l.find((d) => d.name.toLowerCase() === 'gta3.img')?.path ?? l[0]?.path ?? null);
    }).catch(() => {});
  }, [gameDir]);

  // Analysis is keyed by the inputs: a stale result is never shown for a changed selection.
  const key = `${mode}|${dffs.join(',')}|${txds.join(',')}`;
  useEffect(() => {
    if (!txds.length || !gameDir || (mode === 'models' && !dffs.length)) return;
    const p = mode === 'models' ? G.adaptAnalyze(dffs, txds).then((models) => ({ key, models })) : G.retexAnalyze(gameDir, txds).then((retex) => ({ key, retex }));
    let alive = true;
    p.then((r) => alive && setInfo(r)).catch((e) => alive && alert(L.common.error, String(e?.message ?? e)));
    return () => {
      alive = false;
    };
  }, [key, mode, dffs, txds, gameDir, L]);
  const cur = info?.key === key ? info : null;

  const addDffs = async () => {
    const f = await pickFiles({ exts: ['.dff'], multi: true });
    if (!f?.length) return;
    // A PC mod usually ships "name.txd" next to "name.dff".
    const guesses: string[] = [];
    for (const d of f) {
      const g = d.replace(/\.dff$/i, '.txd');
      if (await G.exists(g)) guesses.push(g);
    }
    setDffs(uniq([...dffs, ...f]));
    if (guesses.length) setTxds(uniq([...txds, ...guesses]));
  };
  const addTxds = async () => {
    const f = await pickFiles({ exts: ['.txd'], multi: true });
    if (f?.length) setTxds(uniq([...txds, ...f]));
  };

  const setOpt = (p: Partial<TexImportOptions>) => {
    const next = { ...o, ...p };
    setO(next);
    setSettings({ texImport: next });
  };

  const goModels = async () => {
    if (toImg && img && !(await confirmNewEntries(img, dffs.map((d) => baseName(d).toLowerCase())))) return;
    const out = appDir(`adapted/${stripExt(baseName(dffs[0]))}${dffs.length > 1 ? `+${dffs.length - 1}` : ''}_${stamp()}`);
    const r = await run(() => G.adaptRun(dffs, txds, prefix, out, toDb ? db : null, o, toImg ? img : null));
    if (!r) return;
    const lines = r.models.map((m) => `${m.name}: ${Object.entries(m.renamed).map(([a, b]) => `${a}→${b}`).join(', ')}`).join('\n');
    alert(L.adapt.done, `${L.adapt.resultModels(r.models.length, r.textures)}\n\n${lines}\n\n${L.adapt.savedTo(out.replace(G.storageRoot(), ''))}`);
  };

  const goRetex = async () => {
    if (!gameDir) return;
    const r = await run(() => G.retexRun(gameDir, txds, o, addMissing ? db : null));
    if (!r) return;
    alert(L.adapt.done, L.adapt.retexResult(r.replaced.length, r.added.length, r.skipped.length, r.failed.length) +
      (r.skipped.length ? `\n\n${L.adapt.retexSkipped}: ${r.skipped.join(', ')}` : '') + (r.failed.length ? `\n\n${r.failed.join('\n')}` : ''));
  };

  const dbPicker = (
    <Field label="texdb">
      <Segmented value={db ?? ''} onChange={setDb} options={dbs.map((d) => ({ value: d.path, label: d.name }))} />
    </Field>
  );

  const retexMissing = cur?.retex?.filter((x) => !x.dbs.length) ?? [];
  const canRun = mode === 'models' ? !!cur?.models && cur.models.textures > 0 : !!cur?.retex?.length;

  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: insets.bottom + 100 }}>
        <Segmented value={mode} onChange={setMode} options={[{ value: 'models', label: L.adapt.modeModels }, { value: 'retex', label: L.adapt.modeRetex }]} />
        <T dim size={13}>{mode === 'models' ? L.adapt.hint : L.adapt.retexHint}</T>
        <Card>
          {mode === 'models' ? (
            <>
              <Files icon={FileBox} title={L.adapt.dff} files={dffs} addLabel={L.common.add} onAdd={addDffs} onRemove={(f) => setDffs(dffs.filter((x) => x !== f))} />
              <Divider />
            </>
          ) : null}
          <Files icon={Images} title={L.adapt.txd} files={txds} addLabel={L.common.add} onAdd={addTxds} onRemove={(f) => setTxds(txds.filter((x) => x !== f))} />
        </Card>

        {mode === 'models' && cur?.models ? (
          <Card style={{ paddingVertical: 6 }}>
            {cur.models.models.map((m) => (
              <View key={m.name}>
                <Lines title={m.name} items={m.found} color={c.ok} />
                {m.missing.length ? <Lines title={L.adapt.missing} items={m.missing} color={c.warn} /> : null}
              </View>
            ))}
            <Lines title={L.adapt.unused} items={cur.models.unused} max={12} />
          </Card>
        ) : null}

        {mode === 'retex' && cur?.retex ? (
          <Card style={{ paddingVertical: 6 }}>
            <Lines title={L.adapt.retexFound} items={cur.retex.filter((x) => x.dbs.length).map((x) => `${x.name} → ${x.dbs.join(', ')}`)} color={c.ok} />
            <Lines title={L.adapt.retexNone} items={retexMissing.map((x) => x.name)} color={c.warn} />
            {retexMissing.length ? (
              <>
                <Toggle title={L.adapt.addMissing} value={addMissing} onChange={setAddMissing} />
                {addMissing && dbs.length ? dbPicker : null}
              </>
            ) : null}
          </Card>
        ) : null}

        {mode === 'models' ? (
          <Card style={{ paddingVertical: 6 }}>
            <Field label={L.adapt.prefix} hint={L.adapt.prefixHint}>
              <Input value={prefix} mono onChange={(v) => setSettings({ prefix: v.replace(/[^A-Za-z0-9_]/g, '').slice(0, 14) })} />
            </Field>
            <Toggle title={L.adapt.toTexdb} value={toDb} onChange={setToDb} />
            {toDb && dbs.length ? dbPicker : null}
            <Toggle title={L.adapt.toImg} value={toImg} onChange={setToImg} />
            {toImg && imgs.length ? (
              <Field label="IMG">
                <Segmented value={img ?? ''} onChange={setImg} options={imgs.map((d) => ({ value: d.path, label: d.name }))} />
              </Field>
            ) : null}
          </Card>
        ) : null}

        {mode === 'retex' || toDb ? (
          <Card style={{ paddingVertical: 6 }}>
            <ImportOptions o={o} set={setOpt} />
          </Card>
        ) : null}
      </ScrollView>
      <View style={{ position: 'absolute', left: 16, right: 16, bottom: insets.bottom + 16 }}>
        <Button title={mode === 'models' ? L.adapt.run : L.adapt.runRetex} disabled={!canRun} onPress={mode === 'models' ? goModels : goRetex} />
      </View>
    </View>
  );
}
