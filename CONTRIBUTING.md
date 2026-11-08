# Contributing

Thanks for helping! Bug reports, game-file samples that do not work and pull requests are welcome.

## Setup

- JDK 21, Android SDK (API 36, build-tools 36), optionally Android Studio.
- `./gradlew installDebug` for a debug build on a device, `./gradlew assembleRelease` for release
  (Windows: `scripts/gradle.ps1 ...` sets up the toolchain on drive D:).

## Layout

| path | what |
|---|---|
| `app/src/main/java/.../screens/` | screens (Compose) |
| `app/src/main/java/.../ui/` | UI kit, stack navigation, dialogs, file browser |
| `app/src/main/java/.../data/`, `i18n/` | settings store, dictionary |
| `app/src/main/assets/i18n_*.json` | translations (ru, en; same keys) |
| `app/src/main/java/.../core/` | file formats, pure Kotlin/JVM (no Android imports) |
| `app/src/main/java/.../lib/Gta.kt` | operations for the UI: caches, backups, image decoding |
| `scripts/CoreTest.kt` | format self-test |
| `fastlane/metadata/` | store listing (F-Droid) |

## Before a pull request

```bash
./gradlew testDebugUnitTest lintDebug
scripts/test-core.sh [game-dir]   # format self-test; pass a game folder for real-file checks
```

- Never commit game files: they are copyrighted.
- Keep format code in `core` free of Android APIs so it stays testable on the desktop.
- New user-facing strings go to both `app/src/main/assets/i18n_ru.json` and `i18n_en.json`.

By contributing you agree that your code is released under the AGPL-3.0-or-later license.
