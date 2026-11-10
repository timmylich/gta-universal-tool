# Changelog

## 0.2.0

- The app is now native (Kotlin + Jetpack Compose) instead of React Native: about 1.3 MB instead of ~38 MB,
  faster start and lists. Settings carry over.
- Retexture rewritten for whole-map packs: textures go where the game looks for them per TXD
  ("name_txd" copies and aliases in the cache), identical pictures are stored once, unchanged textures are
  skipped, a whole folder of .txd files (subfolders included) can be added at once.
- Import: "Max size → As replaced" never makes a texture bigger than the one it replaces.
- Aliases show the preview and the name of the texture they point to.
- File browser: select all files in a folder.
- In-app guide; Spanish and Indonesian translations; README in Russian, English, Spanish and Indonesian.
- Fixed: the file browser opened under the texture details sheet.
- Note: 0.1.0 builds signed with the debug key must be uninstalled once before installing 0.2.0.

## 0.1.0 — first release

- Texture databases (texdb): browse with previews, import PNG/JPG (DXT1, DXT5, RGBA8888, RGB565, RGBA4444,
  mipmaps, RLE), edit attributes, export, delete, rebuild `.dat`.
- IMG archives: add/replace (case-insensitive), replace an entry with any file, export, delete, rebuild.
- Audio packs (`.osw`): listen, export, replace and append mp3.
- Mod adaptation: several `.dff` + `.txd` at once with unique texture names; retexture mode.
- Backups of every game file before its first change.
