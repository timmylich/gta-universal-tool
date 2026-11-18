# Publishing in app stores

Everything a store asks for is already in the repository:

| What | Where |
| --- | --- |
| APK (signed) | GitHub → Releases (built by `.github/workflows/release.yml`) |
| Icon 512×512 | `fastlane/metadata/android/*/images/icon.png` |
| Feature graphic 1024×500 | `fastlane/metadata/android/*/images/featureGraphic.png` |
| Title / short / full description | `fastlane/metadata/android/{ru-RU,en-US,es-ES,id}/` |
| What's new | `fastlane/metadata/android/*/changelogs/<versionCode>.txt` |
| Privacy policy URL | `https://github.com/timmylich/gta-universal-tool/blob/main/docs/PRIVACY.md` |
| Screenshots | `fastlane/metadata/android/*/images/phoneScreenshots/` (2 now, from 0.1.0; stores want 4–8) |
| Posts for Telegram / VK, GitHub description and topics | [promo.md](promo.md) |

Typical form answers: category **Tools** (or "Development"); contains ads **no**; in-app purchases
**no**; data collected **none**, internet is used only to check GitHub for a new version (sideloaded installs only, nothing is sent); target audience **12+/16+** (the
game itself is rated 16–18+, some stores ask for the same); content rating: no violence in the
app itself, no user-generated sharing.

"All files access" (`MANAGE_EXTERNAL_STORAGE`) has to be explained in most consoles: *the app
edits game files of GTA San Andreas in shared storage (texture caches, IMG archives, audio packs)
and keeps backups in its own folder there; the files are chosen by the user*.

Trademark: the description already says the project is not affiliated with Rockstar Games or
Take-Two. If a store rejects "GTA" in the title, use "Universal Tool for SA mods" there and keep
the package name.

Always upload the **same signed APK** from GitHub Releases, so the stores can update each other's
installs.

## Store by store

| Store | Account | Upload | Notes |
| --- | --- | --- | --- |
| F-Droid | none | merge request to fdroiddata with `fdroid/io.github.timmylich.gtatool.yml` | builds from source and signs with its own key |
| RuStore | rustore.ru/developer | APK | review 1–3 days; no SDK needed |
| Huawei AppGallery | developer.huawei.com, ID verification | APK | no HMS SDK needed |
| Samsung Galaxy Store | seller.samsungapps.com | APK | needs privacy policy URL |
| Xiaomi GetApps | dev.mi.com/xiaomihyperos | APK | may require ID verification; popular with SA-MP mobile players |
| NashStore | developer.nashstore.ru | APK | |
| Aptoide | connect.aptoide.com | APK | |
| Uptodown | uptodown.com developer console | APK | |
| APKPure | apkpure.com developer console | APK | |
| Google Play | play.google.com/console | AAB | `MANAGE_EXTERNAL_STORAGE` is allowed only for file managers and similar apps; expect a rejection, and the gradle build makes only an APK now |
