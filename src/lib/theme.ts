/** Colors: system / light / dark. One background for app bar, content and system bars. */
import { useMemo } from 'react';
import { useColorScheme } from 'react-native';

import { useSettings } from './store';

export interface Theme {
  dark: boolean;
  bg: string;
  card: string;
  cardAlt: string;
  text: string;
  textDim: string;
  border: string;
  accent: string;
  onAccent: string;
  accentSoft: string;
  danger: string;
  warn: string;
  ok: string;
}

export const ACCENT = '#FF8A1F';

export function alpha(hex: string, a: number): string {
  return hex.slice(0, 7) + Math.round(a * 255).toString(16).padStart(2, '0');
}

export function useTheme(): Theme {
  const system = useColorScheme();
  const pref = useSettings((s) => s.theme);
  const dark = pref === 'system' ? system !== 'light' : pref === 'dark';
  return useMemo(
    () =>
      dark
        ? { dark, bg: '#111214', card: '#1C1D21', cardAlt: '#26272C', text: '#F2F2F3', textDim: '#8F9097', border: '#2A2B30', accent: ACCENT, onAccent: '#1A1206', accentSoft: alpha(ACCENT, 0.18), danger: '#FF5A4E', warn: '#FFC247', ok: '#3DD68C' }
        : { dark, bg: '#F6F6F8', card: '#FFFFFF', cardAlt: '#ECECF0', text: '#141416', textDim: '#6B6B73', border: '#E2E2E6', accent: '#E8730C', onAccent: '#FFFFFF', accentSoft: alpha('#E8730C', 0.13), danger: '#E3261B', warn: '#C98A0E', ok: '#0E9F5A' },
    [dark],
  );
}
