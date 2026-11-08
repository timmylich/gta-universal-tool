import { G } from '../../modules/gta-native';
import { alert } from '../components/Host';
import { currentDict } from '../i18n';
import { getSettings, setSettings } from './store';

/** Asks before adding entries that are not in the archive yet. Returns false when the user cancels. */
export async function confirmNewEntries(path: string, names: string[]): Promise<boolean> {
  const L = currentDict();
  const missing = await G.imgMissing(path, names);
  if (!missing.length || getSettings().imgWarned) return true;
  const shown = missing.slice(0, 15).join('\n') + (missing.length > 15 ? `\n… +${missing.length - 15}` : '');
  return new Promise((res) => alert(L.img.newWarnTitle, L.img.newWarn(shown), [
    { text: L.common.cancel, style: 'cancel', onPress: () => res(false) },
    { text: L.img.dontWarn, style: 'destructive', onPress: () => { setSettings({ imgWarned: true }); res(true); } },
    { text: L.img.addAnyway, style: 'destructive', onPress: () => res(true) },
  ]));
}
