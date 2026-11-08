/**
 * App settings in one small JSON file (files/settings.json: the same file and format the React Native
 * version wrote, so settings survive the update). Read through `Store.s` (Compose state), change
 * through `Store.update { copy(...) }`.
 */
package io.github.timmylich.gtatool.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

@Serializable
data class ImportOptions(
  /** auto | DXT1 | DXT5 | RGBA8888 | RGB565 | RGBA4444 */
  val format: String = "auto",
  val mips: Boolean = true,
  /** RLE-pack the data like the original databases (smaller .dat). */
  val rle: Boolean = true,
  /** Longest side limit, 0 = keep. */
  val maxSize: Int = 0,
  /** Resize to power-of-two sides. */
  val pot: Boolean = true,
  /** auto | 0 | 1 | 2 */
  val alphamode: String = "auto",
  val camnorm: Boolean = false,
  val streammode: Boolean = false,
  /** Extra "key=value" attributes, space separated. */
  val extra: String = "",
  val overwrite: Boolean = true,
)

@Serializable
data class Settings(
  val gameDir: String? = null,
  /** system | ru | en */
  val language: String = "system",
  /** system | light | dark */
  val theme: String = "system",
  /** Copy each game file to /sdcard/GTAUniversalTool/backup before its first change. */
  val backup: Boolean = true,
  val prefix: String = "mod",
  val texImport: ImportOptions = ImportOptions(),
  val lastDir: String? = null,
  val installedAt: Long = System.currentTimeMillis(),
  val donationShown: List<Int> = emptyList(),
  /** Hide the "new entries may crash the game" warning for IMG additions. */
  val imgWarned: Boolean = false,
)

object Store {
  internal val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true; isLenient = true }
  private val writer = Executors.newSingleThreadExecutor()
  private lateinit var file: File

  var s by mutableStateOf(Settings())
    private set

  fun init(context: Context) {
    file = File(context.filesDir, "settings.json")
    // Corrupt or missing settings: start fresh rather than crash.
    s = runCatching { json.decodeFromString<Settings>(file.readText()) }.getOrNull() ?: Settings().also(::save)
  }

  fun update(f: Settings.() -> Settings) {
    val next = s.f()
    if (next == s) return
    s = next
    save(next)
  }

  private fun save(v: Settings) = writer.execute {
    val tmp = File(file.path + ".tmp")
    tmp.writeText(json.encodeToString(Settings.serializer(), v))
    tmp.renameTo(file)
  }
}
