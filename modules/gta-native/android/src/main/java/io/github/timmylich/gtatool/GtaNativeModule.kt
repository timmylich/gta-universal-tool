package io.github.timmylich.gtatool

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import io.github.timmylich.gtatool.core.Adapter
import io.github.timmylich.gtatool.core.ImgArchive
import io.github.timmylich.gtatool.core.OswArchive
import io.github.timmylich.gtatool.core.Rw
import io.github.timmylich.gtatool.core.TexDb
import java.io.File
import java.util.zip.CRC32

/**
 * Bridge between the JS UI and the format code in `core`. All file work runs in AsyncFunctions (background
 * threads); opened databases/archives are cached by path so lists and previews do not re-read big files.
 * Every write first snapshots the original file into the backup folder (once per file), if backups are on.
 */
class GtaNativeModule : Module() {
  private val texdbs = HashMap<String, TexDb>()
  private val imgs = HashMap<String, ImgArchive>()
  private val lock = Any()
  private var backupRoot: File? = File(Environment.getExternalStorageDirectory(), "GTAUniversalTool/backup")

  private val cache get() = File(appContext.reactContext!!.cacheDir, "gut").apply { mkdirs() }

  private fun db(path: String): TexDb = synchronized(lock) {
    texdbs.getOrPut(path) { val f = File(path); TexDb(f, f.name) }
  }

  private fun img(path: String): ImgArchive = synchronized(lock) { imgs.getOrPut(path) { ImgArchive(File(path)) } }

  /** Copies [f] to the backup folder once (keeps the very first original). */
  private fun backup(f: File) {
    val root = backupRoot ?: return
    if (!f.exists()) return
    val sd = Environment.getExternalStorageDirectory().absolutePath
    val rel = f.absolutePath.removePrefix(sd).trimStart('/')
    val dest = File(root, rel)
    if (dest.exists()) return
    dest.parentFile?.mkdirs()
    ensureSpace(dest.parentFile!!, f.length())
    val tmp = File(dest.path + ".part")
    f.copyTo(tmp, true)
    tmp.renameTo(dest)
  }

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

  private fun pot(v: Int): Int {
    var p = 1
    while (p * 2 <= v) p *= 2
    // Nearest power of two (round up when closer).
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
    return if (w == img.w && h == img.h) img else TexDb.Image(w, h, io.github.timmylich.gtatool.core.Pixels.resize(img.px, img.w, img.h, w, h))
  }

  private fun importOne(d: TexDb, img0: TexDb.Image, name: String, o: Map<String, Any?>, srcCrc: Int): Boolean {
    val fmtName = o["format"] as? String ?: "auto"
    val alpha = io.github.timmylich.gtatool.core.Pixels.hasAlpha(img0.px)
    val fmt = when (fmtName) {
      "auto" -> if (alpha) TexDb.Format.DXT5 else TexDb.Format.DXT1
      else -> TexDb.Format.entries.first { it.label == fmtName }
    }
    val dxt = fmt == TexDb.Format.DXT1 || fmt == TexDb.Format.DXT5
    val img = fitSize(img0, (o["maxSize"] as? Number)?.toInt() ?: 0, o["pot"] as? Boolean ?: true, dxt)
    // Replacing keeps the old texture's attributes; only explicit choices override them.
    val oldAttrs = d.find(name)?.attrs() ?: emptyMap()
    val attrs = LinkedHashMap<String, String>()
    val am = (o["alphamode"] as? String) ?: "auto"
    attrs["alphamode"] = when {
      am != "auto" -> am
      !alpha -> "0"
      else -> oldAttrs["alphamode"]?.takeIf { it != "0" } ?: "2"
    }
    for (k in listOf("camnorm", "streammode")) if (o[k] == true) attrs[k] = "1"
    (o["extra"] as? String)?.split(' ')?.filter { it.contains('=') }?.forEach { attrs[it.substringBefore('=')] = it.substringAfter('=') }
    return d.put(name, img, TexDb.PutOptions(fmt, o["mips"] as? Boolean ?: true, o["rle"] as? Boolean ?: true, attrs), srcCrc)
  }

  private fun fileCrc(f: File): Int {
    val c = CRC32()
    f.inputStream().buffered().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; c.update(b, 0, n) } }
    return c.value.toInt()
  }

  /** Throws "NO_SPACE:<bytes>" (shown localized by the UI) when [dir]'s volume cannot take [need] bytes plus a margin. */
  private fun ensureSpace(dir: File, need: Long) {
    if (dir.usableSpace < need + (64L shl 20)) throw java.io.IOException("NO_SPACE:$need")
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

  private fun backupTexdb(d: TexDb) { backup(d.txtFile); backup(d.tocFile); backup(d.datFile); backup(d.tmbFile) }

  override fun definition() = ModuleDefinition {
    Name("GtaNative")

    // ---- storage ----
    Function("hasStorageAccess") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }

    Function("openStorageSettings") {
      val ctx = appContext.reactContext ?: return@Function null
      val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
      else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.startActivity(i)
      null
    }

    Function("storageRoot") { Environment.getExternalStorageDirectory().absolutePath }

    Function("setBackupDir") { path: String? -> backupRoot = path?.let { File(it) }; null }

    AsyncFunction("listDir") { path: String ->
      val f = File(path)
      (f.listFiles() ?: emptyArray()).map {
        mapOf("name" to it.name, "dir" to it.isDirectory, "size" to it.length().toDouble(), "mtime" to it.lastModified().toDouble())
      }
    }

    AsyncFunction("exists") { path: String -> File(path).exists() }

    /** Folders that look like a mobile GTA SA data folder (contain texdb/ with databases), 2 levels deep. */
    AsyncFunction("findGameDirs") {
      val root = Environment.getExternalStorageDirectory()
      val out = ArrayList<String>()
      fun check(d: File) { if (TexDb.list(File(d, "texdb")).isNotEmpty()) out += d.absolutePath }
      root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }?.forEach { d ->
        check(d)
        d.listFiles()?.filter { it.isDirectory }?.forEach(::check)
      }
      out
    }

    AsyncFunction("copyFile") { from: String, to: String ->
      File(to).parentFile?.mkdirs(); File(from).copyTo(File(to), true); null
    }

    // ---- texdb ----
    AsyncFunction("texdbList") { gameDir: String ->
      val root = File(gameDir, "texdb")
      TexDb.list(root).map { mapOf("name" to it, "path" to File(root, it).absolutePath, "size" to File(root, "$it/$it.dxt.dat").length().toDouble()) }
    }

    AsyncFunction("texdbOpen") { path: String, reload: Boolean ->
      if (reload) synchronized(lock) { texdbs.remove(path) }
      val d = db(path)
      synchronized(d) {
        mapOf(
          "header" to d.header, "wasted" to d.wasted.toDouble(), "datSize" to d.datSize.toDouble(),
          "entries" to d.entries.map {
            mapOf("name" to it.name, "w" to it.width, "h" to it.height, "fmt" to (it.format?.label ?: if (it.offset < 0) "alias" else "?"),
              "mips" to it.mips, "line" to it.line, "key" to it.offset.toDouble())
          },
        )
      }
    }

    /** PNG preview path (cached), longest side about [size] px. */
    AsyncFunction("texdbPreview") { path: String, name: String, size: Int ->
      val d = db(path)
      val e = synchronized(d) { d.find(name) } ?: return@AsyncFunction null
      if (e.offset < 0) return@AsyncFunction null
      val f = File(cache, "prev/${path.hashCode().toUInt()}_${e.offset}_$size.png")
      if (f.exists()) return@AsyncFunction f.absolutePath
      var lv = 0; var w = e.width; var h = e.height
      if (e.mips) while (maxOf(w, h) / 2 >= size) { w /= 2; h /= 2; lv++ }
      val raw = synchronized(d) { d.readRaw(e) }
      val im = TexDb.decodeEntry(raw, lv) ?: return@AsyncFunction null
      savePng(im, f)
      f.absolutePath
    }

    AsyncFunction("texdbExport") { path: String, names: List<String>, destDir: String ->
      val d = db(path)
      var n = 0
      for (name in names) {
        val e = synchronized(d) { d.find(name) } ?: continue
        val im = synchronized(d) { d.decode(e) } ?: continue
        savePng(im, File(destDir, "${e.name}.png")); n++
      }
      n
    }

    /** items: [{file, name}], opts: format/mips/rle/maxSize/pot/alphamode/camnorm/streammode/extra/overwrite. */
    AsyncFunction("texdbImport") { path: String, items: List<Map<String, String>>, opts: Map<String, Any?> ->
      val d = db(path)
      synchronized(d) {
        backupTexdb(d)
        val added = ArrayList<String>(); val replaced = ArrayList<String>(); val skipped = ArrayList<String>(); val failed = ArrayList<String>()
        val overwrite = opts["overwrite"] as? Boolean ?: true
        try {
          for (it in items) {
            val name = it["name"]!!
            if (!overwrite && d.find(name) != null) { skipped += name; continue }
            try {
              val src = File(it["file"]!!)
              if (importOne(d, loadImage(src.path, (opts["maxSize"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 4096), name, opts, fileCrc(src))) added += name else replaced += name
            } catch (e: Exception) { failed += "$name: ${e.message}" }
          }
        } finally { d.commit() }
        mapOf("added" to added, "replaced" to replaced, "skipped" to skipped, "failed" to failed)
      }
    }

    AsyncFunction("texdbSetAttrs") { path: String, name: String, changes: Map<String, String> ->
      val d = db(path)
      synchronized(d) {
        val e = d.find(name) ?: throw IllegalArgumentException("not found: $name")
        backupTexdb(d); d.setAttrs(e, changes); d.commit(); e.line
      }
    }

    AsyncFunction("texdbRemove") { path: String, names: List<String> ->
      val d = db(path)
      synchronized(d) {
        backupTexdb(d)
        for (n in names) d.find(n)?.let(d::remove)
        d.commit()
      }
      null
    }

    AsyncFunction("texdbCompact") { path: String ->
      val d = db(path)
      synchronized(d) { backupTexdb(d); ensureSpace(d.dir, d.datSize - d.wasted); d.compact().toDouble() }
    }

    // ---- IMG ----
    AsyncFunction("imgList") { gameDir: String ->
      File(gameDir).walkTopDown().maxDepth(3).filter { it.isFile && it.name.endsWith(".img", true) }
        .map { mapOf("name" to it.name, "path" to it.absolutePath, "size" to it.length().toDouble()) }.toList()
    }

    AsyncFunction("imgOpen") { path: String, reload: Boolean ->
      if (reload) synchronized(lock) { imgs.remove(path) }
      val a = img(path)
      synchronized(a) {
        mapOf(
          "fileSize" to a.file.length().toDouble(), "used" to (a.usedSectors * ImgArchive.SECTOR).toDouble(),
          "entries" to a.entries.map { mapOf("name" to it.name, "size" to (it.sectors.toDouble() * ImgArchive.SECTOR)) },
        )
      }
    }

    /** Which of [names] are not in the archive yet (case-insensitive). */
    AsyncFunction("imgMissing") { path: String, names: List<String> ->
      val a = img(path)
      synchronized(a) { names.filter { a.find(it) == null } }
    }

    AsyncFunction("imgImport") { path: String, files: List<String> ->
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
        mapOf("added" to added, "replaced" to replaced, "failed" to failed)
      }
    }

    /** Replaces (or adds) entry [name] with the contents of [file], whatever the file is called. */
    AsyncFunction("imgReplace") { path: String, name: String, file: String ->
      val a = img(path)
      synchronized(a) { backup(a.file); try { a.put(name, File(file).readBytes()) } finally { a.commit() } }
    }

    AsyncFunction("imgExport") { path: String, names: List<String>, destDir: String ->
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
      n
    }

    AsyncFunction("imgRemove") { path: String, names: List<String> ->
      val a = img(path)
      synchronized(a) { backup(a.file); for (n in names) a.find(n)?.let(a::remove); a.commit() }
      null
    }

    AsyncFunction("imgRebuild") { path: String ->
      val a = img(path)
      synchronized(a) { backup(a.file); ensureSpace(a.file.parentFile!!, a.usedSectors * ImgArchive.SECTOR); a.rebuild().toDouble() }
    }

    // ---- audio ----
    AsyncFunction("oswList") { gameDir: String ->
      File(gameDir, "audio").walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".osw", true) && File(it.path + ".idx").exists() }
        .map { mapOf("name" to it.name, "path" to it.absolutePath, "dir" to it.parentFile!!.name, "size" to it.length().toDouble()) }
        .sortedBy { it["dir"] as String + it["name"] }.toList()
    }

    AsyncFunction("oswTracks") { path: String ->
      OswArchive(File(path)).tracks.map { mapOf("name" to it.name, "size" to it.size.toDouble()) }
    }

    AsyncFunction("oswExtract") { path: String, index: Int, dest: String ->
      val a = OswArchive(File(path))
      File(dest).parentFile?.mkdirs()
      a.extract(a.tracks[index], File(dest)); dest
    }

    /** Cached copy of a track for playback. */
    AsyncFunction("oswPreview") { path: String, index: Int ->
      val a = OswArchive(File(path))
      val t = a.tracks[index]
      val f = File(cache, "audio/${path.hashCode().toUInt()}_${index}_${t.offset}.mp3")
      if (!f.exists()) { f.parentFile?.mkdirs(); a.extract(t, f) }
      f.absolutePath
    }

    /** replace: {index: srcPath}, add: [srcPath] (named by the file name, inside the folder of the last track). */
    AsyncFunction("oswApply") { path: String, replace: Map<String, String>, add: List<String> ->
      val a = OswArchive(File(path))
      backup(a.file); backup(a.idxFile)
      for ((k, v) in replace) a.tracks[k.toInt()].replacement = File(v)
      val folder = a.tracks.lastOrNull()?.name?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" } ?: ""
      a.rebuild(add.map { "$folder${File(it).name}" to File(it) })
      a.tracks.size
    }

    // ---- adaptation ----
    /** What the selected models use and where each texture comes from (no pixel decoding). */
    AsyncFunction("adaptAnalyze") { dffs: List<String>, txds: List<String> ->
      val dicts = txds.map { Adapter.Dict(File(it).name, Rw.readTxd(File(it).readBytes(), decode = false)) }
      val models = dffs.map { loadModel(it) }
      val src = Adapter.resolve(models, dicts)
      val usedKeys = HashSet<String>()
      val perModel = models.mapIndexed { i, m ->
        src[i].forEach { (t, d) -> if (d != null) usedKeys += "$d/${t.lowercase()}" }
        mapOf(
          "name" to m.name,
          "found" to src[i].filterValues { it != null }.map { "${it.key} ← ${dicts[it.value!!].name}" },
          "missing" to src[i].filterValues { it == null }.keys.toList(),
        )
      }
      val unused = dicts.flatMapIndexed { di, d -> d.textures.filter { "$di/${it.name.lowercase()}" !in usedKeys }.map { "${d.name}: ${it.name}" } }
      mapOf("models" to perModel, "unused" to unused, "textures" to usedKeys.size)
    }

    /**
     * Writes "<outDir>/<model>.dff" for every model and the renamed textures as PNG. Optionally imports the textures
     * into a texdb ([texdb] path + import options) and the models into an IMG archive ([imgPath]).
     */
    AsyncFunction("adaptRun") { dffs: List<String>, txds: List<String>, prefix: String, outDir: String, texdb: String?, opts: Map<String, Any?>, imgPath: String? ->
      val taken = texdb?.let { p -> db(p).let { x -> synchronized(x) { x.entries.map { it.name }.toSet() } } } ?: emptySet()
      val r = Adapter.adapt(dffs.map { loadModel(it) }, txds.map { Adapter.Dict(File(it).name, Rw.readTxd(File(it).readBytes())) }, prefix, taken)
      val out = File(outDir); out.mkdirs()
      for (m in r.models) File(out, m.name.lowercase()).writeBytes(m.dff)
      for (t in r.textures) savePng(TexDb.Image(t.w, t.h, t.px), File(out, "${t.name}.png"))
      if (texdb != null) {
        val x = db(texdb)
        synchronized(x) {
          backupTexdb(x)
          try { for (t in r.textures) importOne(x, TexDb.Image(t.w, t.h, t.px), t.name, opts, 0) } finally { x.commit() }
        }
      }
      val imgAdded = ArrayList<String>()
      if (imgPath != null) {
        val a = img(imgPath)
        synchronized(a) { backup(a.file); try { for (m in r.models) if (a.put(m.name.lowercase(), m.dff)) imgAdded += m.name } finally { a.commit() } }
      }
      mapOf(
        "models" to r.models.map { mapOf("name" to it.name, "renamed" to it.renamed, "missing" to it.missing) },
        "textures" to r.textures.size, "unused" to r.unused, "imgAdded" to imgAdded,
      )
    }

    /** Retexture: for every texture of the TXDs, the database that already has it (null = none). */
    AsyncFunction("retexAnalyze") { gameDir: String, txds: List<String> ->
      val dbs = texdbsOf(gameDir)
      txds.flatMap { p -> Rw.readTxd(File(p).readBytes(), decode = false).map { t ->
        mapOf("name" to t.name, "txd" to File(p).name, "w" to t.w, "h" to t.h, "dbs" to dbs.filter { d -> synchronized(d) { d.find(t.name) != null } }.map { it.name })
      } }
    }

    /**
     * Retexture: puts TXD textures into the databases under their original names (models stay untouched).
     * A texture replaces every texture with the same name in any database; textures no database has are added
     * to [fallback] or skipped when it is null.
     */
    AsyncFunction("retexRun") { gameDir: String, txds: List<String>, opts: Map<String, Any?>, fallback: String? ->
      val dbs = texdbsOf(gameDir)
      val replaced = ArrayList<String>(); val added = ArrayList<String>(); val skipped = ArrayList<String>(); val failed = ArrayList<String>()
      val touched = LinkedHashSet<TexDb>()
      try {
        for (p in txds) for (t in Rw.readTxd(File(p).readBytes())) {
          // The same name may live in several databases (gta3 and gta_int): replace it everywhere.
          val targets = dbs.filter { d -> synchronized(d) { d.find(t.name) != null } }.ifEmpty { listOfNotNull(fallback?.let { db(it) }) }
          if (targets.isEmpty()) { skipped += t.name; continue }
          for (target in targets) synchronized(target) {
            if (touched.add(target)) backupTexdb(target)
            try {
              if (importOne(target, TexDb.Image(t.w, t.h, t.px), t.name, opts, 0)) added += "${t.name} → ${target.name}" else replaced += "${t.name} → ${target.name}"
            } catch (e: Exception) { failed += "${t.name}: ${e.message}" }
          }
        }
      } finally { for (d in touched) synchronized(d) { d.commit() } }
      mapOf("replaced" to replaced, "added" to added, "skipped" to skipped, "failed" to failed)
    }

    AsyncFunction("clearCache") { cache.deleteRecursively(); null }
  }
}
