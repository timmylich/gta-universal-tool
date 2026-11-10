/**
 * App-level operations over the format code in `core`. Blocking: call from a background thread
 * (screens use `run { }` / Dispatchers.IO). Opened databases and archives are cached by path, so lists
 * and previews do not re-read big files. Every write first snapshots the original file into the
 * backup folder (once per file), if backups are on.
 */
package io.github.timmylich.gtatool.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import io.github.timmylich.gtatool.core.Adapter
import io.github.timmylich.gtatool.core.ImgArchive
import io.github.timmylich.gtatool.core.OswArchive
import io.github.timmylich.gtatool.core.Pixels
import io.github.timmylich.gtatool.core.Retex
import io.github.timmylich.gtatool.core.Rw
import io.github.timmylich.gtatool.core.TexDb
import io.github.timmylich.gtatool.data.ImportOptions
import java.io.File
import java.io.IOException
import java.util.zip.CRC32

data class DirItem(val name: String, val dir: Boolean, val size: Long)
data class FileInfo(val name: String, val path: String, val size: Long, val dir: String = "")
data class TexEntry(val name: String, val w: Int, val h: Int, val fmt: String, val mips: Boolean, val line: String, val key: Long)
data class TexDbData(val header: String, val wasted: Long, val datSize: Long, val entries: List<TexEntry>)
data class ImportResult(val added: List<String>, val replaced: List<String>, val skipped: List<String>, val failed: List<String>)
data class ImgEntry(val name: String, val size: Long)
data class ImgData(val fileSize: Long, val used: Long, val entries: List<ImgEntry>)
data class Track(val name: String, val size: Long)
data class ModelInfo(val name: String, val found: List<String>, val missing: List<String>)
data class AdaptInfo(val models: List<ModelInfo>, val unused: List<String>, val textures: Int)
data class ModelResult(val name: String, val renamed: Map<String, String>, val missing: List<String>)
data class AdaptResult(val models: List<ModelResult>, val textures: Int, val unused: List<String>, val imgAdded: List<String>)
/** Retexture analysis: [writes] stored copies to replace, [split] TXD-specific copies to create, [missing] "name (txd)". */
data class RetexInfo(val writes: Int, val split: Int, val aliases: Int, val missing: List<String>)
data class RetexResult(val written: Int, val unchanged: Int, val aliases: Int, val missing: List<String>, val failed: List<String>)

/** Thrown when a volume cannot take [need] bytes plus a margin (shown localized by the UI). */
class NoSpace(val need: Long) : IOException("NO_SPACE:$need")

object Gta {
  private val texdbs = HashMap<String, TexDb>()
  private val imgs = HashMap<String, ImgArchive>()
  private val lock = Any()
  lateinit var cacheDir: File

  val storageRoot: String get() = Environment.getExternalStorageDirectory().absolutePath

  /** App folder in shared storage: exports, adapted mods, backups. */
  fun appDir(sub: String) = "$storageRoot/GTAUniversalTool/$sub"

  /** null = backups off. */
  @Volatile var backupRoot: File? = File(appDir("backup"))

  private val cache get() = File(cacheDir, "gut").apply { mkdirs() }

  private fun db(path: String): TexDb = synchronized(lock) { texdbs.getOrPut(path) { val f = File(path); TexDb(f, f.name) } }

  private fun img(path: String): ImgArchive = synchronized(lock) { imgs.getOrPut(path) { ImgArchive(File(path)) } }

  /** Copies [f] to the backup folder once (keeps the very first original). */
  private fun backup(f: File) {
    val root = backupRoot ?: return
    if (!f.exists()) return
    val rel = f.absolutePath.removePrefix(storageRoot).trimStart('/')
    val dest = File(root, rel)
    if (dest.exists()) return
    dest.parentFile?.mkdirs()
    ensureSpace(dest.parentFile!!, f.length())
    val tmp = File(dest.path + ".part")
    f.copyTo(tmp, true)
    tmp.renameTo(dest)
  }

  private fun backupTexdb(d: TexDb) { backup(d.txtFile); backup(d.tocFile); backup(d.datFile); backup(d.tmbFile) }

  /** Decodes an image file; huge pictures are subsampled while decoding so they never hold more than ~2× [limit]². */
  private fun loadImage(path: String, limit: Int = 4096): TexDb.Image {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
    val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = false; inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(path, o) ?: throw IllegalArgumentException("cannot read image: $path")
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val r = TexDb.Image(bmp.width, bmp.height, px)
    bmp.recycle()
    return r
  }

  private fun savePng(img: TexDb.Image, dest: File) {
    dest.parentFile?.mkdirs()
    val bmp = Bitmap.createBitmap(img.px, img.w, img.h, Bitmap.Config.ARGB_8888)
    val tmp = File(dest.path + ".part")
    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    TexDb.replaceFile(tmp, dest)
  }

  /** Nearest power of two (rounds up when closer). */
  private fun pot(v: Int): Int {
    var p = 1
    while (p * 2 <= v) p *= 2
    return if (v - p > p * 2 - v) p * 2 else p
  }

  /** Applies size options: max side, power-of-two, multiple of 4 for DXT. */
  private fun fitSize(img: TexDb.Image, maxSize: Int, forcePot: Boolean, dxt: Boolean): TexDb.Image {
    var w = img.w; var h = img.h
    if (maxSize > 0 && maxOf(w, h) > maxSize) {
      val k = maxSize.toDouble() / maxOf(w, h)
      w = maxOf(1, (w * k).toInt()); h = maxOf(1, (h * k).toInt())
    }
    if (forcePot) { w = minOf(4096, pot(w)); h = minOf(4096, pot(h)) }
    else if (dxt) { w = (w + 3) / 4 * 4; h = (h + 3) / 4 * 4 }
    return if (w == img.w && h == img.h) img else TexDb.Image(w, h, Pixels.resize(img.px, img.w, img.h, w, h))
  }

  /** Returns true when the texture was added, false when replaced. */
  /**
   * [like]: entry a new texture inherits attributes from. maxSize < 0 limits the texture to the size of the one it
   * replaces (or of [like]), so a retexture never makes the game heavier.
   */
  private fun importOne(d: TexDb, img0: TexDb.Image, name: String, o: ImportOptions, srcCrc: Int, like: TexDb.Entry? = null): Boolean {
    val alpha = Pixels.hasAlpha(img0.px)
    val fmt = if (o.format == "auto") (if (alpha) TexDb.Format.DXT5 else TexDb.Format.DXT1) else TexDb.Format.entries.first { it.label == o.format }
    val dxt = fmt == TexDb.Format.DXT1 || fmt == TexDb.Format.DXT5
    val replaced = Retex.resolve(d, name) ?: like
    val limit = if (o.maxSize >= 0) o.maxSize else replaced?.let { maxOf(it.width, it.height) } ?: 0
    val img = fitSize(img0, limit, o.pot, dxt)
    // Replacing keeps the old texture's attributes; only explicit choices override them.
    val oldAttrs = (replaced ?: d.find(name))?.attrs() ?: emptyMap()
    val attrs = LinkedHashMap<String, String>()
    attrs["alphamode"] = when {
      o.alphamode != "auto" -> o.alphamode
      !alpha -> "0"
      else -> oldAttrs["alphamode"]?.takeIf { it != "0" } ?: "2"
    }
    if (o.camnorm) attrs["camnorm"] = "1"
    if (o.streammode) attrs["streammode"] = "1"
    o.extra.split(' ').filter { it.contains('=') }.forEach { attrs[it.substringBefore('=')] = it.substringAfter('=') }
    return d.put(name, img, TexDb.PutOptions(fmt, o.mips, o.rle, attrs, like = like), srcCrc)
  }

  private fun fileCrc(f: File): Int {
    val c = CRC32()
    f.inputStream().buffered().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; c.update(b, 0, n) } }
    return c.value.toInt()
  }

  private fun ensureSpace(dir: File, need: Long) {
    if (dir.usableSpace < need + (64L shl 20)) throw NoSpace(need)
  }

  private fun texdbsOf(gameDir: String): List<TexDb> {
    val root = File(gameDir, "texdb")
    return TexDb.list(root).map { db(File(root, it).absolutePath) }
  }

  private fun loadModel(path: String): Adapter.Model {
    val f = File(path)
    val d = f.readBytes()
    // Damaged DFFs fail here with the file name in the message, not deep inside the batch.
    try { Rw.dffTextures(d) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("${f.name}: ${e.message}") }
    return Adapter.Model(f.name, d)
  }

  // ---- files ----

  fun listDir(path: String): List<DirItem> = (File(path).listFiles() ?: emptyArray()).map { DirItem(it.name, it.isDirectory, it.length()) }

  /** Folders that look like a mobile GTA SA data folder (contain texdb/ with databases), 2 levels deep. */
  fun findGameDirs(): List<String> {
    val out = ArrayList<String>()
    fun check(d: File) { if (TexDb.list(File(d, "texdb")).isNotEmpty()) out += d.absolutePath }
    File(storageRoot).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }?.forEach { d ->
      check(d)
      d.listFiles()?.filter { it.isDirectory }?.forEach(::check)
    }
    return out
  }

  fun clearCache() { cache.deleteRecursively() }

  // ---- texdb ----

  fun texdbList(gameDir: String): List<FileInfo> {
    val root = File(gameDir, "texdb")
    return TexDb.list(root).map { FileInfo(it, File(root, it).absolutePath, File(root, "$it/$it.dxt.dat").length()) }
  }

  fun texdbOpen(path: String, reload: Boolean = false): TexDbData {
    if (reload) synchronized(lock) { texdbs.remove(path) }
    val d = db(path)
    return synchronized(d) {
      TexDbData(d.header, d.wasted, d.datSize, d.entries.map {
        TexEntry(it.name, it.width, it.height, it.format?.label ?: if (it.offset < 0) "alias" else "?", it.mips, it.line, it.offset)
      })
    }
  }

  /** PNG preview path (cached), longest side about [size] px; null for aliases and unknown formats. */
  fun texdbPreview(path: String, name: String, size: Int): String? {
    val d = db(path)
    // An alias shows the texture it points to.
    val e = synchronized(d) { Retex.resolve(d, name) } ?: return null
    val f = File(cache, "prev/${path.hashCode().toUInt()}_${e.offset}_$size.png")
    if (f.exists()) return f.absolutePath
    var lv = 0; var w = e.width; var h = e.height
    if (e.mips) while (maxOf(w, h) / 2 >= size) { w /= 2; h /= 2; lv++ }
    val raw = synchronized(d) { d.readRaw(e) }
    val im = TexDb.decodeEntry(raw, lv) ?: return null
    savePng(im, f)
    return f.absolutePath
  }

  fun texdbExport(path: String, names: List<String>, destDir: String): Int {
    val d = db(path)
    var n = 0
    for (name in names) {
      val e = synchronized(d) { d.find(name) } ?: continue
      val im = synchronized(d) { d.decode(e) } ?: continue
      savePng(im, File(destDir, "${e.name}.png")); n++
    }
    return n
  }

  /** [items]: (image file, texture name). */
  fun texdbImport(path: String, items: List<Pair<String, String>>, o: ImportOptions): ImportResult {
    val d = db(path)
    synchronized(d) {
      backupTexdb(d)
      val added = ArrayList<String>(); val replaced = ArrayList<String>(); val skipped = ArrayList<String>(); val failed = ArrayList<String>()
      try {
        for ((file, name) in items) {
          if (!o.overwrite && d.find(name) != null) { skipped += name; continue }
          try {
            val src = File(file)
            if (importOne(d, loadImage(src.path, o.maxSize.takeIf { it > 0 } ?: 4096), name, o, fileCrc(src))) added += name else replaced += name
          } catch (e: Exception) { failed += "$name: ${e.message}" }
        }
      } finally { d.commit() }
      return ImportResult(added, replaced, skipped, failed)
    }
  }

  /** Returns the new .txt line of the texture. */
  fun texdbSetAttrs(path: String, name: String, changes: Map<String, String>): String {
    val d = db(path)
    synchronized(d) {
      val e = d.find(name) ?: throw IllegalArgumentException("not found: $name")
      backupTexdb(d); d.setAttrs(e, changes); d.commit()
      return e.line
    }
  }

  fun texdbRemove(path: String, names: List<String>) {
    val d = db(path)
    synchronized(d) {
      backupTexdb(d)
      for (n in names) d.find(n)?.let(d::remove)
      d.commit()
    }
  }

  /** Returns the bytes freed. */
  fun texdbCompact(path: String): Long {
    val d = db(path)
    synchronized(d) { backupTexdb(d); ensureSpace(d.dir, d.datSize - d.wasted); return d.compact() }
  }

  // ---- IMG ----

  fun imgList(gameDir: String): List<FileInfo> =
    File(gameDir).walkTopDown().maxDepth(3).filter { it.isFile && it.name.endsWith(".img", true) }
      .map { FileInfo(it.name, it.absolutePath, it.length()) }.toList()

  fun imgOpen(path: String, reload: Boolean = false): ImgData {
    if (reload) synchronized(lock) { imgs.remove(path) }
    val a = img(path)
    return synchronized(a) {
      ImgData(a.file.length(), a.usedSectors.toLong() * ImgArchive.SECTOR, a.entries.map { ImgEntry(it.name, it.sectors.toLong() * ImgArchive.SECTOR) })
    }
  }

  /** Which of [names] are not in the archive yet (case-insensitive). */
  fun imgMissing(path: String, names: List<String>): List<String> {
    val a = img(path)
    return synchronized(a) { names.filter { a.find(it) == null } }
  }

  fun imgImport(path: String, files: List<String>): ImportResult {
    val a = img(path)
    synchronized(a) {
      backup(a.file)
      val added = ArrayList<String>(); val replaced = ArrayList<String>(); val failed = ArrayList<String>()
      try {
        for (f in files) {
          val file = File(f)
          try { if (a.put(file.name, file.readBytes())) added += file.name else replaced += a.find(file.name)!!.name }
          catch (e: Exception) { failed += "${file.name}: ${e.message}" }
        }
      } finally { a.commit() }
      return ImportResult(added, replaced, emptyList(), failed)
    }
  }

  /** Replaces (or adds) entry [name] with the contents of [file], whatever the file is called. */
  fun imgReplace(path: String, name: String, file: String) {
    val a = img(path)
    synchronized(a) { backup(a.file); try { a.put(name, File(file).readBytes()) } finally { a.commit() } }
  }

  fun imgExport(path: String, names: List<String>, destDir: String): Int {
    val a = img(path)
    var n = 0
    synchronized(a) {
      for (name in names) {
        val e = a.find(name) ?: continue
        val raw = a.read(e)
        // RenderWare files carry their size: drop the sector padding.
        val len = if (name.endsWith(".dff", true) || name.endsWith(".txd", true)) Rw.streamLength(raw) else raw.size
        File(destDir).mkdirs(); File(destDir, e.name).writeBytes(raw.copyOf(len)); n++
      }
    }
    return n
  }

  fun imgRemove(path: String, names: List<String>) {
    val a = img(path)
    synchronized(a) { backup(a.file); for (n in names) a.find(n)?.let(a::remove); a.commit() }
  }

  /** Returns the bytes freed. */
  fun imgRebuild(path: String): Long {
    val a = img(path)
    synchronized(a) { backup(a.file); ensureSpace(a.file.parentFile!!, a.usedSectors.toLong() * ImgArchive.SECTOR); return a.rebuild() }
  }

  // ---- audio ----

  fun oswList(gameDir: String): List<FileInfo> =
    File(gameDir, "audio").walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".osw", true) && File(it.path + ".idx").exists() }
      .map { FileInfo(it.name, it.absolutePath, it.length(), it.parentFile!!.name) }
      .sortedBy { it.dir + it.name }.toList()

  fun oswTracks(path: String): List<Track> = OswArchive(File(path)).tracks.map { Track(it.name, it.size.toLong()) }

  fun oswExtract(path: String, index: Int, dest: String): String {
    val a = OswArchive(File(path))
    File(dest).parentFile?.mkdirs()
    a.extract(a.tracks[index], File(dest))
    return dest
  }

  /** Cached copy of a track for playback. */
  fun oswPreview(path: String, index: Int): String {
    val a = OswArchive(File(path))
    val t = a.tracks[index]
    val f = File(cache, "audio/${path.hashCode().toUInt()}_${index}_${t.offset}.mp3")
    if (!f.exists()) { f.parentFile?.mkdirs(); a.extract(t, f) }
    return f.absolutePath
  }

  /** [replace]: track index → mp3; [add]: mp3 files appended (named by the file, inside the folder of the last track). */
  fun oswApply(path: String, replace: Map<Int, String>, add: List<String>): Int {
    val a = OswArchive(File(path))
    backup(a.file); backup(a.idxFile)
    for ((k, v) in replace) a.tracks[k].replacement = File(v)
    val folder = a.tracks.lastOrNull()?.name?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" } ?: ""
    a.rebuild(add.map { "$folder${File(it).name}" to File(it) })
    return a.tracks.size
  }

  // ---- adaptation ----

  /** What the selected models use and where each texture comes from (no pixel decoding). */
  fun adaptAnalyze(dffs: List<String>, txds: List<String>): AdaptInfo {
    val dicts = txds.map { Adapter.Dict(File(it).name, Rw.readTxd(File(it).readBytes(), decode = false)) }
    val models = dffs.map { loadModel(it) }
    val src = Adapter.resolve(models, dicts)
    val usedKeys = HashSet<String>()
    val perModel = models.mapIndexed { i, m ->
      src[i].forEach { (t, d) -> if (d != null) usedKeys += "$d/${t.lowercase()}" }
      ModelInfo(m.name, src[i].filterValues { it != null }.map { "${it.key} ← ${dicts[it.value!!].name}" }, src[i].filterValues { it == null }.keys.toList())
    }
    val unused = dicts.flatMapIndexed { di, d -> d.textures.filter { "$di/${it.name.lowercase()}" !in usedKeys }.map { "${d.name}: ${it.name}" } }
    return AdaptInfo(perModel, unused, usedKeys.size)
  }

  /**
   * Writes "<outDir>/<model>.dff" for every model and the renamed textures as PNG. Optionally imports the
   * textures into a texdb ([texdb] path + import options) and the models into an IMG archive ([imgPath]).
   */
  fun adaptRun(dffs: List<String>, txds: List<String>, prefix: String, outDir: String, texdb: String?, o: ImportOptions, imgPath: String?): AdaptResult {
    val taken = texdb?.let { p -> db(p).let { x -> synchronized(x) { x.entries.map { it.name }.toSet() } } } ?: emptySet()
    val r = Adapter.adapt(dffs.map { loadModel(it) }, txds.map { Adapter.Dict(File(it).name, Rw.readTxd(File(it).readBytes())) }, prefix, taken)
    val out = File(outDir); out.mkdirs()
    for (m in r.models) File(out, m.name.lowercase()).writeBytes(m.dff)
    for (t in r.textures) savePng(TexDb.Image(t.w, t.h, t.px), File(out, "${t.name}.png"))
    if (texdb != null) {
      val x = db(texdb)
      synchronized(x) {
        backupTexdb(x)
        try { for (t in r.textures) importOne(x, TexDb.Image(t.w, t.h, t.px), t.name, o, 0) } finally { x.commit() }
      }
    }
    val imgAdded = ArrayList<String>()
    if (imgPath != null) {
      val a = img(imgPath)
      synchronized(a) { backup(a.file); try { for (m in r.models) if (a.put(m.name.lowercase(), m.dff)) imgAdded += m.name } finally { a.commit() } }
    }
    return AdaptResult(r.models.map { ModelResult(it.name, it.renamed, it.missing) }, r.textures.size, r.unused, imgAdded)
  }

  private fun retexPlan(gameDir: String, txds: List<String>, fallback: String?): Retex.Plan {
    val items = txds.flatMap { p -> val txd = File(p).nameWithoutExtension; Rw.readTxd(File(p).readBytes()).map { Retex.Item(txd, it.name, Retex.hash(it)) } }
    return Retex.plan(texdbsOf(gameDir), items, fallback?.let { db(it) })
  }

  /** Retexture: what [retexRun] would do (see core/Retex.kt). */
  fun retexAnalyze(gameDir: String, txds: List<String>, fallback: String?): RetexInfo {
    val p = retexPlan(gameDir, txds, fallback)
    return RetexInfo(p.writes.size, p.split, p.aliases.size, p.missing.map { "${it.name} (${it.txd})" })
  }

  /**
   * Retexture: puts TXD textures into the databases keeping per-TXD differences (models stay untouched).
   * With [skipSame], textures that look like what the game already has are not rewritten (whole-map packs
   * usually ship every texture, most of them unchanged).
   */
  fun retexRun(gameDir: String, txds: List<String>, o: ImportOptions, fallback: String?, skipSame: Boolean): RetexResult {
    val plan = retexPlan(gameDir, txds, fallback)
    val byItem = plan.writes.groupBy { it.item.key }
    var written = 0; var unchanged = 0
    val failed = ArrayList<String>()
    val notCreated = HashSet<String>()
    val touched = LinkedHashSet<TexDb>()
    try {
      for (p in txds) {
        val txd = File(p).nameWithoutExtension
        for (t in Rw.readTxd(File(p).readBytes())) for (w in byItem["${txd.lowercase()}/${t.name.lowercase()}"].orEmpty()) synchronized(w.db) {
          try {
            val cur = Retex.resolve(w.db, w.target) ?: w.like
            if (skipSame && cur != null && Retex.same(w.db, cur, t.px, t.w, t.h)) {
              unchanged++
              if (w.db.find(w.target) == null) notCreated += "${w.db.name}/${w.target.lowercase()}"
              return@synchronized
            }
            if (touched.add(w.db)) backupTexdb(w.db)
            importOne(w.db, TexDb.Image(t.w, t.h, t.px), w.target, o, 0, w.like)
            written++
          } catch (e: Exception) { failed += "${t.name} ($txd): ${e.message}" }
        }
      }
      // An alias to a copy that was not created would break the texture; without it the name keeps resolving as before.
      for (a in plan.aliases) if ("${a.db.name}/${a.target.lowercase()}" !in notCreated) synchronized(a.db) {
        if (touched.add(a.db)) backupTexdb(a.db)
        a.db.alias(a.name, a.target)
      }
    } finally { for (d in touched) synchronized(d) { d.commit() } }
    return RetexResult(written, unchanged, plan.aliases.size, plan.missing.map { "${it.name} (${it.txd})" }, failed)
  }
}
