package io.github.timmylich.gtatool.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GTA SA IMG archive, version 2: "VER2", u32 count, then 32-byte entries
 * (u32 offset in 2048-byte sectors, u16 size in sectors, u16 0, char[24] name), then data.
 * Changes are applied to the file right away; the directory is written by [commit].
 */
class ImgArchive(val file: File) {
  class Entry(var name: String, var offset: Long, var sectors: Int)

  val entries = ArrayList<Entry>()
  private val byName = HashMap<String, Entry>()
  var dirty = false; private set

  init {
    RandomAccessFile(file, "r").use { raf ->
      val head = ByteArray(8); raf.readFully(head)
      require(String(head, 0, 4, Charsets.ISO_8859_1) == "VER2") { "not a VER2 IMG archive" }
      val n = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
      require(n in 0..1_000_000) { "bad entry count $n" }
      val dir = ByteArray(n * 32); raf.readFully(dir)
      val b = ByteBuffer.wrap(dir).order(ByteOrder.LITTLE_ENDIAN)
      for (i in 0 until n) {
        val off = b.int.toLong() and 0xffffffffL
        val s1 = b.short.toInt() and 0xffff
        val s2 = b.short.toInt() and 0xffff
        val nb = ByteArray(24); b.get(nb)
        val z = nb.indexOf(0).let { if (it < 0) 24 else it }
        val e = Entry(String(nb, 0, z, Charsets.ISO_8859_1), off, if (s1 != 0) s1 else s2)
        entries += e
        byName.putIfAbsent(e.name.lowercase(), e)
      }
    }
  }

  fun find(name: String) = byName[name.lowercase()]

  /** Sectors used by data (for the "rebuild would save" estimate). */
  val fileSectors get() = (file.length() + SECTOR - 1) / SECTOR
  val usedSectors get() = dirSectors(entries.size) + entries.sumOf { it.sectors.toLong() }

  fun read(e: Entry): ByteArray = RandomAccessFile(file, "r").use { raf ->
    raf.seek(e.offset * SECTOR)
    ByteArray(e.sectors * SECTOR).also { raf.readFully(it) }
  }

  /**
   * Adds or replaces a file (case-insensitive: "Car.DFF" replaces "car.dff" and keeps the stored name).
   * Returns true when a new entry was created.
   */
  fun put(name: String, data: ByteArray): Boolean {
    require(name.isNotEmpty() && name.length <= 23 && name.all { it.code in 33..126 }) { "name must be 1-23 ASCII chars: $name" }
    val sectors = ((data.size + SECTOR - 1) / SECTOR).coerceAtLeast(1)
    require(sectors <= 0xffff) { "file too large" }
    val old = find(name)
    RandomAccessFile(file, "rw").use { raf ->
      val e = old ?: Entry(name, 0, 0).also { entries += it; byName[name.lowercase()] = it; makeDirRoom(raf) }
      val offset = if (old != null && sectors <= old.sectors) old.offset else endSector(raf)
      raf.seek(offset * SECTOR)
      raf.write(data)
      val pad = sectors * SECTOR - data.size
      if (pad > 0) raf.write(ByteArray(pad))
      e.offset = offset; e.sectors = sectors
    }
    dirty = true
    return old == null
  }

  fun remove(e: Entry) {
    entries.remove(e)
    byName.remove(e.name.lowercase())
    dirty = true
  }

  /** Writes the directory in place. */
  fun commit() {
    if (!dirty) return
    RandomAccessFile(file, "rw").use { raf -> raf.seek(0); raf.write(directory()) }
    dirty = false
  }

  /** Rewrites the archive without gaps left by replaced/removed files. Returns bytes saved. */
  fun rebuild(progress: (Float) -> Unit = {}): Long {
    val before = file.length()
    val tmp = File(file.parentFile, file.name + ".tmp")
    RandomAccessFile(file, "r").use { src ->
      RandomAccessFile(tmp, "rw").use { out ->
        out.setLength(0)
        var pos = dirSectors(entries.size)
        out.seek(pos * SECTOR)
        val buf = ByteArray(1 shl 20)
        val sorted = entries.sortedBy { it.offset }
        for ((i, e) in sorted.withIndex()) {
          src.seek(e.offset * SECTOR)
          var left = e.sectors.toLong() * SECTOR
          while (left > 0) {
            val n = minOf(left, buf.size.toLong()).toInt()
            src.readFully(buf, 0, n); out.write(buf, 0, n); left -= n
          }
          e.offset = pos; pos += e.sectors
          if (i % 128 == 0) progress(i.toFloat() / sorted.size)
        }
        out.seek(0); out.write(directory())
      }
    }
    TexDb.replaceFile(tmp, file)
    dirty = false
    return before - file.length()
  }

  private fun directory(): ByteArray {
    val b = ByteBuffer.allocate(8 + 32 * entries.size).order(ByteOrder.LITTLE_ENDIAN)
    b.put("VER2".toByteArray()); b.putInt(entries.size)
    for (e in entries) {
      b.putInt(e.offset.toInt()); b.putShort(e.sectors.toShort()); b.putShort(0)
      val nb = ByteArray(24); val s = e.name.toByteArray(Charsets.ISO_8859_1)
      System.arraycopy(s, 0, nb, 0, minOf(23, s.size)); b.put(nb)
    }
    return b.array()
  }

  private fun endSector(raf: RandomAccessFile): Long {
    val byData = entries.maxOfOrNull { it.offset + it.sectors } ?: 0L
    return maxOf(byData, (raf.length() + SECTOR - 1) / SECTOR, dirSectors(entries.size))
  }

  /** The directory grew: move files that now overlap it to the end of the archive. */
  private fun makeDirRoom(raf: RandomAccessFile) {
    val need = dirSectors(entries.size)
    for (e in entries.sortedBy { it.offset }) {
      if (e.sectors == 0 || e.offset >= need) continue
      raf.seek(e.offset * SECTOR)
      val data = ByteArray(e.sectors * SECTOR); raf.readFully(data)
      val to = endSector(raf)
      raf.seek(to * SECTOR); raf.write(data)
      e.offset = to
      dirty = true
    }
  }

  companion object {
    const val SECTOR = 2048
    fun dirSectors(n: Int) = ((8L + 32L * n + SECTOR - 1) / SECTOR)
  }
}
