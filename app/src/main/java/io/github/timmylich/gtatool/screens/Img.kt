/**
 * IMG archives: the list and one archive (search, add/replace files, where a case-insensitive match keeps
 * the stored name; a warning before adding new entries; export, delete, rebuild).
 */
package io.github.timmylich.gtatool.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.DialogButton
import io.github.timmylich.gtatool.ui.Frame
import io.github.timmylich.gtatool.ui.IconButton
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Nav
import io.github.timmylich.gtatool.ui.Route
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.ask
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.pickFiles
import io.github.timmylich.gtatool.ui.tap
import io.github.timmylich.gtatool.ui.work

@Composable
fun ImgList() {
  val gameDir = Store.s.gameDir ?: ""
  FileListScreen(L["img.title"], "box", load = Gta::imgList, sub = { "${bytes(it.size)} · ${it.path.removePrefix(gameDir)}" }) { Nav.push(Route.ImgView(it.path)) }
}

@Composable
fun ImgView(path: String) {
  val data = loaded(path) { Gta.imgOpen(path) }
  var q by remember { mutableStateOf("") }
  var sel by remember { mutableStateOf<Set<String>?>(null) }
  val query = q.trim().lowercase()
  val list = remember(data, query) { data?.entries?.let { all -> if (query.isEmpty()) all else all.filter { query in it.name.lowercase() } } ?: emptyList() }

  val add = {
    act {
      val files = pickFiles(multi = true)?.takeIf { it.isNotEmpty() } ?: return@act
      if (!confirmNewEntries(path, files.map(baseName))) return@act
      val r = work { Gta.imgImport(path, files) }
      Changes.bump()
      if (r != null) {
        val d = currentDict
        alert(d["common.done"], withFailures(d.result(Triple("added", r.added.size, true), Triple("replaced", r.replaced.size, true), Triple("errors", r.failed.size, false)), r.failed))
      }
    }
  }
  val export = { names: List<String> ->
    act {
      val dir = Gta.appDir("export/${baseName(path)}")
      val n = work { Gta.imgExport(path, names, dir) } ?: return@act
      alert(currentDict["common.done"], currentDict.t("common.exportedTo", n, shortPath(dir)))
    }
  }
  val remove = { names: List<String> ->
    act {
      val d = currentDict
      if (!ask(d["common.delete"], d.t("img.removeAsk", d.n("common.files", names.size)), d["common.delete"], true)) return@act
      work { Gta.imgRemove(path, names) }
      sel = null
      Changes.bump()
    }
  }
  val rebuild = {
    act {
      val d = currentDict
      if (!ask(d["img.rebuild"], d["img.rebuildAsk"], d["img.rebuild"])) return@act
      val saved = work { Gta.imgRebuild(path) }
      if (saved != null) alert(d["common.done"], d.t("img.rebuildDone", bytes(saved)))
      Changes.bump()
    }
  }
  val actions = { name: String ->
    val d = currentDict
    alert(name, null, listOf(
      DialogButton(d["common.export"]) { export(listOf(name)) },
      DialogButton(d["img.replaceFile"]) {
        act {
          val f = pickFiles()?.firstOrNull() ?: return@act
          work { Gta.imgReplace(path, name, f) }
          Changes.bump()
        }
      },
      DialogButton(d["common.delete"], "destructive") { remove(listOf(name)) },
      DialogButton(d["common.cancel"], "cancel"),
    ))
  }

  val s = sel
  Frame(if (s != null) L.t("common.selected", s.size) else baseName(path), actions = {
    if (s != null) SelectionActions(onAll = { sel = list.map { it.name }.toSet() }, onExport = { export(s.toList()) }, onDelete = { remove(s.toList()) }, onClose = { sel = null })
    else IconButton("plus", L["img.addFiles"], onClick = add)
  }) {
    if (data == null) return@Frame Loading()
    val waste = data.fileSize - data.used
    ListTop(q, { q = it }, L["common.search"]) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T("${L.n("common.files", list.size)} · ${bytes(data.fileSize)}" + if (waste > 0) " · " + L.t("img.rebuildHint", bytes(waste)) else "", Modifier.weight(1f), dim = true, size = 12.5f)
        Button(L["img.rebuild"], kind = "soft", icon = "recycle", small = true, onClick = rebuild)
      }
    }
    LazyColumn(Modifier.fillMaxSize()) {
      items(list, key = { it.name }) { e ->
        val on = s?.contains(e.name) == true
        Row(
          Modifier.fillMaxWidth()
            .tap(scaleTo = 0.98f, onLongClick = { sel = (s ?: emptySet()).toggle(e.name).ifEmpty { null } }) {
              if (s != null) sel = s.toggle(e.name).ifEmpty { null } else actions(e.name)
            }
            .background(if (on) c.accentSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 11.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          T(e.name, Modifier.weight(1f), maxLines = 1)
          T(bytes(e.size), dim = true, size = 12.5f)
        }
      }
      item { Box(Modifier.navigationBarsPadding().padding(8.dp)) }
    }
  }
}
