# Mobile GTA SA file formats

Reverse-engineered from the Android version (64-bit, SA-MP clients) and verified on real game files
(`scripts/test-core.sh`). All integers are little-endian.

## Texture database — `texdb/<db>/`

| file | layout |
|---|---|
| `<db>.txt` | line 1: db header (`cat=0 name=Default onfoot=5 slow=5 fast=5 defaultformat=2 defaultstream=0`); then one line per texture: `"name" width=W height=H img=XXXXXXXX png=XXXXXXXX [alphamode=1\|2] [camnorm=1] [streammode=1] [detailtile=N] [hassibling=1] …`. Lines like `"A" "affiliate=B"` are aliases with no data of their own. |
| `<db>.dxt.toc` | u32 size of `.dat`, then one u32 offset into `.dat` per txt line (`0xFFFFFFFF` = no data). |
| `<db>.dxt.dat` | per texture: u32 flags (high 16 bits = pixel format, low 16 = id shared with the thumbnail), u16 width, u16 height (bit 15 = full mip chain follows), u32 byte count counted from offset 12, u32 RLE marker, pixel data. |
| `<db>.dxt.tmb` | 8×8 thumbnails, same header, no mips, one per texture with data, in txt order. |

Pixel formats (high 16 bits of flags):

| code | format | RLE unit |
|---|---|---|
| `0x83F0` | DXT1 | 8-byte block |
| `0x83F3` | DXT5 | 16-byte block |
| `0x1401` | RGBA8888 (bytes R, G, B, A) | 4 bytes |
| `0x8363` | RGB565 | 4 bytes |
| `0x8033` | RGBA4444 (R in the high nibble, A in the low one) | 4 bytes |

RLE: when the marker is non-zero, a unit whose first byte equals the marker is `marker, count, unit` and expands
to `count` copies of `unit`. The marker is a byte value no unit starts with. Data of 16-bit formats is padded to
4 bytes.

`img=` / `png=` are checksums used by Rockstar's build tools; the game does not appear to check them. The tool
writes CRC32 values.

### Aliases and per-TXD copies

A line `"A" "affiliate=B"` has no data: the game uses texture `B` for `A` (chains are possible).
The PC → mobile conversion merged all TXDs into one database. A texture name used by several TXDs with
different pictures is stored once per TXD as `<name>_<txd>` (e.g. `des_dirt1_cs_mountain`), and TXDs with the
same picture are aliases to one stored copy. The game looks up `<name>_<txd>` first, then `<name>`. A retexture
must therefore write to `<name>_<txd>` when it exists (see `core/Retex.kt`); writing only `<name>` leaves those
TXDs unchanged. `hassibling=1` does not affect this lookup.

## IMG archive (VER2)

`"VER2"`, u32 count, then 32-byte entries: u32 offset (2048-byte sectors), u16 size (sectors), u16 0,
`char[24]` name. Names are matched case-insensitively. Replaced files that do not fit in place are appended at
the end, which leaves gaps; *rebuild* rewrites the files back to back.

## Audio packs — `audio/<dir>/<name>.osw` + `.osw.idx`

`.osw` is a ZIP with stored (uncompressed) mp3 files (SFX packs also contain directory entries).
`.osw.idx` is what the game reads: u32 count, then per file u32 data offset in `.osw`, u32 size,
u16 name length, name (directory entries are not listed). Tracks are played by position.

## RenderWare (DFF / PC TXD)

Chunks: u32 type, u32 size, u32 version, data. Texture names live in String chunks under Texture (`0x06`), in
MatFX (`0x120`) environment/bump/dual textures and in the Specular Material plugin (`0x253F2F6`, `char[24]`).
PC TXD texture natives (D3D8/D3D9) are decoded: DXT1/3/5, 8888, 888, 565, 1555, 4444, PAL8/PAL4.
