// Self-test of the format code (see test-core.ps1 / CI).
// Part 1 needs nothing: it builds texdb, IMG, OSW, DFF and TXD files from scratch.
// Part 2 runs only when a real game folder is given: it works on copies, the folder is never modified.
import io.github.timmylich.gtatool.core.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val RW = 0x1803FFFF

fun chunk(type: Int, data: ByteArray) = Rw.Chunk(type, RW, data, null)
fun box(type: Int, vararg kids: Rw.Chunk) = Rw.Chunk(type, RW, ByteArray(0), kids.toMutableList())
fun str(s: String) = chunk(Rw.STRING, s.toByteArray().copyOf((s.length + 4) and 3.inv()))
fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)

/** A model whose materials use the given texture names. */
fun dff(vararg textures: String): ByteArray {
  val mats = textures.map { t -> box(Rw.MATERIAL, chunk(Rw.STRUCT, ByteArray(28)), box(Rw.TEXTURE, chunk(Rw.STRUCT, ByteArray(4)), str(t), str(""), box(Rw.EXTENSION)), box(Rw.EXTENSION)) }
  val matList = box(Rw.MATLIST, chunk(Rw.STRUCT, le(4).putInt(mats.size).array()), *mats.toTypedArray())
  val geo = box(Rw.GEOMETRY, chunk(Rw.STRUCT, ByteArray(16)), matList, box(Rw.EXTENSION))
  return Rw.write(listOf(box(Rw.CLUMP, chunk(Rw.STRUCT, ByteArray(12)), box(Rw.GEOMLIST, chunk(Rw.STRUCT, le(4).putInt(1).array()), geo), box(Rw.EXTENSION))))
}

/** A PC (D3D9) TXD with 8888 textures filled with one colour each. */
fun txd(vararg tex: Pair<String, Int>): ByteArray {
  val natives = tex.map { (name, argb) ->
    val w = 8; val h = 8
    val b = le(88 + 4 + w * h * 4)
    b.putInt(9).putInt(0x1102)
    b.put(name.toByteArray().copyOf(32)); b.put(ByteArray(32))
    b.putInt(0x0500).putInt(21).putShort(w.toShort()).putShort(h.toShort())
    b.put(32).put(1).put(4).put(0)
    b.putInt(w * h * 4)
    repeat(w * h) { b.put(argb.toByte()).put((argb shr 8).toByte()).put((argb shr 16).toByte()).put((argb ushr 24).toByte()) } // B,G,R,A
    box(Rw.TEXNATIVE, chunk(Rw.STRUCT, b.array()), box(Rw.EXTENSION))
  }
  return Rw.write(listOf(box(Rw.TEXDICT, chunk(Rw.STRUCT, le(4).putShort(tex.size.toShort()).putShort(2).array()), *natives.toTypedArray(), box(Rw.EXTENSION))))
}

fun gradient(w: Int, h: Int) = TexDb.Image(w, h, IntArray(w * h) { i -> ((i % w * 255 / w) shl 24) or ((i % w * 4) and 255 shl 16) or ((i / w * 4) and 255 shl 8) or 0x40 })

fun synthetic(out: File) {
  // texdb from scratch: every format, reload, replace keeps name and attributes, remove, compact.
  val dir = File(out, "syn").apply { deleteRecursively(); mkdirs() }
  File(dir, "syn.txt").writeText("cat=0 name=Default onfoot=5 slow=5 fast=5 defaultformat=2 defaultstream=0\n")
  File(dir, "syn.dxt.toc").writeBytes(ByteArray(4)); File(dir, "syn.dxt.dat").writeBytes(ByteArray(0)); File(dir, "syn.dxt.tmb").writeBytes(ByteArray(0))
  var db = TexDb(dir, "syn")
  val im = gradient(64, 32)
  for (f in TexDb.Format.entries) check(db.put("Tex_${f.label}", im, TexDb.PutOptions(f, attrs = mapOf("camnorm" to "1"))))
  db.commit()
  db = TexDb(dir, "syn")
  for (f in TexDb.Format.entries) {
    val e = db.find("tex_${f.label}")!!
    val back = db.decode(e)!!
    check(e.format == f && back.w == 64 && back.h == 32 && db.decodeThumb(e)!!.w == 8) { "texdb $f" }
    if (f == TexDb.Format.RGBA8888) check(back.px.contentEquals(im.px)) { "8888 must be lossless" }
    check(db.decode(e, 3)!!.w == 8) { "mip 3 of $f" }
  }
  check(!db.put("TEX_DXT1", im, TexDb.PutOptions(TexDb.Format.DXT5, keepOldAttrs = true)))
  db.remove(db.find("tex_rgb565")!!)
  db.commit()
  db = TexDb(dir, "syn")
  check(db.entries.size == 4 && db.find("tex_dxt1")!!.name == "Tex_DXT1" && db.find("tex_dxt1")!!.attrs()["camnorm"] == "1")
  check(db.wasted > 0 && db.compact() > 0 && TexDb(dir, "syn").wasted == 0L)
  check(TexDb(dir, "syn").entries.all { it.offset < 0 || TexDb(dir, "syn").decode(it) != null })
  // RLE packs repeated units and unpacks to the same bytes.
  val flat = ByteArray(4096) { (it % 8).toByte() }
  val (m, packed) = TexDb.rleEncode(flat, 8)!!
  check(packed.size < flat.size && TexDb.rleDecode(packed, 0, packed.size, 8, m, flat.size).contentEquals(flat))
  println("synthetic texdb: ok")

  // IMG from scratch: add, case-insensitive replace (bigger), grow the directory, remove, rebuild.
  val img = File(out, "syn.img"); img.writeBytes("VER2".toByteArray() + ByteArray(4))
  var a = ImgArchive(img)
  repeat(100) { check(a.put("m$it.dff", ByteArray(3000 + it) { b -> (b + it).toByte() })) }
  check(!a.put("M5.DFF", ByteArray(9000) { 7 }))
  a.remove(a.find("m6.dff")!!)
  a.commit()
  a = ImgArchive(img)
  check(a.entries.size == 99 && a.find("m5.dff")!!.name == "m5.dff" && a.read(a.find("m5.dff")!!).copyOf(9000).all { it == 7.toByte() })
  check(a.read(a.find("m99.dff")!!).copyOf(3099).contentEquals(ByteArray(3099) { b -> (b + 99).toByte() }))
  check(a.rebuild() > 0); a = ImgArchive(img)
  check(a.usedSectors * ImgArchive.SECTOR == img.length() && a.read(a.find("m42.dff")!!).copyOf(3042).contentEquals(ByteArray(3042) { b -> (b + 42).toByte() }))
  println("synthetic img: ok")

  // OSW from scratch: append tracks, replace one, valid zip.
  val osw = File(out, "syn.osw"); osw.writeBytes(ByteArray(0)); File(osw.path + ".idx").writeBytes(ByteArray(4))
  val t1 = File(out, "a.mp3").apply { writeBytes(ByteArray(1000) { 1 }) }
  val t2 = File(out, "b.mp3").apply { writeBytes(ByteArray(2000) { 2 }) }
  var o = OswArchive(osw)
  o.rebuild(listOf("Track_001.mp3" to t1, "Track_002.mp3" to t2))
  o = OswArchive(osw)
  o.tracks[0].replacement = t2; o.rebuild()
  o = OswArchive(osw)
  check(o.tracks.size == 2 && o.read(o.tracks[0]).contentEquals(t2.readBytes()) && o.read(o.tracks[1]).contentEquals(t2.readBytes()))
  java.util.zip.ZipFile(osw).use { z -> check(z.getInputStream(z.getEntry("Track_002.mp3")).readBytes().contentEquals(t2.readBytes())) }
  println("synthetic audio: ok")

  // Adaptation: two models share one TXD, one has its own TXD with a clashing name, identical images share a name.
  val shared = Adapter.Dict("shared.txd", Rw.readTxd(txd("body" to 0xFFFF0000.toInt(), "glass" to 0x8000FF00.toInt(), "junk" to -1)))
  val own = Adapter.Dict("car2.txd", Rw.readTxd(txd("body" to 0xFF0000FF.toInt(), "glass2" to 0x8000FF00.toInt())))
  check(shared.textures.size == 3 && shared.textures[1].px[0] == 0x8000FF00.toInt()) { "txd decode" }
  val r = Adapter.adapt(listOf(Adapter.Model("car1.dff", dff("body", "glass", "vehiclegrunge256")), Adapter.Model("car2.dff", dff("body", "glass2"))), listOf(shared, own), "mod")
  val (c1, c2) = r.models
  check(c1.renamed.keys == setOf("body", "glass") && c1.missing == listOf("vehiclegrunge256"))
  check(c1.renamed["body"] != c2.renamed["body"]) { "car2 must take body from its own txd" }
  check(c1.renamed["glass"] == c2.renamed["glass2"]) { "identical images share one name" }
  check(r.textures.size == 3 && r.unused == listOf("shared.txd: junk"))
  check(Rw.dffTextures(c2.dff) == setOf(c2.renamed["body"], c2.renamed["glass2"]))
  check(r.textures.all { it.name.startsWith("mod_") && it.name.length == 10 })
  println("synthetic adaptation: ok")
}

fun realFiles(game: File, out: File) {
  val texdb = File(game, "texdb")
  for (n in TexDb.list(texdb)) {
    val db = TexDb(File(texdb, n), n)
    val bad = db.entries.count { it.offset >= 0 && db.decode(it) == null }
    println("texdb $n: ${db.entries.size} entries, undecodable $bad"); check(bad == 0)
  }
  val name = TexDb.list(texdb).first()
  val copy = File(out, name).apply { deleteRecursively(); mkdirs() }
  File(texdb, name).listFiles()!!.forEach { it.copyTo(File(copy, it.name)) }
  var db = TexDb(copy, name)
  val src = db.entries.first { it.offset >= 0 }
  val im = db.decode(src)!!
  check(!db.put(src.name.uppercase(), im, TexDb.PutOptions(TexDb.Format.DXT1)))
  db.commit()
  db = TexDb(copy, name)
  check(db.find(src.name)!!.name == src.name && db.compact() > 0)
  println("texdb write on a copy of $name: ok")

  val imgSrc = texdb.listFiles()!!.filter { it.name.endsWith(".img") }.minBy { it.length() }
  val img = File(out, "test.img"); imgSrc.copyTo(img, true)
  val orig = ImgArchive(imgSrc)
  var a = ImgArchive(img)
  val first = a.entries.first()
  check(!a.put(first.name.uppercase(), a.read(first) + ByteArray(5000)))
  repeat(200) { check(a.put("selftest_$it.dff", ByteArray(100))) }
  a.commit(); a.rebuild(); a = ImgArchive(img)
  for (e in orig.entries.drop(1)) check(a.read(a.find(e.name)!!).contentEquals(orig.read(e))) { "img data ${e.name}" }
  println("img ${imgSrc.name}: ok")

  val osw = File(game, "audio").walkTopDown().first { it.name.endsWith(".osw") && File(it.path + ".idx").exists() }
  val o2 = File(out, osw.name); osw.copyTo(o2, true); File(osw.path + ".idx").copyTo(File(o2.path + ".idx"), true)
  var o = OswArchive(o2); val before = o.tracks.map { o.read(it) }
  val rep = File(out, "rep.mp3").apply { writeBytes(before.last()) }
  o.tracks[0].replacement = rep; o.rebuild()
  o = OswArchive(o2)
  check(o.read(o.tracks[0]).contentEquals(rep.readBytes()))
  for (i in 1 until before.size) check(o.read(o.tracks[i]).contentEquals(before[i]))
  println("audio ${osw.name}: ok")

  val g = ImgArchive(File(texdb, "gta3.img"))
  var n = 0; var damaged = 0
  for (e in g.entries.filter { it.name.endsWith(".dff") }) {
    val raw = g.read(e); val b = raw.copyOf(Rw.streamLength(raw))
    val renamed = try { Rw.renameDff(b) { "${it}_x" } } catch (x: IllegalArgumentException) { damaged++; continue }
    check(Rw.dffTextures(renamed) == Rw.dffTextures(b).map { "${it}_x" }.toSet()) { "rename ${e.name}" }
    n++
  }
  println("dff: $n ok, $damaged damaged in source (skipped)")
}

fun main(args: Array<String>) {
  val out = File(args.getOrElse(1) { System.getProperty("java.io.tmpdir") + "/gut-core-test" }).apply { mkdirs() }
  synthetic(out)
  val game = args.getOrNull(0)?.let(::File)
  if (game != null && File(game, "texdb").isDirectory) realFiles(game, out) else println("no game folder: real-file checks skipped")
  println("ALL OK")
}
