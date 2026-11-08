/**
 * Built-in file browser over shared storage (the app has "All files access", so no SAF copies are needed).
 * `pickFiles(exts, multi)` / `pickDir()` return absolute paths, or null when cancelled.
 */
package io.github.timmylich.gtatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.DirItem
import io.github.timmylich.gtatool.lib.Gta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

private class PickRequest(val dirMode: Boolean, val exts: List<String>?, val multi: Boolean, val start: String?) {
  val result = CompletableDeferred<List<String>?>()
}

private object Picker {
  var req by mutableStateOf<PickRequest?>(null)
}

private suspend fun request(r: PickRequest): List<String>? {
  Picker.req = r
  return r.result.await()
}

suspend fun pickFiles(exts: List<String>? = null, multi: Boolean = false, start: String? = null): List<String>? =
  request(PickRequest(false, exts, multi, start))

suspend fun pickDir(start: String? = null): String? = request(PickRequest(true, null, false, start))?.firstOrNull()

private val NAME_ORDER = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Natural-ish order: folders first, then names (digits compared by value). */
private fun sortKey(a: DirItem, b: DirItem): Int {
  if (a.dir != b.dir) return if (a.dir) -1 else 1
  val na = Regex("""\d+""").find(a.name); val nb = Regex("""\d+""").find(b.name)
  if (na != null && nb != null && na.range.first == nb.range.first && a.name.take(na.range.first) == b.name.take(nb.range.first)) {
    val x = na.value.toBigInteger().compareTo(nb.value.toBigInteger())
    if (x != 0) return x
  }
  return NAME_ORDER.compare(a.name, b.name)
}

@Composable
fun FilePickerHost() {
  val req = Picker.req ?: return
  val root = Gta.storageRoot
  var dir by remember(req) { mutableStateOf(req.start ?: Store.s.lastDir?.takeIf { java.io.File(it).isDirectory } ?: root) }
  var items by remember(req) { mutableStateOf<List<DirItem>>(emptyList()) }
  var sel by remember(req) { mutableStateOf(setOf<String>()) }

  LaunchedEffect(req, dir) {
    val ext = req.exts?.map { it.lowercase() }
    items = withContext(Dispatchers.IO) {
      Gta.listDir(dir).filter { i -> !i.name.startsWith(".") && (i.dir || (!req.dirMode && (ext == null || ext.any { i.name.lowercase().endsWith(it) }))) }
        .sortedWith(::sortKey)
    }
  }

  val finish = { v: List<String>? ->
    if (v != null) Store.update { copy(lastDir = dir) }
    Picker.req = null
    req.result.complete(v)
  }
  val up = { if (dir == root || dir == "/") finish(null) else dir = dir.substringBeforeLast('/').ifEmpty { "/" } }
  BackHandler { up() }

  Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      IconButton("close", L["common.close"]) { finish(null) }
      IconButton("up", L["browser.up"]) { up() }
      T(dir.removePrefix(root).ifEmpty { "/" }, Modifier.weight(1f), dim = true, size = 13f, maxLines = 2)
    }
    Box(Modifier.weight(1f)) {
      if (items.isEmpty()) Empty(L["browser.empty"])
      LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.name }) { item ->
          val p = "$dir/${item.name}"
          val on = p in sel
          Row(
            Modifier.fillMaxWidth().tap(scaleTo = 0.98f) {
              when {
                item.dir -> dir = p
                !req.multi -> finish(listOf(p))
                else -> sel = if (on) sel - p else sel + p
              }
            }.background(if (on) c.accentSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Icon(if (item.dir) "folder" else "file", 22.dp, if (item.dir) c.accent else c.textDim)
            Column(Modifier.weight(1f)) {
              T(item.name, maxLines = 1)
              if (!item.dir) T(bytes(item.size), dim = true, size = 12f)
            }
            if (on) Icon("check", 20.dp, c.accent)
          }
        }
      }
    }
    if (req.dirMode) Button(L["browser.useFolder"], Modifier.fillMaxWidth().padding(12.dp)) { finish(listOf(dir)) }
    else if (req.multi) Button(L.t("browser.pickN", sel.size), Modifier.fillMaxWidth().padding(12.dp), enabled = sel.isNotEmpty()) { finish(sel.toList()) }
  }
}
