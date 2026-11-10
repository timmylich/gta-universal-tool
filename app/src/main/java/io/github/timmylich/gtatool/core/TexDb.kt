package io.github.timmylich.gtatool.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * GTA SA mobile texture database (texdb/<name>/<name>.{txt,dxt.toc,dxt.dat,dxt.tmb}).
 *
 * - .txt  line 0: db header ("cat=0 name=Default ... defaultformat=2"), then one line per texture:
 *         `"name" width=W height=H img=XXXXXXXX png=XXXXXXXX [alphamode=1|2] [camnorm=1] [streammode=1] ...`
 *         Lines like `"A" "affiliate=B"` are aliases with no data of their own.
 * - .toc  u32 size of .dat, then u32 offset into .dat per txt line (0xFFFFFFFF = no data).
 * - .dat  per texture: u32 flags (high 16 = pixel format, low 16 = id shared with the thumbnail),
 *         u16 width, u16 height (bit 15 = has mip chain), u32 byte count from offset 12, u32 RLE marker, data.
 *         RLE: when marker != 0, a unit whose first byte equals the marker is `marker, count, unit` = unit × count.
 *         Unit = one DXT block (8/16 bytes) or 4 bytes for 16/32-bit formats.
 * - .tmb  8×8 thumbnails with the same header, one per texture that has data, in .txt order.
 */
class TexDb(val dir: File, val name: String) {
  enum class Format(val code: Int, val label: String, val unit: Int) {
    DXT1(0x83f0, "DXT1", 8), DXT5(0x83f3, "DXT5", 16), RGBA8888(0x1401, "RGBA8888", 4),
    RGB565(0x8363, "RGB565", 4), RGBA4444(0x8033, "RGBA4444", 4);

    companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
  }

  class Entry(var name: String, var line: String, var offset: Long, var thumb: ByteArray?) {
    var width = 0; var height = 0; var flags = 0; var mips = false
    val format get() = Format.of(flags ushr 16)
    fun attrs(): LinkedHashMap<String, String> = parseAttrs(line)
  }

  val txtFile = File(dir, "$name.txt")
  val tocFile = File(dir, "$name.dxt.toc")
  val datFile = File(dir, "$name.dxt.dat")
  val tmbFile = File(dir, "$name.dxt.tmb")
  var header = ""
  val entries = ArrayList<Entry>()
  private val byName = HashMap<String, Entry>()
  var datSize = 0L
  /** Bytes in .dat not referenced by any entry (reclaimed by [compact]). */
  val wasted: Long get() = datSize - entries.filter { it.offset >= 0 }.sumOf { entrySize(it) }
  private val sizes = HashMap<Long, Long>()
  var dirty = false; private set

  init { load() }

  private fun load() {
    val lines = txtFile.readText(Charsets.ISO_8859_1).split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
    header = lines.first()
    val toc = ByteBuffer.wrap(tocFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    datSize = toc.int.toLong() and 0xffffffffL
    val tmb = if (tmbFile.exists()) tmbFile.readBytes() else ByteArray(0)
    var tp = 0
    RandomAccessFile(datFile, "r").use { raf ->
      val hb = ByteArray(16)
      for (i in 1 until lines.size) {
        val l = lines[i]
        val off = if (toc.remaining() >= 4) toc.int.toLong() and 0xffffffffL else 0xffffffffL
        val e = Entry(nameOf(l), l, if (off == 0xffffffffL) -1 else off, null)
        if (e.offset >= 0) {
          raf.seek(e.offset); raf.readFully(hb)
          val b = ByteBuffer.wrap(hb).order(ByteOrder.LITTLE_ENDIAN)
          e.flags = b.int; e.width = b.short.toInt() and 0xffff
          val h = b.short.toInt() and 0xffff
          e.height = h and 0x7fff; e.mips = h and 0x8000 != 0
          sizes[e.offset] = 12L + (b.int.toLong() and 0xffffffffL)
          if (tp + 12 <= tmb.size) {
            val n = 12 + ByteBuffer.wrap(tmb, tp + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            e.thumb = tmb.copyOfRange(tp, minOf(tmb.size, tp + n)); tp += n
          }
        }
        entries += e
        byName.putIfAbsent(e.name.lowercase(), e)
      }
    }
  }

  private fun entrySize(e: Entry) = sizes[e.offset] ?: 0L

  fun find(name: String) = byName[name.lowercase()]

  /** Raw entry bytes from .dat (header + data). */
  fun readRaw(e: Entry): ByteArray {
    RandomAccessFile(datFile, "r").use { raf ->
      raf.seek(e.offset)
      val out = ByteArray(entrySize(e).toInt())
      raf.readFully(out)
      return out
    }
  }

  class Image(val w: Int, val h: Int, val px: IntArray)

  /** Decodes mip [level] (0 = full size; clamped to the last level). */
  fun decode(e: Entry, level: Int = 0): Image? {
    if (e.offset < 0) return null
    return decodeEntry(readRaw(e), level)
  }

  /** Decodes the stored thumbnail (8×8), if any. */
  fun decodeThumb(e: Entry): Image? = e.thumb?.let { decodeEntry(it, 0) }

  // ---- writing ----

  class PutOptions(
    val format: Format,
    val mips: Boolean = true,
    val rle: Boolean = true,
    /** Attributes written to the txt line besides width/height/img/png (e.g. alphamode, camnorm, streammode). */
    val attrs: Map<String, String> = emptyMap(),
    /** Keep attributes of the replaced texture that are not in [attrs] (detailtile, hassibling, ...). */
    val keepOldAttrs: Boolean = true,
    /** Entry whose attributes a new texture (or a replaced alias) inherits, e.g. the copy it splits from. */
    val like: Entry? = null,
  )

  /**
   * Adds or replaces a texture (name match is case-insensitive; a replaced texture keeps its original name).
   * Pixel data is appended to .dat; call [commit] to write the index files.
   */
  fun put(texName: String, img: Image, opt: PutOptions, srcCrc: Int = 0): Boolean {
    require(texName.isNotEmpty() && texName.length <= 64 && !texName.contains('"')) { "bad texture name: $texName" }
    val old = find(texName)
    val id = old?.takeIf { it.offset >= 0 }?.let { it.flags and 0xffff } ?: freeId()
    val main = encodeEntry(img, opt.format, opt.mips, opt.rle, id)
    val thumbImg = Image(8, 8, Pixels.resize(img.px, img.w, img.h, 8, 8))
    val thumb = encodeEntry(thumbImg, opt.format, false, false, id)
    val off = datSize
    RandomAccessFile(datFile, "rw").use { raf -> raf.seek(off); raf.write(main); datSize = off + main.size }
    sizes[off] = main.size.toLong()
    val attrs = LinkedHashMap<String, String>()
    attrs["width"] = img.w.toString(); attrs["height"] = img.h.toString()
    attrs["img"] = "%08x".format(crc(main)); attrs["png"] = "%08x".format(if (srcCrc != 0) srcCrc else crc(thumb))
    val src = if (old != null && old.offset >= 0) old else opt.like ?: old
    if (src != null && opt.keepOldAttrs) for ((k, v) in src.attrs()) if (k !in attrs && k !in opt.attrs && k != "affiliate" && k != "format") attrs[k] = v
    for ((k, v) in opt.attrs) if (v.isNotEmpty() && v != "0") attrs[k] = v
    val e = old ?: Entry(texName, "", -1, null).also { entries += it; byName[texName.lowercase()] = it }
    e.line = "\"${e.name}\"" + attrs.entries.joinToString("") { " ${it.key}=${it.value}" }
    e.offset = off; e.thumb = thumb
    e.flags = (opt.format.code shl 16) or id; e.width = img.w; e.height = img.h; e.mips = opt.mips
    dirty = true
    return old == null
  }

  /** Makes [name] an alias of [target] (no data of its own), adding the line when it does not exist. */
  fun alias(name: String, target: String) {
    require(name.isNotEmpty() && name.length <= 64 && !name.contains('"')) { "bad texture name: $name" }
    val e = find(name) ?: Entry(name, "", -1, null).also { entries += it; byName[name.lowercase()] = it }
    e.line = "\"${e.name}\" \"affiliate=$target\""
    e.offset = -1; e.thumb = null; e.width = 0; e.height = 0; e.flags = 0; e.mips = false
    dirty = true
  }

  /** Changes txt attributes only (no pixel data). Empty or "0" removes a key. */
  fun setAttrs(e: Entry, changes: Map<String, String>) {
    val a = e.attrs()
    for ((k, v) in changes) if (v.isEmpty() || v == "0") a.remove(k) else a[k] = v
    e.line = "\"${e.name}\"" + a.entries.joinToString("") { " ${it.key}=${it.value}" }
    dirty = true
  }

  fun remove(e: Entry) {
    entries.remove(e)
    byName.remove(e.name.lowercase())
    entries.firstOrNull { it.name.equals(e.name, true) }?.let { byName[it.name.lowercase()] = it }
    dirty = true
  }

  /** Writes .toc/.tmb/.txt atomically (temp file + rename). */
  fun commit() {
    if (!dirty) return
    val toc = ByteBuffer.allocate(4 + 4 * entries.size).order(ByteOrder.LITTLE_ENDIAN)
    toc.putInt(datSize.toInt())
    for (e in entries) toc.putInt(if (e.offset < 0) -1 else e.offset.toInt())
    val tmb = java.io.ByteArrayOutputStream()
    for (e in entries) if (e.offset >= 0) tmb.write(e.thumb ?: encodeEntry(Image(8, 8, IntArray(64)), Format.RGBA8888, false, false, e.flags and 0xffff))
    val txt = buildString { append(header).append('\n'); for (e in entries) append(e.line).append('\n') }
    writeAtomic(tocFile, toc.array())
    writeAtomic(tmbFile, tmb.toByteArray())
    writeAtomic(txtFile, txt.toByteArray(Charsets.ISO_8859_1))
    dirty = false
  }

  /** Rewrites .dat with only the used entries (drops data of replaced/removed textures). Returns bytes saved. */
  fun compact(progress: (Float) -> Unit = {}): Long {
    val before = datSize
    val tmp = File(dir, "$name.dxt.dat.tmp")
    val newOff = HashMap<Entry, Long>()
    RandomAccessFile(datFile, "r").use { src ->
      java.io.BufferedOutputStream(tmp.outputStream(), 1 shl 20).use { out ->
        var pos = 0L
        val live = entries.filter { it.offset >= 0 }
        for ((i, e) in live.withIndex()) {
          val n = entrySize(e).toInt()
          val buf = ByteArray(n)
          src.seek(e.offset); src.readFully(buf)
          out.write(buf)
          newOff[e] = pos
          pos += n
          if (i % 256 == 0) progress(i.toFloat() / live.size)
        }
      }
    }
    val newSizes = HashMap<Long, Long>()
    for ((e, o) in newOff) { newSizes[o] = entrySize(e); e.offset = o }
    sizes.clear(); sizes.putAll(newSizes)
    datSize = tmp.length()
    replaceFile(tmp, datFile)
    dirty = true
    commit()
    return before - datSize
  }

  private fun freeId(): Int {
    val used = entries.filter { it.offset >= 0 }.map { it.flags and 0xffff }.toHashSet()
    val r = java.util.Random()
    repeat(100000) { val v = r.nextInt(0x10000); if (v !in used) return v }
    return r.nextInt(0x10000)
  }

  companion object {
    fun nameOf(line: String): String {
      if (!line.startsWith('"')) return line.substringBefore(' ')
      val end = line.indexOf('"', 1)
      return if (end < 0) line.substring(1) else line.substring(1, end)
    }

    fun parseAttrs(line: String): LinkedHashMap<String, String> {
      val out = LinkedHashMap<String, String>()
      val end = if (line.startsWith('"')) line.indexOf('"', 1) + 1 else line.indexOf(' ').let { if (it < 0) line.length else it }
      for (tok in line.substring(end).split(' ')) {
        val t = tok.trim('"')
        val eq = t.indexOf('=')
        if (eq > 0) out[t.substring(0, eq)] = t.substring(eq + 1)
      }
      return out
    }

    /** Lists texture databases in a texdb folder: names whose <name>/<name>.txt and .dxt.toc exist. */
    fun list(texdb: File): List<String> =
      texdb.listFiles()?.filter { File(it, "${it.name}.txt").exists() && File(it, "${it.name}.dxt.toc").exists() }?.map { it.name }?.sorted() ?: emptyList()

    fun crc(b: ByteArray): Int = CRC32().also { it.update(b) }.value.toInt()

    private fun mipBytes(fmt: Format, w: Int, h: Int) = when (fmt) {
      Format.DXT1 -> ((w + 3) / 4) * ((h + 3) / 4) * 8
      Format.DXT5 -> ((w + 3) / 4) * ((h + 3) / 4) * 16
      Format.RGBA8888 -> w * h * 4
      else -> w * h * 2
    }

    private fun encodeLevel(fmt: Format, px: IntArray, w: Int, h: Int): ByteArray = when (fmt) {
      Format.DXT1 -> Pixels.encodeDxt(px, w, h, 1)
      Format.DXT5 -> Pixels.encodeDxt(px, w, h, 5)
      Format.RGBA8888 -> Pixels.encodeRaw(px, "8888")
      Format.RGB565 -> Pixels.encodeRaw(px, "565")
      Format.RGBA4444 -> Pixels.encodeRaw(px, "4444")
    }

    /** Builds a full .dat entry (header + optionally RLE-packed pixel data). */
    fun encodeEntry(img: Image, fmt: Format, mips: Boolean, rle: Boolean, id: Int): ByteArray {
      require(img.w in 1..4096 && img.h in 1..4096) { "bad size ${img.w}x${img.h}" }
      val raw = java.io.ByteArrayOutputStream()
      var px = img.px; var w = img.w; var h = img.h
      while (true) {
        raw.write(encodeLevel(fmt, px, w, h))
        if (!mips || (w == 1 && h == 1)) break
        px = Pixels.half(px, w, h); w = maxOf(1, w / 2); h = maxOf(1, h / 2)
      }
      while (raw.size() % fmt.unit != 0) raw.write(0)
      var data = raw.toByteArray()
      var marker = 0
      if (rle) rleEncode(data, fmt.unit)?.let { (m, packed) -> marker = m; data = packed }
      val b = ByteBuffer.allocate(16 + data.size).order(ByteOrder.LITTLE_ENDIAN)
      b.putInt((fmt.code shl 16) or (id and 0xffff))
      b.putShort(img.w.toShort()); b.putShort((img.h or if (mips) 0x8000 else 0).toShort())
      b.putInt(4 + data.size); b.putInt(marker); b.put(data)
      return b.array()
    }

    /** Returns (marker, packed) or null when packing does not help or no free marker byte exists. */
    fun rleEncode(d: ByteArray, unit: Int): Pair<Int, ByteArray>? {
      val n = d.size / unit
      val used = BooleanArray(256)
      for (i in 0 until n) used[d[i * unit].toInt() and 255] = true
      val marker = (1..255).firstOrNull { !used[it] } ?: return null
      val out = java.io.ByteArrayOutputStream(d.size)
      var i = 0
      while (i < n) {
        var run = 1
        while (i + run < n && run < 255 && same(d, i * unit, (i + run) * unit, unit)) run++
        if (run >= 2) { out.write(marker); out.write(run) }
        out.write(d, i * unit, unit)
        i += run
      }
      return if (out.size() < d.size) marker to out.toByteArray() else null
    }

    private fun same(d: ByteArray, a: Int, b: Int, n: Int): Boolean {
      for (k in 0 until n) if (d[a + k] != d[b + k]) return false
      return true
    }

    fun rleDecode(d: ByteArray, off: Int, end: Int, unit: Int, marker: Int, limit: Int): ByteArray {
      if (marker == 0) return d.copyOfRange(off, end)
      val out = java.io.ByteArrayOutputStream(limit)
      var i = off
      while (i < end && out.size() < limit) {
        if ((d[i].toInt() and 255) == marker && i + 2 + unit <= end) {
          val cnt = d[i + 1].toInt() and 255
          repeat(cnt) { out.write(d, i + 2, unit) }
          i += 2 + unit
        } else {
          out.write(d, i, minOf(unit, end - i)); i += unit
        }
      }
      return out.toByteArray()
    }

    fun decodeEntry(raw: ByteArray, level: Int): Image? {
      val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
      val flags = b.int; val w0 = b.short.toInt() and 0xffff; val hh = b.short.toInt() and 0xffff
      val size = b.int; val marker = b.int
      val fmt = Format.of(flags ushr 16) ?: return null
      val mips = hh and 0x8000 != 0
      var w = w0; var h = hh and 0x7fff
      var skip = 0L
      var lv = 0
      while (lv < level && mips && !(w == 1 && h == 1)) { skip += mipBytes(fmt, w, h); w = maxOf(1, w / 2); h = maxOf(1, h / 2); lv++ }
      val need = (skip + mipBytes(fmt, w, h)).toInt()
      val data = rleDecode(raw, 16, minOf(raw.size, 12 + size), fmt.unit, marker, need)
      val o = skip.toInt()
      val px = when (fmt) {
        Format.DXT1 -> Pixels.decodeDxt(data, o, w, h, 1)
        Format.DXT5 -> Pixels.decodeDxt(data, o, w, h, 5)
        Format.RGBA8888 -> Pixels.decodeRaw(data, o, w, h, "8888")
        Format.RGB565 -> Pixels.decodeRaw(data, o, w, h, "565")
        Format.RGBA4444 -> Pixels.decodeRaw(data, o, w, h, "4444")
      }
      return Image(w, h, px)
    }

    fun writeAtomic(f: File, bytes: ByteArray) {
      val tmp = File(f.parentFile, f.name + ".tmp")
      tmp.writeBytes(bytes)
      replaceFile(tmp, f)
    }

    fun replaceFile(tmp: File, f: File) {
      if (!tmp.renameTo(f)) {
        // Some storage providers refuse rename-over: delete then rename.
        f.delete()
        if (!tmp.renameTo(f)) throw java.io.IOException("cannot replace $f")
      }
    }
  }
}
