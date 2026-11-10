/**
 * Texture databases (texdb/<name>/): the list, one database (virtualized list with lazy previews,
 * search, attribute editing, export / replace / delete, multi-select by long press, .dat rebuild)
 * and the import screen.
 */
package io.github.timmylich.gtatool.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.lib.TexEntry
import io.github.timmylich.gtatool.ui.AppSheet
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.Frame
import io.github.timmylich.gtatool.ui.IconButton
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Labeled
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Nav
import io.github.timmylich.gtatool.ui.Route
import io.github.timmylich.gtatool.ui.Screen
import io.github.timmylich.gtatool.ui.Segmented
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.Toggle
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.ask
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.pickFiles
import io.github.timmylich.gtatool.ui.tap
import io.github.timmylich.gtatool.ui.work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMAGE_EXTS = listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp")

// A few decodes at a time keep fast scrolling from queueing hundreds of reads.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val previews = Dispatchers.IO.limitedParallelism(3)

@Composable
fun Textures() = FileListScreen(L["tex.title"], "database", load = Gta::texdbList, sub = { bytes(it.size) }) { Nav.push(Route.TexDb(it.path)) }

@Composable
private fun Thumb(path: String, e: TexEntry, size: Int) {
  val img by produceState<ImageBitmap?>(null, path, e.name, e.key, size) {
    value = withContext(previews) { runCatching { Gta.texdbPreview(path, e.name, size)?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }.getOrNull() }
  }
  val px = if (size == 64) 48.dp else 220.dp
  Box(Modifier.size(px).clip(RoundedCornerShape(10.dp)).background(c.cardAlt)) {
    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
  }
}

private val ATTR = Regex("""[ "](\w+)=([^\s"]+)""")
private fun attrsOf(line: String) = ATTR.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }

private fun info(e: TexEntry, aliasLabel: String) =
  if (e.fmt == "alias") "$aliasLabel: ${attrsOf(e.line)["affiliate"] ?: e.line.split('"').getOrNull(3) ?: ""}"
  else "${e.w}×${e.h} · ${e.fmt}${if (e.mips) " · mips" else ""}"

@Composable
fun TexDbScreen(path: String) {
  val name = baseName(path)
  val data = loaded(path) { Gta.texdbOpen(path) }
  var q by remember { mutableStateOf("") }
  var sel by remember { mutableStateOf<Set<String>?>(null) }
  var open by remember { mutableStateOf<TexEntry?>(null) }
  val query = q.trim().lowercase()
  val list = remember(data, query) { data?.entries?.let { all -> if (query.isEmpty()) all else all.filter { query in it.name.lowercase() } } ?: emptyList() }

  val startImport = { fixed: String? ->
    // The sheet is its own window: close it, or the file browser opens underneath.
    open = null
    act {
      val files = pickFiles(IMAGE_EXTS, multi = fixed == null) ?: return@act
      if (files.isEmpty()) return@act
      Nav.push(Route.TexImport(path, files.map { it to (fixed ?: baseName(it).substringBeforeLast('.')) }))
    }
  }
  val export = { names: List<String> ->
    act {
      val dir = Gta.appDir("export/$name")
      val n = work { Gta.texdbExport(path, names, dir) } ?: return@act
      alert(currentDict["common.done"], currentDict.t("common.exportedTo", n, shortPath(dir)))
    }
  }
  val remove = { names: List<String> ->
    act {
      val d = currentDict
      if (!ask(d["common.delete"], d.t("tex.removeAsk", d.n("tex.textures", names.size)), d["common.delete"], true)) return@act
      work { Gta.texdbRemove(path, names) }
      open = null
      sel = null
      Changes.bump()
    }
  }
  val compact = {
    act {
      val d = currentDict
      if (!ask(d["tex.compact"], d["tex.compactAsk"], d["tex.compact"])) return@act
      val saved = work { Gta.texdbCompact(path) }
      if (saved != null) alert(d["common.done"], d.t("tex.compactDone", bytes(saved)))
      Changes.bump()
    }
  }

  val s = sel
  Frame(if (s != null) L.t("common.selected", s.size) else name, actions = {
    if (s != null) SelectionActions(
      onAll = { sel = list.filter { it.fmt != "alias" }.map { it.name }.toSet() },
      onExport = { export(s.toList()) },
      onDelete = { remove(s.toList()) },
      onClose = { sel = null },
    ) else IconButton("plus", L["common.import"]) { startImport(null) }
  }) {
    if (data == null) return@Frame Loading()
    ListTop(q, { q = it }, L["tex.filter"]) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T("${L.n("tex.textures", list.size)} · .dat ${bytes(data.datSize)}" + if (data.wasted > 0) " · " + L.t("tex.wasted", bytes(data.wasted)) else "",
          Modifier.weight(1f), dim = true, size = 12.5f)
        if (data.wasted > 0) Button(L["tex.compact"], kind = "soft", icon = "recycle", small = true, onClick = compact)
      }
    }
    LazyColumn(Modifier.fillMaxSize()) {
      items(list, key = { it.name }) { e ->
        val on = s?.contains(e.name) == true
        Row(
          Modifier.fillMaxWidth()
            .tap(scaleTo = 0.98f, onLongClick = { sel = (s ?: emptySet()).toggle(e.name).ifEmpty { null } }) {
              if (s != null) sel = s.toggle(e.name).ifEmpty { null } else open = e
            }
            .background(if (on) c.accentSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Thumb(path, e, 64)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(e.name, semi = true, maxLines = 1)
            T(info(e, L["tex.alias"]), dim = true, size = 12f, maxLines = 1)
          }
        }
      }
      item { Box(Modifier.navigationBarsPadding().padding(8.dp)) }
    }
  }

  open?.let { e ->
    Details(path, e, onClose = { open = null }, onExport = { export(listOf(e.name)) }, onReplace = { startImport(e.name) }, onRemove = { remove(listOf(e.name)) })
  }
}

@Composable
private fun Details(path: String, e: TexEntry, onClose: () -> Unit, onExport: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
  val a0 = remember(e) { attrsOf(e.line) }
  var alphamode by remember(e) { mutableStateOf(a0["alphamode"] ?: "0") }
  var camnorm by remember(e) { mutableStateOf(a0["camnorm"] == "1") }
  var streammode by remember(e) { mutableStateOf(a0["streammode"] == "1") }
  var line by remember(e) { mutableStateOf(e.line) }
  val saved = attrsOf(line)
  val changed = alphamode != (saved["alphamode"] ?: "0") || camnorm != (saved["camnorm"] == "1") || streammode != (saved["streammode"] == "1")
  val alias = e.fmt == "alias"
  AppSheet(onClose) {
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Thumb(path, e, 256)
        T(e.name, size = 17f, semi = true, center = true)
        if (!alias) T(info(e, ""), dim = true, size = 13f)
      }
      if (!alias) {
        Labeled(L["tex.alphamode"], L["tex.alphaHint"]) { Segmented(alphamode, listOf("0", "1", "2").map { it to it }) { alphamode = it } }
        Toggle(L["tex.camnorm"], camnorm, L["tex.camnormHint"]) { camnorm = it }
        Toggle(L["tex.streammode"], streammode, L["tex.streamHint"]) { streammode = it }
        if (changed) Button(L["tex.saveAttrs"], Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          act {
            val changes = mapOf("alphamode" to alphamode, "camnorm" to if (camnorm) "1" else "0", "streammode" to if (streammode) "1" else "0")
            work { Gta.texdbSetAttrs(path, e.name, changes) }?.let { line = it; Changes.bump() }
          }
        }
      }
      T(line, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), dim = true, mono = true, size = 11.5f)
      Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!alias) Button(L["tex.exportPng"], Modifier.weight(1f), "soft", "download", small = true, onClick = onExport)
        Button(L["tex.replacePng"], Modifier.weight(1f), "soft", small = true, onClick = onReplace)
      }
      Button(L["common.delete"], Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), "danger", "trash", small = true, onClick = onRemove)
    }
  }
}

@Composable
fun TexImport(path: String, items: List<Pair<String, String>>) {
  var o by remember { mutableStateOf(Store.s.texImport) }
  val set = { v: io.github.timmylich.gtatool.data.ImportOptions -> o = v; saveImportOptions(v) }
  Screen(L["tex.importTitle"], bottom = {
    Button(L.t("tex.run", items.size), Modifier.fillMaxWidth()) {
      act {
        val r = work { Gta.texdbImport(path, items, o) } ?: return@act
        val d = currentDict
        Changes.bump()
        Nav.pop()
        alert(d["common.done"], withFailures(d.result(Triple("added", r.added.size, true), Triple("replaced", r.replaced.size, true), Triple("skipped", r.skipped.size, false), Triple("errors", r.failed.size, false)), r.failed))
      }
    }
  }) {
    Card(vertical = 6.dp) {
      Labeled("${L["tex.names"]} → ${baseName(path)}", L["tex.nameHint"]) {
        for ((_, n) in items.take(50)) T(n, mono = true, size = 12.5f, maxLines = 1)
        if (items.size > 50) T("… +${items.size - 50}", dim = true)
      }
      Toggle(L["tex.overwrite"], o.overwrite, L["tex.overwriteHint"]) { set(o.copy(overwrite = it)) }
    }
    Card(vertical = 6.dp) { ImportOptionsForm(o, set) }
  }
}
