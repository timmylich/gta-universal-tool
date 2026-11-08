package io.github.timmylich.gtatool.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RenderWare binary streams (DFF models, PC TXD dictionaries).
 * A chunk is u32 type, u32 size, u32 version, then [size] bytes. Container chunks hold child chunks;
 * everything else is kept as raw bytes, so a DFF is written back byte-identical except for renamed textures.
 */
object Rw {
  const val STRUCT = 0x01; const val STRING = 0x02; const val EXTENSION = 0x03; const val TEXTURE = 0x06
  const val MATERIAL = 0x07; const val MATLIST = 0x08; const val FRAMELIST = 0x0E; const val GEOMETRY = 0x0F
  const val CLUMP = 0x10; const val ATOMIC = 0x14; const val TEXNATIVE = 0x15; const val TEXDICT = 0x16
  const val GEOMLIST = 0x1A; const val MATFX = 0x120; const val SPECULAR = 0x253F2F6
  private val CONTAINERS = setOf(EXTENSION, TEXTURE, MATERIAL, MATLIST, FRAMELIST, GEOMETRY, CLUMP, ATOMIC, TEXNATIVE, TEXDICT, GEOMLIST)

  class Chunk(val type: Int, val version: Int, var data: ByteArray, val children: MutableList<Chunk>?) {
    val size: Int get() = children?.sumOf { it.size + 12 } ?: data.size
  }

  fun parse(b: ByteArray, from: Int = 0, to: Int = b.size): MutableList<Chunk> {
    val out = ArrayList<Chunk>()
    var p = from
    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    while (p + 12 <= to) {
      val type = bb.getInt(p); val size = bb.getInt(p + 4); val ver = bb.getInt(p + 8)
      if (size < 0 || p + 12 + size > to) break
      val body = p + 12
      val kids = if (type in CONTAINERS) parse(b, body, body + size).takeIf { k -> k.sumOf { it.size + 12 } == size } else null
      out += Chunk(type, ver, if (kids == null) b.copyOfRange(body, body + size) else ByteArray(0), kids)
      p = body + size
    }
    return out
  }

  fun write(chunks: List<Chunk>): ByteArray {
    val o = java.io.ByteArrayOutputStream()
    fun w(c: Chunk) {
      val h = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(c.type).putInt(c.size).putInt(c.version)
      o.write(h.array())
      if (c.children != null) c.children.forEach(::w) else o.write(c.data)
    }
    chunks.forEach(::w)
    return o.toByteArray()
  }

  /** Size of the first top-level chunk incl. header (to trim IMG sector padding), or the whole array. */
  fun streamLength(b: ByteArray): Int {
    if (b.size < 12) return b.size
    val s = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
    return if (s in 0..(b.size - 12)) s + 12 else b.size
  }

  private fun cstr(d: ByteArray, off: Int = 0, max: Int = d.size - off): String {
    var e = off
    while (e < off + max && e < d.size && d[e] != 0.toByte()) e++
    return String(d, off, e - off, Charsets.ISO_8859_1)
  }

  private fun strChunk(s: String, version: Int): Chunk {
    val raw = s.toByteArray(Charsets.ISO_8859_1)
    val len = (raw.size + 4) and 3.inv() // null terminator, padded to 4
    return Chunk(STRING, version, raw.copyOf(len), null)
  }

  private fun walk(cs: List<Chunk>, f: (Chunk) -> Unit) { for (c in cs) { f(c); c.children?.let { walk(it, f) } } }

  // ---- DFF ----

  /** Texture names (and masks) referenced by a model, including MatFX env maps and specular maps. */
  fun dffTextures(dff: ByteArray): Set<String> {
    val names = LinkedHashSet<String>()
    renameDff(dff) { names += it; null }
    return names
  }

  /**
   * Renames texture references. [map] gets the old name and returns the new one or null to keep it.
   * Returns the new DFF bytes.
   */
  fun renameDff(dff: ByteArray, map: (String) -> String?): ByteArray {
    val tree = parse(dff, 0, streamLength(dff))
    require(tree.isNotEmpty() && tree.sumOf { it.size + 12 } == streamLength(dff)) { "damaged or unsupported RenderWare file" }
    fun texture(t: Chunk) {
      val kids = t.children ?: return
      for ((i, c) in kids.withIndex()) {
        if (c.type != STRING) continue
        val old = cstr(c.data)
        if (old.isEmpty()) continue
        map(old)?.let { kids[i] = strChunk(it, c.version) }
      }
    }
    walk(tree) { c ->
      when (c.type) {
        TEXTURE -> texture(c)
        MATFX -> c.data = renameMatFx(c.data, ::texture)
        SPECULAR -> if (c.data.size >= 28) {
          val old = cstr(c.data, 4, 24)
          if (old.isNotEmpty()) map(old)?.let { n ->
            val nb = n.toByteArray(Charsets.ISO_8859_1)
            java.util.Arrays.fill(c.data, 4, 28, 0); System.arraycopy(nb, 0, c.data, 4, minOf(23, nb.size))
          }
        }
      }
    }
    return write(tree)
  }

  /** MatFX material data: u32 effect type, then two effect slots, some of which embed a Texture chunk. */
  private fun renameMatFx(d: ByteArray, onTexture: (Chunk) -> Unit): ByteArray {
    val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
    val out = java.io.ByteArrayOutputStream()
    fun copy(n: Int) { val a = ByteArray(n); b.get(a); out.write(a) }
    fun tex() {
      if (b.remaining() < 4) return
      val has = b.int; out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(has).array())
      if (has == 0 || b.remaining() < 12) return
      val size = b.getInt(b.position() + 4)
      val chunks = parse(d, b.position(), b.position() + 12 + size)
      b.position(b.position() + 12 + size)
      chunks.forEach(onTexture)
      out.write(write(chunks))
    }
    try {
      copy(4)
      for (slot in 0..1) {
        if (b.remaining() < 4) break
        val type = b.getInt(b.position()); copy(4)
        when (type) {
          1 -> { copy(4); tex(); tex() } // bump map: coefficient, bump texture, height texture
          2 -> { copy(8); tex() } // env map: coefficient, framebuffer alpha
          4 -> { copy(12); tex() } // dual texture: src/dst blend
          5 -> {} // uv transform
        }
      }
      if (b.remaining() > 0) copy(b.remaining())
      return out.toByteArray()
    } catch (e: Exception) {
      return d // unknown layout: leave as is
    }
  }

  // ---- TXD (PC, D3D8/D3D9) ----

  class Tex(val name: String, val mask: String, val w: Int, val h: Int, val px: IntArray)

  /**
   * Reads PC TXD textures (top mip level) as ARGB. Unsupported formats are skipped.
   * With [decode] = false only names and sizes are read (px is empty): fast, for previews of what a TXD holds.
   */
  fun readTxd(txd: ByteArray, decode: Boolean = true): List<Tex> {
    val out = ArrayList<Tex>()
    walk(parse(txd)) { c ->
      if (c.type != TEXNATIVE) return@walk
      val s = c.children?.firstOrNull { it.type == STRUCT }?.data ?: return@walk
      runCatching { readNative(s, decode) }.getOrNull()?.let { out += it }
    }
    return out
  }

  private fun readNative(d: ByteArray, decode: Boolean): Tex? {
    val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
    val platform = b.int
    if (platform != 8 && platform != 9) return null
    b.int // filter
    val name = cstr(d, 8, 32); val mask = cstr(d, 40, 32)
    b.position(72)
    val raster = b.int
    val fourcc = b.int
    val w = b.short.toInt() and 0xffff; val h = b.short.toInt() and 0xffff
    val depth = b.get().toInt() and 255
    b.get(); b.get()
    val comp = b.get().toInt() and 255
    val pal8 = raster and 0x2000 != 0; val pal4 = raster and 0x4000 != 0
    var palette: IntArray? = null
    if (pal8 || pal4) {
      val n = if (pal8) 256 else 32 // PAL4 stores 32 entries in RW
      palette = IntArray(n) { val r = b.get().toInt() and 255; val g = b.get().toInt() and 255; val bl = b.get().toInt() and 255; val a = b.get().toInt() and 255; (a shl 24) or (r shl 16) or (g shl 8) or bl }
    }
    val size = b.int
    val p = b.position()
    if (size <= 0 || p + size > d.size || depth == 0) return null
    if (!decode) return Tex(name, mask, w, h, IntArray(0))
    val px: IntArray = when {
      palette != null -> IntArray(w * h) { i ->
        val idx = if (pal8) d[p + i].toInt() and 255 else ((d[p + i / 2].toInt() shr ((i % 2) * 4)) and 15)
        palette[idx]
      }
      platform == 9 && fourcc == 0x31545844 || platform == 8 && comp == 1 -> Pixels.decodeDxt(d, p, w, h, 1)
      platform == 9 && fourcc == 0x33545844 || platform == 8 && comp == 3 -> Pixels.decodeDxt(d, p, w, h, 3)
      platform == 9 && fourcc == 0x35545844 || platform == 8 && comp == 5 -> Pixels.decodeDxt(d, p, w, h, 5)
      else -> {
        // Uncompressed D3D formats are stored as BGRA/16-bit little-endian.
        val fmt = raster and 0x0f00
        IntArray(w * h) { i ->
          when (fmt) {
            0x0500, 0x0600 -> { // 8888 / 888 (B,G,R,A)
              val o = p + i * 4
              val a = if (fmt == 0x0500) d[o + 3].toInt() and 255 else 255
              (a shl 24) or ((d[o + 2].toInt() and 255) shl 16) or ((d[o + 1].toInt() and 255) shl 8) or (d[o].toInt() and 255)
            }
            else -> {
              val v = (d[p + i * 2].toInt() and 255) or ((d[p + i * 2 + 1].toInt() and 255) shl 8)
              when (fmt) {
                0x0200 -> (255 shl 24) or ((v shr 11 and 31) * 255 / 31 shl 16) or ((v shr 5 and 63) * 255 / 63 shl 8) or ((v and 31) * 255 / 31)
                0x0300 -> ((v shr 12 and 15) * 17 shl 24) or ((v shr 8 and 15) * 17 shl 16) or ((v shr 4 and 15) * 17 shl 8) or ((v and 15) * 17)
                else -> ((if (v and 0x8000 != 0) 255 else 0) shl 24) or ((v shr 10 and 31) * 255 / 31 shl 16) or ((v shr 5 and 31) * 255 / 31 shl 8) or ((v and 31) * 255 / 31)
              }
            }
          }
        }
      }
    }
    return Tex(name, mask, w, h, px)
  }
}
