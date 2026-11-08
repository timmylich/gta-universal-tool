import { G } from '../../modules/gta-native';

/** App folder in shared storage: exports, adapted mods, backups. */
export const appDir = (sub: string) => `${G.storageRoot()}/GTAUniversalTool/${sub}`;

export const baseName = (p: string) => p.split('/').pop() ?? p;

export const stripExt = (n: string) => n.replace(/\.[^.]+$/, '');

/** Hand-off of picked files from a list screen to the import screen. */
export const pending: { texImport: { path: string; items: { file: string; name: string }[] } | null } = { texImport: null };
