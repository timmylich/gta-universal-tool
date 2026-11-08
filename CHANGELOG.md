# Changelog

## 0.2.0

- The app is now native (Kotlin + Jetpack Compose) instead of React Native: about 1.3 MB instead of ~38 MB,
  faster start and lists. Features and file handling are unchanged; settings carry over.
- Note: 0.1.0 builds signed with the debug key must be uninstalled once before installing 0.2.0.

## 0.1.0 — first release

- Texture databases (texdb): browse with previews, import PNG/JPG (DXT1, DXT5, RGBA8888, RGB565, RGBA4444,
  mipmaps, RLE), edit attributes, export, delete, rebuild `.dat`.
- IMG archives: add/replace (case-insensitive), replace an entry with any file, export, delete, rebuild.
- Audio packs (`.osw`): listen, export, replace and append mp3.
- Mod adaptation: several `.dff` + `.txd` at once with unique texture names; retexture mode.
- Backups of every game file before its first change.
