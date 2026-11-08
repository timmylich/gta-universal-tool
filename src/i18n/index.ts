/** Typed dictionaries: `const L = useT();` then `L.section.key`. */
import { getLocales } from 'expo-localization';

import { getSettings, useSettings } from '../lib/store';
import { en } from './en';
import { ru, type Dict } from './ru';

const RU_LIKE = ['ru', 'uk', 'be', 'kk', 'ky', 'uz', 'tg', 'hy', 'az', 'ka'];

function resolve(setting: 'system' | 'ru' | 'en'): Dict {
  if (setting === 'ru') return ru;
  if (setting === 'en') return en;
  return RU_LIKE.includes(getLocales()[0]?.languageCode ?? 'en') ? ru : en;
}

export function useT(): Dict {
  return resolve(useSettings((s) => s.language));
}

export function currentDict(): Dict {
  return resolve(getSettings().language);
}

/** 1536 → "1.5 KB" */
export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const u = ['KB', 'MB', 'GB'];
  let i = -1;
  do {
    n /= 1024;
    i++;
  } while (n >= 1024 && i < u.length - 1);
  return `${n.toFixed(n < 10 ? 1 : 0)} ${u[i]}`;
}
