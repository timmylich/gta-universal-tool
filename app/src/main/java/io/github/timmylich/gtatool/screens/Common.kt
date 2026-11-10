/** Pieces shared by several screens: change counter, file lists, import options form, selection header. */
package io.github.timmylich.gtatool.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.timmylich.gtatool.data.ImportOptions
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.lib.FileInfo
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.DialogButton
import io.github.timmylich.gtatool.ui.Divider
import io.github.timmylich.gtatool.ui.Empty
import io.github.timmylich.gtatool.ui.IconButton
import io.github.timmylich.gtatool.ui.Input
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Labeled
import io.github.timmylich.gtatool.ui.ListRow
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Screen
import io.github.timmylich.gtatool.ui.Segmented
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.Toggle
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bumped after every write, so screens below in the stack reload what they show. */
object Changes {
  var v by mutableIntStateOf(0)
  fun bump() { v++ }
}

/** Loads [load] on a background thread whenever [keys] or [Changes] change; null while loading. */
@Composable
fun <T> loaded(vararg keys: Any?, load: () -> T): T? {
  var value by remember(*keys) { mutableStateOf<T?>(null) }
  LaunchedEffect(*keys, Changes.v) {
    value = withContext(Dispatchers.IO) {
      runCatching(load).onFailure { alert(currentDict["common.error"], it.message ?: it.toString()) }.getOrNull()
    }
  }
  return value
}

/** A list of game files (databases, archives, audio packs) on one card. */
@Composable
fun FileListScreen(title: String, icon: String, hint: String? = null, load: (String) -> List<FileInfo>, sub: (FileInfo) -> String, open: (FileInfo) -> Unit) {
  val gameDir = Store.s.gameDir ?: ""
  val list = loaded(gameDir) { load(gameDir) }
  Screen(title) {
    if (hint != null) T(hint, dim = true, size = 13f)
    if (list == null) Loading()
    else Card {
      if (list.isEmpty()) Empty(L["common.none"])
      list.forEachIndexed { i, f ->
        if (i > 0) Divider()
        ListRow(f.name, sub(f), icon) { open(f) }
      }
    }
  }
}

/** Search field and a counter line above a list. */
@Composable
fun ListTop(q: String, onQ: (String) -> Unit, placeholder: String, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Input(q, onQ, placeholder = placeholder)
    content()
  }
}

/** Header actions while items are selected. */
@Composable
fun SelectionActions(onAll: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
  IconButton("checkall", L["common.all"], onClick = onAll)
  IconButton("download", L["common.export"], onClick = onExport)
  IconButton("trash", L["common.delete"], c.danger, onDelete)
  IconButton("close", L["common.close"], onClick = onClose)
}

fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

val baseName = { p: String -> p.substringAfterLast('/') }

/** Short path for messages (relative to shared storage). */
fun shortPath(p: String) = p.removePrefix(Gta.storageRoot)

private val FORMATS = listOf("auto", "DXT1", "DXT5", "RGBA8888", "RGB565", "RGBA4444")

/** Texture import options (format, size, mips, alpha attributes); [set] receives the changed copy. */
@Composable
fun ImportOptionsForm(o: ImportOptions, set: (ImportOptions) -> Unit) {
  Labeled(L["tex.format"], L["tex.formatHint"]) {
    Segmented(o.format, FORMATS.map { it to if (it == "auto") L["tex.auto"] else it }) { set(o.copy(format = it)) }
  }
  Labeled(L["tex.maxSize"], L["tex.maxSizeHint"]) {
    Segmented(o.maxSize, listOf(0, -1, 2048, 1024, 512, 256, 128).map { it to when (it) { 0 -> L["tex.original"]; -1 -> L["tex.asReplaced"]; else -> "$it" } }) { set(o.copy(maxSize = it)) }
  }
  Toggle(L["tex.pot"], o.pot, L["tex.potHint"]) { set(o.copy(pot = it)) }
  Toggle(L["tex.mips"], o.mips, L["tex.mipsHint"]) { set(o.copy(mips = it)) }
  Toggle(L["tex.rle"], o.rle, L["tex.rleHint"]) { set(o.copy(rle = it)) }
  Divider()
  Labeled(L["tex.alphamode"], L["tex.alphaHint"]) {
    Segmented(o.alphamode, listOf("auto", "0", "1", "2").map { it to if (it == "auto") L["tex.auto"] else it }) { set(o.copy(alphamode = it)) }
  }
  Toggle(L["tex.camnorm"], o.camnorm, L["tex.camnormHint"]) { set(o.copy(camnorm = it)) }
  Toggle(L["tex.streammode"], o.streammode, L["tex.streamHint"]) { set(o.copy(streammode = it)) }
  Labeled(L["tex.extra"], L["tex.extraHint"]) {
    Input(o.extra, { set(o.copy(extra = it)) }, placeholder = "detailtile=60", mono = true)
  }
}

/** Import options remembered between imports. */
fun saveImportOptions(o: ImportOptions) = Store.update { copy(texImport = o) }

/** Asks before adding entries that are not in the archive yet. False when the user cancels. */
suspend fun confirmNewEntries(path: String, names: List<String>): Boolean {
  val L = currentDict
  val missing = withContext(Dispatchers.IO) { Gta.imgMissing(path, names) }
  if (missing.isEmpty() || Store.s.imgWarned) return true
  val shown = missing.take(15).joinToString("\n") + if (missing.size > 15) "\n… +${missing.size - 15}" else ""
  val r = CompletableDeferred<Boolean>()
  alert(L["img.newWarnTitle"], L.t("img.newWarn", shown), listOf(
    DialogButton(L["common.cancel"], "cancel") { r.complete(false) },
    DialogButton(L["img.dontWarn"], "destructive") { Store.update { copy(imgWarned = true) }; r.complete(true) },
    DialogButton(L["img.addAnyway"], "destructive") { r.complete(true) },
  ))
  return r.await()
}

/** "Errors" details appended to a result message. */
fun withFailures(msg: String, failed: List<String>) = if (failed.isEmpty()) msg else msg + "\n\n" + failed.joinToString("\n")
