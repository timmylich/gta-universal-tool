/** Texture import options form (format, size, mips, alpha attributes). */
import type { TexFormat, TexImportOptions } from '../../modules/gta-native';
import { useT } from '../i18n';
import { Divider, Field, Input, Segmented, Toggle } from './ui';

export function ImportOptions({ o, set }: { o: TexImportOptions; set: (p: Partial<TexImportOptions>) => void }) {
  const L = useT();
  const formats: TexFormat[] = ['auto', 'DXT1', 'DXT5', 'RGBA8888', 'RGB565', 'RGBA4444'];
  return (
    <>
      <Field label={L.tex.format} hint={L.tex.formatHint}>
        <Segmented value={o.format} onChange={(format) => set({ format })} options={formats.map((f) => ({ value: f, label: f === 'auto' ? L.tex.auto : f }))} />
      </Field>
      <Field label={L.tex.maxSize}>
        <Segmented value={o.maxSize} onChange={(maxSize) => set({ maxSize })} options={[0, 2048, 1024, 512, 256, 128].map((v) => ({ value: v, label: v ? String(v) : L.tex.original }))} />
      </Field>
      <Toggle title={L.tex.pot} hint={L.tex.potHint} value={o.pot} onChange={(pot) => set({ pot })} />
      <Toggle title={L.tex.mips} hint={L.tex.mipsHint} value={o.mips} onChange={(mips) => set({ mips })} />
      <Toggle title={L.tex.rle} hint={L.tex.rleHint} value={o.rle} onChange={(rle) => set({ rle })} />
      <Divider />
      <Field label={L.tex.alphamode} hint={L.tex.alphaHint}>
        <Segmented value={o.alphamode} onChange={(alphamode) => set({ alphamode })} options={(['auto', '0', '1', '2'] as const).map((v) => ({ value: v, label: v === 'auto' ? L.tex.auto : v }))} />
      </Field>
      <Toggle title={L.tex.camnorm} hint={L.tex.camnormHint} value={o.camnorm} onChange={(camnorm) => set({ camnorm })} />
      <Toggle title={L.tex.streammode} hint={L.tex.streamHint} value={o.streammode} onChange={(streammode) => set({ streammode })} />
      <Field label={L.tex.extra} hint={L.tex.extraHint}>
        <Input value={o.extra} onChange={(extra) => set({ extra })} mono placeholder="detailtile=60" />
      </Field>
    </>
  );
}
