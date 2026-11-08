# Contributing

Thanks for helping! Bug reports, game-file samples that do not work and pull requests are welcome.

## Setup

- Node.js 22+, JDK 21, Android SDK (API 36, build-tools 36), optionally Android Studio.
- `npm ci`, then `npm run android` for a debug build on a device, or `npm run build:apk` (Windows) for release.

## Layout

| path | what |
|---|---|
| `src/app/` | screens (expo-router) |
| `src/components/`, `src/lib/`, `src/i18n/` | UI kit, settings store, translations (ru, en) |
| `modules/gta-native/android/.../core/` | file formats, pure Kotlin/JVM (no Android imports) |
| `modules/gta-native/android/.../GtaNativeModule.kt` | bridge to JS, backups, image decoding |
| `scripts/CoreTest.kt` | format self-test |
| `fastlane/metadata/` | store listing (F-Droid) |

## Before a pull request

```bash
npm run check       # typecheck + lint
npm run test:core   # format self-test; pass a game folder for real-file checks
```

- Never commit game files: they are copyrighted.
- Keep format code in `core` free of Android APIs so it stays testable on the desktop.
- New user-facing strings go to both `src/i18n/ru.ts` and `src/i18n/en.ts`.

By contributing you agree that your code is released under the AGPL-3.0-or-later license.
