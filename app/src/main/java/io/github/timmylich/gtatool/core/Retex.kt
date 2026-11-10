package io.github.timmylich.gtatool.core

/**
 * Plans a retexture (PC .txd files → mobile texture databases) that keeps per-TXD differences.
 *
 * How the mobile caches store PC textures: all TXDs are merged into one database, so a name used by
 * several TXDs with different pictures is stored once per TXD as "<name>_<txd>" (e.g. des_dirt1_cs_mountain),
 * and TXDs with the same picture are aliases to one stored copy ("des_dirt1_cs_town" "affiliate=des_dirt1_cs_mountain").
 * The game looks up "<name>_<txd>" first, then "<name>". Writing only "<name>" (the old retexture mode) leaves
 * every "<name>_<txd>" texture unchanged.
 *
 * The plan for each database:
 *  - every PC texture finds its lookup entry ("<name>_<txd>" if present, else "<name>") and the stored copy
 *    that entry resolves to through aliases;
 *  - textures that resolve to the same stored copy are grouped by picture: the main picture (the one whose
 *    lookup entry is the stored copy itself, else the most common one) overwrites the stored copy, so all its
 *    aliases follow; every other picture gets its own "<name>_<txd>" entry, and TXDs sharing that picture
 *    become aliases of it (no duplicate data);
 *  - textures no database has are added to [fallback] as "<name>" (or "<name>_<txd>" when the name is taken by
 *    a different picture), or reported as missing.
 */
object Retex {
  /** One texture of the pack: [txd] is the TXD name without extension, [hash] identifies the picture. */
  class Item(val txd: String, val name: String, val hash: Long) {
    val key get() = "${txd.lowercase()}/${name.lowercase()}"
  }

  /** Write [item]'s picture as [target] into [db]; [like] is the entry whose attributes and size a new entry inherits. */
  class Write(val db: TexDb, val target: String, val item: Item, val like: TexDb.Entry?)

  class Alias(val db: TexDb, val name: String, val target: String)

  class Plan(val writes: List<Write>, val aliases: List<Alias>, val missing: List<Item>) {
    /** Texture names that got a TXD-specific copy because TXDs disagree. */
    val split get() = writes.filter { it.like != null && !it.target.equals(it.like.name, true) }.size
  }

  private const val MAX_HOPS = 8

  /** Follows aliases from [name] to the entry that has data (null when the chain is broken or too long). */
  fun resolve(db: TexDb, name: String): TexDb.Entry? {
    var e = db.find(name) ?: return null
    repeat(MAX_HOPS) {
      if (e.offset >= 0) return e
      val next = e.attrs()["affiliate"] ?: return null
      e = db.find(next) ?: return null
    }
    return null
  }

  private fun lookup(db: TexDb, it: Item): String? = "${it.name}_${it.txd}".let { v -> if (db.find(v) != null) v else null } ?: it.name.takeIf { db.find(it) != null }

  fun plan(dbs: List<TexDb>, items: List<Item>, fallback: TexDb?): Plan {
    val writes = ArrayList<Write>(); val aliases = ArrayList<Alias>(); val missing = ArrayList<Item>()
    // (db, stored copy) → (item, its lookup name)
    val groups = LinkedHashMap<Pair<TexDb, String>, MutableList<Pair<Item, String>>>()
    val unplaced = ArrayList<Item>()
    for (it in items.distinctBy { it.key }) {
      var placed = false
      for (db in dbs) {
        val l = lookup(db, it) ?: continue
        val stored = resolve(db, l) ?: continue
        groups.getOrPut(db to stored.name) { ArrayList() } += it to l
        placed = true
      }
      if (!placed) unplaced += it
    }
    for ((k, members) in groups) {
      val (db, stored) = k
      val like = db.find(stored)
      val clusters = members.groupBy { it.first.hash }.values.sortedByDescending { it.size }
      val owner = clusters.filter { c -> c.any { it.second.equals(stored, true) } }.maxByOrNull { it.size } ?: clusters.first()
      writes += Write(db, stored, owner.first().first, like)
      for (c in clusters) if (c !== owner) {
        val names = c.map { (it, l) -> if (l.equals(it.name, true)) "${it.name}_${it.txd}" else l }
        writes += Write(db, names.first(), c.first().first, like)
        for (n in names.drop(1)) aliases += Alias(db, n, names.first())
      }
    }
    if (fallback == null) missing += unplaced
    else for ((_, c) in unplaced.groupBy { it.name.lowercase() }) {
      // shortcut: the name may already be taken in the fallback by an unrelated texture; then it is replaced, as before.
      val byPic = c.groupBy { it.hash }.values.sortedByDescending { it.size }
      writes += Write(fallback, byPic.first().first().name, byPic.first().first(), null)
      for (p in byPic.drop(1)) {
        val names = p.map { "${it.name}_${it.txd}" }
        writes += Write(fallback, names.first(), p.first(), null)
        for (n in names.drop(1)) aliases += Alias(fallback, n, names.first())
      }
    }
    return Plan(writes, aliases, missing)
  }

  /** Picture hash for grouping identical textures across TXDs. */
  fun hash(t: Rw.Tex): Long = (t.w.toLong() shl 48) xor (t.h.toLong() shl 32) xor (t.px.contentHashCode().toLong() and 0xffffffffL)

  /**
   * True when [px] looks like the stored texture [e] (same picture, allowing for DXT loss), compared at about
   * 32 px. Lets a whole-map pack skip the textures it does not change.
   */
  fun same(db: TexDb, e: TexDb.Entry, px: IntArray, w: Int, h: Int): Boolean {
    var lv = 0; var ew = e.width; var eh = e.height
    if (e.mips) while (maxOf(ew, eh) / 2 >= 32) { ew /= 2; eh /= 2; lv++ }
    val old = db.decode(e, lv) ?: return false
    val s = minOf(32, old.w, old.h)
    val a = Pixels.resize(old.px, old.w, old.h, s, s)
    val b = Pixels.resize(px, w, h, s, s)
    var sum = 0L; var worst = 0
    for (i in a.indices) for (sh in 0..24 step 8) {
      val d = Math.abs((a[i] ushr sh and 255) - (b[i] ushr sh and 255))
      sum += d; if (d > worst) worst = d
    }
    return sum < a.size * 4L * 6 && worst < 64
  }
}
