/** App settings in one small JSON file; `useSettings(selector)` re-renders only on the selected value. */
import { File, Paths } from 'expo-file-system';
import { useSyncExternalStore } from 'react';

import type { TexImportOptions } from '../../modules/gta-native';

export interface Settings {
  gameDir: string | null;
  language: 'system' | 'ru' | 'en';
  theme: 'system' | 'light' | 'dark';
  /** Copy each game file to /sdcard/GTAUniversalTool/backup before its first change. */
  backup: boolean;
  prefix: string;
  texImport: TexImportOptions;
  lastDir: string | null;
  installedAt: number;
  donationShown: number[];
  /** Hide the "new entries may crash the game" warning for IMG additions. */
  imgWarned: boolean;
}

export const DEFAULT_IMPORT: TexImportOptions = {
  format: 'auto', mips: true, rle: true, maxSize: 0, pot: true, alphamode: 'auto', camnorm: false, streammode: false, extra: '', overwrite: true,
};

const DEFAULTS: Settings = {
  gameDir: null, language: 'system', theme: 'system', backup: true, prefix: 'mod', texImport: DEFAULT_IMPORT,
  lastDir: null, installedAt: Date.now(), donationShown: [], imgWarned: false,
};

const file = new File(Paths.document, 'settings.json');
let state: Settings = load();
const subs = new Set<() => void>();

function load(): Settings {
  try {
    if (file.exists) {
      const s = JSON.parse(file.textSync()) as Partial<Settings>;
      return { ...DEFAULTS, ...s, texImport: { ...DEFAULT_IMPORT, ...s.texImport } };
    }
  } catch {
    // Corrupt settings: start fresh rather than crash.
  }
  return { ...DEFAULTS };
}

export function getSettings() {
  return state;
}

export function setSettings(patch: Partial<Settings>) {
  state = { ...state, ...patch };
  try {
    file.write(JSON.stringify(state));
  } catch (e) {
    console.warn('settings save failed', e);
  }
  subs.forEach((f) => f());
}

export function useSettings<T>(sel: (s: Settings) => T): T {
  return useSyncExternalStore(
    (cb) => {
      subs.add(cb);
      return () => subs.delete(cb);
    },
    () => sel(state),
  );
}
