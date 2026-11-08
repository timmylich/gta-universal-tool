/** Language, theme, backups, cache, about. */
import Constants from 'expo-constants';
import { Code, Eraser } from 'lucide-react-native';
import { Linking, ScrollView } from 'react-native';

import { G } from '../../modules/gta-native';
import { alert, run } from '../components/Host';
import { Card, Field, Row, Segmented, T, Toggle } from '../components/ui';
import { REPO_URL } from '../config';
import { useT } from '../i18n';
import { setSettings, useSettings } from '../lib/store';

export default function SettingsScreen() {
  const L = useT();
  const s = useSettings((x) => x);
  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
      <Card style={{ paddingVertical: 6 }}>
        <Field label={L.settings.language}>
          <Segmented value={s.language} onChange={(language) => setSettings({ language })}
            options={[{ value: 'system', label: L.settings.system }, { value: 'ru', label: 'Русский' }, { value: 'en', label: 'English' }]} />
        </Field>
        <Field label={L.settings.theme}>
          <Segmented value={s.theme} onChange={(theme) => setSettings({ theme })}
            options={[{ value: 'system', label: L.settings.system }, { value: 'light', label: L.settings.light }, { value: 'dark', label: L.settings.dark }]} />
        </Field>
      </Card>
      <Card style={{ paddingVertical: 6 }}>
        <Toggle title={L.settings.backup} hint={L.settings.backupHint} value={s.backup} onChange={(backup) => setSettings({ backup })} />
        <Toggle title={L.img.newWarnTitle} value={!s.imgWarned} onChange={(v) => setSettings({ imgWarned: !v })} />
      </Card>
      <Card>
        <Row icon={Eraser} title={L.settings.clearCache} onPress={async () => {
          await run(() => G.clearCache());
          alert(L.common.done, L.settings.cleared);
        }} />
      </Card>
      <Card style={{ padding: 16, gap: 6 }}>
        <T semi>{L.settings.about}</T>
        <T dim size={13.5}>{L.settings.aboutText}</T>
        <T dim size={13}>{`${L.settings.version} ${Constants.expoConfig?.version ?? ''} · AGPL-3.0-or-later`}</T>
      </Card>
      <Card>
        <Row icon={Code} title={L.settings.source} sub={REPO_URL.replace('https://', '')} onPress={() => Linking.openURL(REPO_URL).catch(() => {})} />
      </Card>
    </ScrollView>
  );
}
