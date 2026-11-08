/**
 * UI strings from assets/i18n_{ru,en}.json (same keys in both). Plain keys: `L["common.save"]`;
 * templates: `L.t("common.exportedTo", n, dir)` fills {0}, {1}…; plurals: `L.n("tex.textures", n)`
 * picks a form from an array (ru: one/few/many, en: one/other).
 */
package io.github.timmylich.gtatool.i18n

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.Locale

enum class Lang(val code: String) { Ru("ru"), En("en") }

/** Languages whose speakers most likely prefer Russian over English among the ones we ship. */
private val RU_LIKE = setOf("ru", "uk", "be", "kk", "ky", "uz", "tg", "hy", "az", "ka")

/** [setting]: "system" | "ru" | "en" (the saved settings value). */
fun resolveLang(setting: String): Lang = when (setting) {
  "ru" -> Lang.Ru
  "en" -> Lang.En
  else -> if (Locale.getDefault().language in RU_LIKE) Lang.Ru else Lang.En
}

class Dict(val lang: Lang, private val root: JsonObject) {
  private fun node(path: String): JsonElement? {
    var cur: JsonElement = root
    for (part in path.split('.')) cur = (cur as? JsonObject)?.get(part) ?: return null
    return cur
  }

  /** Missing keys show the key itself, so they are easy to spot. */
  operator fun get(path: String): String = (node(path) as? JsonPrimitive)?.contentOrNull ?: path

  fun t(path: String, vararg args: Any?): String = fill(get(path), args)

  /** Plural form of [path] for [count]; {0} is the count. */
  fun n(path: String, count: Int): String {
    val forms = (node(path) as? JsonArray)?.map { (it as JsonPrimitive).content } ?: return path
    return fill(forms[pluralIndex(lang, count).coerceAtMost(forms.size - 1)], arrayOf(count))
  }

  /** "Added: 3\nReplaced: 1…" from (res.* key, value, shown even when 0). */
  fun result(vararg lines: Triple<String, Int, Boolean>): String =
    lines.filter { it.third || it.second > 0 }.joinToString("\n") { get("res." + it.first) + ": " + it.second }

  companion object {
    private val PLACEHOLDER = Regex("""\{(\d+)\}""")
    private fun fill(template: String, args: Array<out Any?>) = PLACEHOLDER.replace(template) { args.getOrNull(it.groupValues[1].toInt()).toString() }

    fun pluralIndex(lang: Lang, n: Int): Int {
      if (lang == Lang.En) return if (n == 1) 0 else 1
      val a = Math.abs(n) % 100
      val b = a % 10
      return when {
        a in 11..19 -> 2
        b == 1 -> 0
        b in 2..4 -> 1
        else -> 2
      }
    }

    private val cache = HashMap<Lang, Dict>()

    fun of(context: Context, lang: Lang): Dict = synchronized(cache) {
      cache.getOrPut(lang) {
        val text = context.assets.open("i18n_${lang.code}.json").bufferedReader().use { it.readText() }
        Dict(lang, Json.parseToJsonElement(text).jsonObject)
      }
    }
  }
}

/** 1536 → "1.5 KB" */
fun bytes(n: Long): String {
  if (n < 1024) return "$n B"
  val u = listOf("KB", "MB", "GB")
  var v = n.toDouble()
  var i = -1
  do { v /= 1024; i++ } while (v >= 1024 && i < u.size - 1)
  return String.format(Locale.US, if (v < 10) "%.1f %s" else "%.0f %s", v, u[i])
}
