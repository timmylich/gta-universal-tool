# Promo texts

Ready to paste. Every post fits the 1024-character Telegram caption limit (checked by length,
emoji count as 1–2 characters). Links: GitHub Releases for the APK, or the store page once it is
live.

## Telegram / VK posts

### 1. Winter retexture (story)

```text
Каждый год одно и то же: хочется снег на сервере, а зимний ретекстур с ПК на телефон нормально не ставится. Часть текстур меняется, часть остаётся летней — потому что в мобильной GTA одна и та же текстура из разных TXD лежит в кэше под разными именами.

Есть приложение, которое это учитывает — GTA Universal Tool. Кидаешь ему папку с .txd из мода — оно само раскладывает каждую текстуру туда, где её ищет игра, одинаковые картинки не дублирует, неизменённые пропускает. Перед изменениями делает резервную копию.

Заодно это полноценная замена TXD Tool и IMG Tool: превью кэшей gta3/gta_int/samp, замена текстур из PNG, архивы IMG, звук .osw, перенос машин и скинов с ПК.

Весит 1,4 МБ, без рекламы и аналитики, ничего не собирает. Бесплатно, код открыт.

👉 github.com/timmylich/gta-universal-tool
```

### 2. Short: "from the phone"

```text
Поменять текстуру в мобильной SA-MP без компьютера — теперь реально.

GTA Universal Tool открывает кэши текстур прямо на телефоне: видишь превью, ищешь по имени, заменяешь на свою картинку из галереи — и заходишь в игру. Размер можно ограничить «как у заменяемой», чтобы FPS не просел.

Там же IMG-архивы, звук и перенос модов с ПК. Перед каждым изменением — резервная копия, так что сломать игру сложно.

Бесплатно, без рекламы, 1,4 МБ.
👉 github.com/timmylich/gta-universal-tool
```

### 3. For server communities (SA-MP / RP)

```text
Для тех, кто делает сборки и моды под мобильный SA-MP.

Раньше для ретекстура или новой машины нужны были TXD Tool, IMG Tool и компьютер для конвертации. Теперь всё это умеет одно приложение — GTA Universal Tool:

• кэши текстур с превью, импорт PNG/JPG, DXT1/DXT5/RGBA, мип-карты
• ретекстур целой карты с учётом алиасов и копий текстур по TXD
• перенос dff + txd с ПК: уникальные имена текстур, лишнее отбрасывается
• IMG: добавить, заменить, выгрузить, пересобрать
• паки звука .osw

Работает офлайн, делает резервные копии, открытый код. Создано при поддержке San Fierro RolePlay.

👉 github.com/timmylich/gta-universal-tool
```

### 4. English (international SA-MP groups)

```text
Ever tried putting a winter retexture on mobile GTA SA and half the map stayed green? The mobile texture cache keeps separate copies of the same texture for different TXDs, so a plain name-by-name replace misses them.

GTA Universal Tool handles that: give it the mod's folder of .txd files and it writes each texture where the game actually looks for it, stores identical pictures once and skips the ones the mod didn't change.

It also replaces TXD Tool and IMG Tool on the phone: cache previews, PNG import, IMG archives, .osw audio, PC car and skin ports. Backups before every first change.

1.4 MB, no ads, no analytics, collects nothing. Free and open source.

👉 github.com/timmylich/gta-universal-tool
```

## GitHub

**About → Description** (max 350 characters):

```text
TXD Tool + IMG Tool for mobile GTA San Andreas and SA-MP on Android: texture caches (texdb), IMG archives, .osw audio, PC mod adaptation and whole-map retextures. Native, offline, no ads.
```

**Topics** (GitHub allows up to 20):

```text
gta gta-sa gta-san-andreas samp sa-mp gta-modding modding android kotlin jetpack-compose txd txd-tool img-tool texdb renderware dff dxt texture-editor retexture offline
```

With GitHub CLI:

```powershell
gh repo edit timmylich/gta-universal-tool --description "TXD Tool + IMG Tool for mobile GTA San Andreas and SA-MP on Android: texture caches (texdb), IMG archives, .osw audio, PC mod adaptation and whole-map retextures. Native, offline, no ads." --homepage "https://github.com/timmylich/gta-universal-tool/releases/latest"
gh repo edit timmylich/gta-universal-tool --add-topic gta,gta-sa,gta-san-andreas,samp,sa-mp,gta-modding,modding,android,kotlin,jetpack-compose,txd,txd-tool,img-tool,texdb,renderware,dff,dxt,texture-editor,retexture,offline
```
