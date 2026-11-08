/** JS bindings for the local `GtaNative` Android module (all file formats live there, see android/.../core). */
import { requireNativeModule } from 'expo';

export interface DirItem { name: string; dir: boolean; size: number; mtime: number }
export interface TexDbInfo { name: string; path: string; size: number }
export interface TexEntry { name: string; w: number; h: number; fmt: string; mips: boolean; line: string; key: number }
export interface TexDbData { header: string; wasted: number; datSize: number; entries: TexEntry[] }
export interface ImportResult { added: string[]; replaced: string[]; skipped?: string[]; failed: string[] }
export interface ImgInfo { name: string; path: string; size: number }
export interface ImgEntry { name: string; size: number }
export interface ImgData { fileSize: number; used: number; entries: ImgEntry[] }
export interface OswInfo { name: string; path: string; dir: string; size: number }
export interface Track { name: string; size: number }
export interface AdaptInfo { models: { name: string; found: string[]; missing: string[] }[]; unused: string[]; textures: number }
export interface AdaptResult { models: { name: string; renamed: Record<string, string>; missing: string[] }[]; textures: number; unused: string[]; imgAdded: string[] }
export interface RetexItem { name: string; txd: string; w: number; h: number; dbs: string[] }
export interface RetexResult { replaced: string[]; added: string[]; skipped: string[]; failed: string[] }

export type TexFormat = 'auto' | 'DXT1' | 'DXT5' | 'RGBA8888' | 'RGB565' | 'RGBA4444';
export interface TexImportOptions {
  format: TexFormat;
  mips: boolean;
  /** RLE-pack the data like the original databases (smaller .dat). */
  rle: boolean;
  /** Longest side limit, 0 = keep. */
  maxSize: number;
  /** Resize to power-of-two sides. */
  pot: boolean;
  alphamode: 'auto' | '0' | '1' | '2';
  camnorm: boolean;
  streammode: boolean;
  /** Extra "key=value" attributes, space separated. */
  extra: string;
  overwrite: boolean;
}

interface Native {
  hasStorageAccess(): boolean;
  openStorageSettings(): void;
  storageRoot(): string;
  setBackupDir(path: string | null): void;
  listDir(path: string): Promise<DirItem[]>;
  exists(path: string): Promise<boolean>;
  findGameDirs(): Promise<string[]>;
  copyFile(from: string, to: string): Promise<void>;

  texdbList(gameDir: string): Promise<TexDbInfo[]>;
  texdbOpen(path: string, reload: boolean): Promise<TexDbData>;
  texdbPreview(path: string, name: string, size: number): Promise<string | null>;
  texdbExport(path: string, names: string[], destDir: string): Promise<number>;
  texdbImport(path: string, items: { file: string; name: string }[], opts: TexImportOptions): Promise<ImportResult>;
  texdbSetAttrs(path: string, name: string, changes: Record<string, string>): Promise<string>;
  texdbRemove(path: string, names: string[]): Promise<void>;
  texdbCompact(path: string): Promise<number>;

  imgList(gameDir: string): Promise<ImgInfo[]>;
  imgOpen(path: string, reload: boolean): Promise<ImgData>;
  imgMissing(path: string, names: string[]): Promise<string[]>;
  imgImport(path: string, files: string[]): Promise<ImportResult>;
  imgReplace(path: string, name: string, file: string): Promise<boolean>;
  imgExport(path: string, names: string[], destDir: string): Promise<number>;
  imgRemove(path: string, names: string[]): Promise<void>;
  imgRebuild(path: string): Promise<number>;

  oswList(gameDir: string): Promise<OswInfo[]>;
  oswTracks(path: string): Promise<Track[]>;
  oswExtract(path: string, index: number, dest: string): Promise<string>;
  oswPreview(path: string, index: number): Promise<string>;
  oswApply(path: string, replace: Record<string, string>, add: string[]): Promise<number>;

  adaptAnalyze(dffs: string[], txds: string[]): Promise<AdaptInfo>;
  adaptRun(dffs: string[], txds: string[], prefix: string, outDir: string, texdb: string | null, opts: TexImportOptions, img: string | null): Promise<AdaptResult>;
  retexAnalyze(gameDir: string, txds: string[]): Promise<RetexItem[]>;
  retexRun(gameDir: string, txds: string[], opts: TexImportOptions, fallback: string | null): Promise<RetexResult>;
  clearCache(): Promise<void>;
}

export const G = requireNativeModule<Native>('GtaNative');
