package io.github.timmylich.gtatool.core

/**
 * PC mod → mobile. On PC every model has its own TXD; on mobile all textures live in shared texdb caches where
 * equal names clash. Every texture used by the given models gets a unique "<prefix>_<random>" name, the DFFs are
 * rewritten to match and textures no model uses are dropped.
 *
 * Several models and dictionaries can be adapted together: one TXD may serve many DFFs (each texture is renamed
 * once and shared), and a texture is looked up first in the TXD with the model's own name ("car.dff" → "car.txd"),
 * then in the other dictionaries in the given order. Identical images from different TXDs share one name.
 */
object Adapter {
  class Model(val name: String, val dff: ByteArray)
  class Dict(val name: String, val textures: List<Rw.Tex>)

  class ModelResult(val name: String, val dff: ByteArray, val renamed: Map<String, String>, val missing: List<String>)
  class Result(val models: List<ModelResult>, val textures: List<Rw.Tex>, val unused: List<String>)

  private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

  fun randomName(prefix: String, taken: Set<String>, rnd: java.util.Random = java.util.Random()): String {
    val p = cleanPrefix(prefix)
    while (true) {
      val s = (if (p.isEmpty()) "" else "${p}_") + (1..6).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
      if (s.lowercase() !in taken) return s
    }
  }

  fun cleanPrefix(prefix: String) = prefix.filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }.take(14)

  private fun baseName(n: String) = n.substringAfterLast('/').substringBeforeLast('.').lowercase()

  /** Search order of dictionaries for a model: same base name first, then the rest as given. */
  private fun order(model: String, dicts: List<Dict>): List<Int> {
    val own = dicts.indices.filter { baseName(dicts[it].name) == baseName(model) }
    return own + dicts.indices.filter { it !in own }
  }

  /** Which dictionary (index) provides each texture a model uses; null = not found in any. */
  fun resolve(models: List<Model>, dicts: List<Dict>): List<Map<String, Int?>> {
    val index = dicts.map { d -> d.textures.associateBy { it.name.lowercase() } }
    return models.map { m ->
      val ord = order(m.name, dicts)
      Rw.dffTextures(m.dff).associateWith { t -> ord.firstOrNull { index[it].containsKey(t.lowercase()) } }
    }
  }

  fun adapt(models: List<Model>, dicts: List<Dict>, prefix: String, taken: Set<String> = emptySet()): Result {
    val index = dicts.map { d -> d.textures.associateBy { it.name.lowercase() } }
    val sources = resolve(models, dicts)
    val busy = taken.map { it.lowercase() }.toHashSet()
    val newName = HashMap<Pair<Int, String>, String>() // (dict, old lower) → new
    val byContent = HashMap<Int, MutableList<Rw.Tex>>() // pixel hash → already named textures
    val out = ArrayList<Rw.Tex>()
    val used = HashSet<Pair<Int, String>>()
    val results = models.mapIndexed { mi, m ->
      val renamed = LinkedHashMap<String, String>()
      val missing = ArrayList<String>()
      for ((tex, di) in sources[mi]) {
        if (di == null) { missing += tex; continue }
        val key = di to tex.lowercase()
        used += key
        val n = newName.getOrPut(key) {
          val t = index[di].getValue(tex.lowercase())
          val same = byContent.getOrPut(t.px.contentHashCode()) { ArrayList() }
          same.firstOrNull { it.w == t.w && it.h == t.h && it.px.contentEquals(t.px) }?.name
            ?: randomName(prefix, busy).also { busy += it.lowercase(); Rw.Tex(it, "", t.w, t.h, t.px).let { x -> same += x; out += x } }
        }
        renamed[tex] = n
      }
      val lower = renamed.mapKeys { it.key.lowercase() }
      ModelResult(m.name, Rw.renameDff(m.dff) { lower[it.lowercase()] }, renamed, missing)
    }
    val unused = dicts.flatMapIndexed { di, d -> d.textures.filter { (di to it.name.lowercase()) !in used }.map { "${d.name}: ${it.name}" } }
    return Result(results, out, unused)
  }
}
