/**
 * Update check against the latest GitHub release, once per app start. "Later" (or closing the dialog)
 * hides it for 3 days. Store installs are skipped: the store updates them, and some stores forbid
 * pointing users to updates elsewhere (F-Droid builds also use another signing key).
 */
package io.github.timmylich.gtatool.screens

import android.content.Context
import android.os.Build
import io.github.timmylich.gtatool.BuildConfig
import io.github.timmylich.gtatool.REPO_URL
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.ui.DialogButton
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_API = "https://api.github.com/repos/timmylich/gta-universal-tool/releases/latest"
private const val SNOOZE_MS = 3 * 24 * 60 * 60 * 1000L
private var checked = false

/** "v0.2.1" vs "0.2.0" → true. Non-numeric parts count as 0. */
internal fun isNewer(tag: String, current: String): Boolean {
  val parse = { v: String -> v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 } }
  val a = parse(tag)
  val b = parse(current)
  for (i in 0 until maxOf(a.size, b.size)) {
    val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
    if (d != 0) return d > 0
  }
  return false
}

/** Installed from an APK file (package installer, adb), not by a store app. */
fun sideloaded(ctx: Context): Boolean {
  val pm = ctx.packageManager
  val src = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(ctx.packageName).installingPackageName
    else @Suppress("DEPRECATION") pm.getInstallerPackageName(ctx.packageName)
  }.getOrNull()
  return src == null || "packageinstaller" in src || src == "com.android.shell"
}

private data class Release(val tag: String, val page: String, val apk: String?)

/** Latest release, or null on any network/parse error. */
private fun latestRelease(): Release? = runCatching {
  val c = URL(LATEST_API).openConnection() as HttpURLConnection
  c.connectTimeout = 5000
  c.readTimeout = 5000
  c.setRequestProperty("Accept", "application/vnd.github+json")
  try {
    val o = Json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
    val apk = o["assets"]?.jsonArray?.firstNotNullOfOrNull { a ->
      a.jsonObject["browser_download_url"]?.jsonPrimitive?.content?.takeIf { it.endsWith(".apk") }
    }
    Release(o["tag_name"]!!.jsonPrimitive.content, o["html_url"]!!.jsonPrimitive.content, apk)
  } finally {
    c.disconnect()
  }
}.getOrNull()

/** [manual]: from Settings; ignores "later" and reports "no update" / errors instead of staying silent. */
suspend fun checkForUpdate(ctx: Context, manual: Boolean = false) {
  if (!manual && (checked || System.currentTimeMillis() < Store.s.updateSnoozedUntil || !sideloaded(ctx))) return
  checked = true
  val d = currentDict
  val r = if (manual) work { latestRelease() ?: error(d["update.failed"]) } ?: return else withContext(Dispatchers.IO) { latestRelease() } ?: return
  // Links come from the network: open only our own release pages and files.
  if (!isNewer(r.tag, BuildConfig.VERSION_NAME) || !r.page.startsWith("$REPO_URL/releases/")) {
    if (manual) alert(d["update.latest"], d.t("update.latestText", BuildConfig.VERSION_NAME))
    return
  }
  val apk = r.apk?.takeIf { it.startsWith("$REPO_URL/releases/download/") }
  alert(d["update.title"], d.t("update.text", r.tag.removePrefix("v"), BuildConfig.VERSION_NAME), listOfNotNull(
    apk?.let { DialogButton(d["update.download"]) { openUrl(ctx, it) } },
    DialogButton(d["update.page"], if (apk == null) "default" else "soft") { openUrl(ctx, r.page) },
    DialogButton(d["update.later"], "cancel") { Store.update { copy(updateSnoozedUntil = System.currentTimeMillis() + SNOOZE_MS) } },
  ))
}
