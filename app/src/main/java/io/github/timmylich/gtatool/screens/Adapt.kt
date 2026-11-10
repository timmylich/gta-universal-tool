/**
 * PC mods → mobile.
 * Models mode: several .dff and .txd at once (one .txd may serve many models); textures get unique
 * "prefix_random" names, models are rewritten, unused textures dropped; optional import into a texdb and an IMG.
 * Retexture mode: only .txd; textures replace the ones with the same names wherever they are in the databases.
 */
package io.github.timmylich.gtatool.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.timmylich.gtatool.lib.AdaptInfo
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.lib.RetexInfo
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.Divider
import io.github.timmylich.gtatool.ui.Icon
import io.github.timmylich.gtatool.ui.Input
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Labeled
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Screen
import io.github.timmylich.gtatool.ui.Segmented
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.Toggle
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.pickDir
import io.github.timmylich.gtatool.ui.pickFiles
import io.github.timmylich.gtatool.ui.tap
import io.github.timmylich.gtatool.ui.work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Files(icon: String, title: String, files: List<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
  Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(icon, 20.dp, c.accent)
      T(title + if (files.isNotEmpty()) " · ${files.size}" else "", Modifier.weight(1f), semi = true)
      Button(L["common.add"], kind = "soft", icon = "plus", small = true, onClick = onAdd)
    }
    if (files.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      for (f in files) Row(
        Modifier.tap(label = "${baseName(f)} ×") { onRemove(f) }.background(c.cardAlt, RoundedCornerShape(10.dp)).padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        T(baseName(f), size = 13f)
        Icon("close", 14.dp, c.textDim)
      }
    }
  }
}

@Composable
private fun Lines(title: String, items: List<String>, color: Color? = null, max: Int = 30) {
  if (items.isEmpty()) return
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    T("$title: ${items.size}", semi = true, size = 13.5f)
    T(items.take(max).joinToString("\n") + if (items.size > max) "\n… +${items.size - max}" else "", mono = true, size = 12f, color = color ?: c.textDim)
  }
}

private fun stamp() = SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())

@Composable
fun Adapt() {
  val gameDir = Store.s.gameDir ?: ""
  val prefix = Store.s.prefix
  var mode by remember { mutableStateOf("models") }
  var dffs by remember { mutableStateOf(listOf<String>()) }
  var txds by remember { mutableStateOf(listOf<String>()) }
  val dbs = loaded(gameDir) { Gta.texdbList(gameDir) } ?: emptyList()
  val imgs = loaded(gameDir) { Gta.imgList(gameDir) } ?: emptyList()
  var db by remember { mutableStateOf<String?>(null) }
  var img by remember { mutableStateOf<String?>(null) }
  val dbPath = db ?: (dbs.firstOrNull { it.name == "gta3" } ?: dbs.firstOrNull())?.path
  val imgPath = img ?: (imgs.firstOrNull { it.name.equals("gta3.img", true) } ?: imgs.firstOrNull())?.path
  var toDb by remember { mutableStateOf(true) }
  var toImg by remember { mutableStateOf(false) }
  var addMissing by remember { mutableStateOf(false) }
  var skipSame by remember { mutableStateOf(true) }
  var o by remember { mutableStateOf(Store.s.texImport) }

  // Analysis is keyed by the inputs: a stale result is never shown for a changed selection.
  var models by remember { mutableStateOf<AdaptInfo?>(null) }
  var retex by remember { mutableStateOf<RetexInfo?>(null) }
  var analyzing by remember { mutableStateOf(false) }
  val fallback = if (addMissing) dbPath else null
  LaunchedEffect(mode, dffs, txds, fallback) {
    models = null
    retex = null
    if (txds.isEmpty() || (mode == "models" && dffs.isEmpty())) return@LaunchedEffect
    analyzing = true
    runCatching {
      withContext(Dispatchers.IO) { if (mode == "models") models = Gta.adaptAnalyze(dffs, txds) else retex = Gta.retexAnalyze(gameDir, txds, fallback) }
    }.onFailure { alert(currentDict["common.error"], it.message ?: it.toString()) }
    analyzing = false
  }

  val addDffs = {
    act {
      val f = pickFiles(listOf(".dff"), multi = true)?.takeIf { it.isNotEmpty() } ?: return@act
      // A PC mod usually ships "name.txd" next to "name.dff".
      val guesses = f.map { it.replace(Regex("""\.dff$""", RegexOption.IGNORE_CASE), ".txd") }.filter { File(it).exists() }
      dffs = (dffs + f).distinct()
      if (guesses.isNotEmpty()) txds = (txds + guesses).distinct()
    }
  }
  val addTxds = { act { pickFiles(listOf(".txd"), multi = true)?.let { txds = (txds + it).distinct() } } }
  // Whole-map packs: every .txd inside a folder (an unpacked gta3.img), subfolders included.
  val addTxdDir = {
    act {
      val dir = pickDir() ?: return@act
      val found = withContext(Dispatchers.IO) { File(dir).walkTopDown().filter { it.isFile && it.name.endsWith(".txd", true) }.map { it.path }.toList() }
      if (found.isEmpty()) alert(currentDict["adapt.noTxd"]) else txds = (txds + found).distinct()
    }
  }
  val setOpt = { v: io.github.timmylich.gtatool.data.ImportOptions -> o = v; saveImportOptions(v) }

  val goModels = {
    act {
      val d = currentDict
      if (toImg && imgPath != null && !confirmNewEntries(imgPath, dffs.map { baseName(it).lowercase() })) return@act
      val first = baseName(dffs[0]).substringBeforeLast('.')
      val out = Gta.appDir("adapted/$first${if (dffs.size > 1) "+${dffs.size - 1}" else ""}_${stamp()}")
      val r = work { Gta.adaptRun(dffs, txds, prefix, out, if (toDb) dbPath else null, o, if (toImg) imgPath else null) } ?: return@act
      Changes.bump()
      val lines = r.models.joinToString("\n") { m -> "${m.name}: " + m.renamed.entries.joinToString(", ") { "${it.key}→${it.value}" } }
      alert(d["adapt.done"], "${d.t("adapt.resultModels", r.models.size, r.textures)}\n\n$lines\n\n${d.t("adapt.savedTo", shortPath(out))}")
    }
  }
  val goRetex = {
    act {
      val d = currentDict
      val r = work { Gta.retexRun(gameDir, txds, o, fallback, skipSame) } ?: return@act
      Changes.bump()
      val msg = d.result(Triple("written", r.written, true), Triple("unchanged", r.unchanged, false), Triple("aliases", r.aliases, false), Triple("skipped", r.missing.size, false), Triple("errors", r.failed.size, false)) +
        (if (r.missing.isNotEmpty()) "\n\n${d["adapt.retexSkipped"]}: ${r.missing.take(50).joinToString(", ")}" + (if (r.missing.size > 50) " … +${r.missing.size - 50}" else "") else "")
      alert(d["adapt.done"], withFailures(msg, r.failed))
    }
  }

  val dbPicker = @Composable {
    Labeled("texdb") { Segmented(dbPath, dbs.map { it.path to it.name }) { db = it } }
  }
  val canRun = !analyzing && if (mode == "models") (models?.textures ?: 0) > 0 else (retex?.writes ?: 0) > 0

  Screen(L["adapt.title"], bottom = {
    Button(if (mode == "models") L["adapt.run"] else L["adapt.runRetex"], Modifier.fillMaxWidth(), enabled = canRun, onClick = if (mode == "models") goModels else goRetex)
  }) {
    Segmented(mode, listOf("models" to L["adapt.modeModels"], "retex" to L["adapt.modeRetex"])) { mode = it }
    T(if (mode == "models") L["adapt.hint"] else L["adapt.retexHint"], dim = true, size = 13f)
    Card {
      if (mode == "models") {
        Files("filebox", L["adapt.dff"], dffs, addDffs) { f -> dffs = dffs - f }
        Divider()
      }
      Files("images", L["adapt.txd"], txds, addTxds) { f -> txds = txds - f }
      if (mode == "retex") Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(L["adapt.addFolder"], Modifier.weight(1f), "soft", "folder", small = true, onClick = addTxdDir)
        if (txds.isNotEmpty()) Button(L["adapt.clear"], kind = "ghost", small = true) { txds = emptyList() }
      }
    }
    if (analyzing) Loading()

    val m = models
    if (mode == "models" && m != null) Card(vertical = 6.dp) {
      for (x in m.models) {
        Lines(x.name, x.found, c.ok)
        Lines(L["adapt.missing"], x.missing, c.warn)
      }
      Lines(L["adapt.unused"], m.unused, max = 12)
    }

    val rt = retex
    if (mode == "retex" && rt != null) Card(vertical = 6.dp) {
      T(L.t("adapt.retexPlan", rt.writes, rt.split, rt.aliases), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), size = 13.5f)
      Lines(L["adapt.retexNone"], rt.missing, c.warn)
      if (rt.missing.isNotEmpty() || addMissing) {
        Toggle(L["adapt.addMissing"], addMissing) { addMissing = it }
        if (addMissing && dbs.isNotEmpty()) dbPicker()
      }
      Toggle(L["adapt.skipSame"], skipSame, L["adapt.skipSameHint"]) { skipSame = it }
    }

    if (mode == "models") Card(vertical = 6.dp) {
      Labeled(L["adapt.prefix"], L["adapt.prefixHint"]) {
        Input(prefix, { v -> Store.update { copy(prefix = v.filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }.take(14)) } }, mono = true)
      }
      Toggle(L["adapt.toTexdb"], toDb) { toDb = it }
      if (toDb && dbs.isNotEmpty()) dbPicker()
      Toggle(L["adapt.toImg"], toImg) { toImg = it }
      if (toImg && imgs.isNotEmpty()) Labeled("IMG") { Segmented(imgPath, imgs.map { it.path to it.name }) { img = it } }
    }

    if (mode == "retex" || toDb) Card(vertical = 6.dp) { ImportOptionsForm(o, setOpt) }
  }
}
