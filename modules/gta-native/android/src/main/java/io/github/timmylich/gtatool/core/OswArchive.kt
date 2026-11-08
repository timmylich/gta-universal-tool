package io.github.timmylich.gtatool.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Mobile GTA SA audio pack: <name>.osw is a ZIP with stored (uncompressed) mp3 files,
 * <name>.osw.idx is the index the game actually reads:
 * u32 count, then per file: u32 data offset in .osw, u32 size, u16 name length, name.
 * Directory entries of the zip ("Bank_001/") are not listed in the index.
 * The game plays tracks by position, so replacing keeps the order and the name.
 */
class OswArchive(val file: File) {
  class Track(val name: String, var offset: Long, var size: Long, var replacement: File? = null)

  val idxFile = File(file.path + ".idx")
  val tracks = ArrayList<Track>()

  init {
    val b = ByteBuffer.wrap(idxFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    val n = b.int
    require(n in 0..100000) { "bad idx" }
    repeat(n) {
      val off = b.int.toLong() and 0xffffffffL
      val size = b.int.toLong() and 0xffffffffL
      val len = b.short.toInt() and 0xffff
      val nb = ByteArray(len); b.get(nb)
      tracks += Track(String(nb, Charsets.UTF_8), off, size)
    }
  }

  fun read(t: Track): ByteArray = RandomAccessFile(file, "r").use { raf ->
    raf.seek(t.offset); ByteArray(t.size.toInt()).also { raf.readFully(it) }
  }

  /** Copies one track to [dest] without loading it whole. */
  fun extract(t: Track, dest: File) = RandomAccessFile(file, "r").use { raf ->
    dest.outputStream().use { out -> copy(raf, t.offset, t.size, out) }
  }

  /** Rewrites .osw (stored zip) and .idx with pending replacements. [added] appends new files at the end. */
  fun rebuild(added: List<Pair<String, File>> = emptyList(), progress: (Float) -> Unit = {}) {
    val all = tracks.map { Triple(it.name, it.replacement, it) } + added.map { Triple(it.first, it.second, null) }
    val tmp = File(file.path + ".tmp")
    val central = java.io.ByteArrayOutputStream()
    val newIdx = ArrayList<Track>()
    var count = 0
    RandomAccessFile(file, "r").use { src ->
      java.io.BufferedOutputStream(tmp.outputStream(), 1 shl 20).use { out ->
        var pos = 0L
        val dirs = LinkedHashSet<String>()
        fun entry(name: String, size: Long, crc: Int, body: ((java.io.OutputStream) -> Unit)?) {
          val nb = name.toByteArray(Charsets.UTF_8)
          val h = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
          h.putInt(0x04034b50).putShort(10).putShort(0).putShort(0).putShort(0).putShort(0)
          h.putInt(crc).putInt(size.toInt()).putInt(size.toInt()).putShort(nb.size.toShort()).putShort(0)
          val cd = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
          cd.putInt(0x02014b50).putShort(10).putShort(10).putShort(0).putShort(0).putShort(0).putShort(0)
          cd.putInt(crc).putInt(size.toInt()).putInt(size.toInt()).putShort(nb.size.toShort()).putShort(0).putShort(0)
          cd.putShort(0).putShort(0).putInt(if (name.endsWith('/')) 0x10 else 0).putInt(pos.toInt())
          central.write(cd.array()); central.write(nb)
          out.write(h.array()); out.write(nb)
          pos += 30 + nb.size
          if (body != null) { body(out); newIdx += Track(name, pos, size) }
          pos += size
          count++
        }
        for ((i, t) in all.withIndex()) {
          val (name, repl, orig) = t
          val slash = name.lastIndexOf('/')
          if (slash > 0) { val d = name.substring(0, slash + 1); if (dirs.add(d)) entry(d, 0, 0, null) }
          if (repl != null) {
            val crc = CRC32(); repl.inputStream().buffered().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; crc.update(b, 0, n) } }
            entry(name, repl.length(), crc.value.toInt()) { o -> repl.inputStream().use { it.copyTo(o, 1 shl 16) } }
          } else {
            orig!!
            val crc = CRC32()
            val data = ByteArray(orig.size.toInt()); src.seek(orig.offset); src.readFully(data); crc.update(data)
            entry(name, orig.size, crc.value.toInt()) { o -> o.write(data) }
          }
          progress(i.toFloat() / all.size)
        }
        val cdOff = pos
        out.write(central.toByteArray())
        val end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        end.putInt(0x06054b50).putShort(0).putShort(0).putShort(count.toShort()).putShort(count.toShort())
        end.putInt(central.size()).putInt(cdOff.toInt()).putShort(0)
        out.write(end.array())
      }
    }
    val idx = java.io.ByteArrayOutputStream()
    val head = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newIdx.size); idx.write(head.array())
    for (t in newIdx) {
      val nb = t.name.toByteArray(Charsets.UTF_8)
      val b = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(t.offset.toInt()).putInt(t.size.toInt()).putShort(nb.size.toShort())
      idx.write(b.array()); idx.write(nb)
    }
    TexDb.replaceFile(tmp, file)
    TexDb.writeAtomic(idxFile, idx.toByteArray())
    tracks.clear(); tracks.addAll(newIdx)
  }

  companion object {
    fun copy(raf: RandomAccessFile, off: Long, size: Long, out: java.io.OutputStream) {
      raf.seek(off)
      val b = ByteArray(1 shl 16)
      var left = size
      while (left > 0) { val n = minOf(left, b.size.toLong()).toInt(); raf.readFully(b, 0, n); out.write(b, 0, n); left -= n }
    }
  }
}
