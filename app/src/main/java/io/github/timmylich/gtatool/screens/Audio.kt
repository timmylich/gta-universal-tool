/** Audio packs (.osw + .osw.idx in audio/): the list and one pack (play, queue mp3 replacements, append, rebuild once). */
package io.github.timmylich.gtatool.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.Frame
import io.github.timmylich.gtatool.ui.IconButton
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Nav
import io.github.timmylich.gtatool.ui.Route
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.pickFiles
import io.github.timmylich.gtatool.ui.work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AudioList() = FileListScreen(L["audio.title"], "audio", L["audio.hint"], load = Gta::oswList, sub = { "${it.dir} · ${bytes(it.size)}" }) {
  Nav.push(Route.Osw(it.path))
}

@Composable
fun Osw(path: String) {
  val tracks = loaded(path) { Gta.oswTracks(path) }
  var repl by remember { mutableStateOf(mapOf<Int, String>()) }
  var added by remember { mutableStateOf(listOf<String>()) }
  var playing by remember { mutableStateOf<Int?>(null) }
  val player = remember { MediaPlayer() }
  DisposableEffect(Unit) { onDispose { player.release() } }

  val stop = { runCatching { player.reset() }; playing = null }
  val play = { i: Int ->
    if (playing == i) stop()
    else act {
      val file = repl[i] ?: withContext(Dispatchers.IO) { runCatching { Gta.oswPreview(path, i) }.getOrNull() } ?: return@act
      runCatching {
        player.reset()
        player.setDataSource(file)
        player.setOnCompletionListener { playing = null }
        player.prepare()
        player.start()
        playing = i
      }.onFailure { alert(currentDict["common.error"], it.message ?: it.toString()) }
    }
  }
  val apply = {
    stop()
    act {
      work { Gta.oswApply(path, repl, added) } ?: return@act
      repl = emptyMap()
      added = emptyList()
      Changes.bump()
      alert(currentDict["common.done"], currentDict["audio.applied"])
    }
  }

  val pending = repl.size + added.size
  Frame(baseName(path).removeSuffix(".osw").removeSuffix(".OSW"), actions = {
    IconButton("plus", L["audio.addTrack"]) { act { pickFiles(listOf(".mp3"), multi = true)?.let { added = added + it } } }
  }, bottom = if (pending > 0) ({ Button(L.t("audio.apply", pending), Modifier.fillMaxWidth(), onClick = apply) }) else null) {
    if (tracks == null) return@Frame Loading()
    T(L.n("audio.tracks", tracks.size), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), dim = true, size = 12.5f)
    LazyColumn(Modifier.fillMaxSize()) {
      itemsIndexed(tracks, key = { i, t -> "$i/${t.name}" }) { i, t ->
        val r = repl[i]
        Row(
          Modifier.fillMaxWidth().background(if (r != null) c.accentSoft else Color.Transparent).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(if (playing == i) "pause" else "play", L["audio.play"], c.accent) { play(i) }
          Column(Modifier.weight(1f)) {
            T("${i + 1}. ${t.name}", maxLines = 1)
            T(if (r != null) "${L["audio.pending"]}: ${baseName(r)}" else bytes(t.size), dim = true, size = 12f, maxLines = 1)
          }
          if (r != null) IconButton("undo", L["common.cancel"]) { repl = repl - i }
          else IconButton("download", L["common.export"]) {
            act {
              val dest = "${Gta.appDir("export/${baseName(path)}")}/${t.name}"
              work { Gta.oswExtract(path, i, dest) }?.let { alert(currentDict["common.done"], shortPath(it)) }
            }
          }
          IconButton("replace", L["audio.replaceWith"]) { act { pickFiles(listOf(".mp3"))?.firstOrNull()?.let { repl = repl + (i to it) } } }
        }
      }
      if (added.isNotEmpty()) item {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) { for (a in added) T("+ ${baseName(a)}", color = c.ok, size = 13f) }
      }
      item { Box(Modifier.navigationBarsPadding().height(if (pending > 0) 90.dp else 16.dp)) }
    }
  }
}
