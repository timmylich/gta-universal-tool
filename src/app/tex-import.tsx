/** Import PNG/JPG into a texture database with format/mip/alpha options (remembered between imports). */
import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { G, type TexImportOptions } from '../../modules/gta-native';
import { alert, run } from '../components/Host';
import { ImportOptions } from '../components/ImportOptions';
import { Button, Card, Field, T, Toggle } from '../components/ui';
import { useT } from '../i18n';
import { baseName, pending } from '../lib/paths';
import { getSettings, setSettings } from '../lib/store';

export default function TexImport() {
  const L = useT();
  const job = pending.texImport;
  const insets = useSafeAreaInsets();
  const [o, setO] = useState<TexImportOptions>(() => getSettings().texImport);
  const set = (p: Partial<TexImportOptions>) => {
    const next = { ...o, ...p };
    setO(next);
    setSettings({ texImport: next });
  };
  if (!job) return null;
  const go = async () => {
    const r = await run(() => G.texdbImport(job.path, job.items, o));
    if (!r) return;
    pending.texImport = null;
    alert(L.common.done, L.tex.result(r.added.length, r.replaced.length, r.skipped?.length ?? 0, r.failed.length) + (r.failed.length ? `\n\n${r.failed.join('\n')}` : ''));
    router.back();
  };
  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 100 }}>
        <Card style={{ paddingVertical: 6 }}>
          <Field label={`${L.tex.names} → ${baseName(job.path)}`} hint={L.tex.nameHint}>
            {job.items.slice(0, 50).map((i) => <T key={i.file} mono size={12.5} numberOfLines={1}>{i.name}</T>)}
            {job.items.length > 50 ? <T dim>… +{job.items.length - 50}</T> : null}
          </Field>
          <Toggle title={L.tex.overwrite} hint={L.tex.overwriteHint} value={o.overwrite} onChange={(overwrite) => set({ overwrite })} />
        </Card>
        <Card style={{ paddingVertical: 6 }}>
          <ImportOptions o={o} set={set} />
        </Card>
      </ScrollView>
      <View style={{ position: 'absolute', left: 16, right: 16, bottom: insets.bottom + 16 }}>
        <Button title={L.tex.run(job.items.length)} onPress={go} />
      </View>
    </View>
  );
}
