/** Route → screen, and the home screen (storage permission, game folder, tools). */
package io.github.timmylich.gtatool.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.timmylich.gtatool.SFRP_URL
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.Divider
import io.github.timmylich.gtatool.ui.IconButton
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.ListRow
import io.github.timmylich.gtatool.ui.Nav
import io.github.timmylich.gtatool.ui.Route
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.pickDir
import io.github.timmylich.gtatool.ui.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppScreen(r: Route) = when (r) {
  Route.Home -> Home()
  Route.Textures -> Textures()
  is Route.TexDb -> TexDbScreen(r.path)
  is Route.TexImport -> TexImport(r.path, r.items)
  Route.Img -> ImgList()
  is Route.ImgView -> ImgView(r.path)
  Route.Audio -> AudioList()
  is Route.Osw -> Osw(r.path)
  Route.Adapt -> Adapt()
  Route.Settings -> SettingsScreen()
  Route.Support -> Support()
  Route.Guide -> Guide()
}

fun openUrl(ctx: Context, url: String) {
  runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun hasAccess(ctx: Context) =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
  else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun openStorageSettings(ctx: Context) {
  val uri = Uri.parse("package:${ctx.packageName}")
  val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
  else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
  runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val TOOLS = listOf(
  Triple("image", "textures", Route.Textures),
  Triple("box", "img", Route.Img),
  Triple("audio", "audio", Route.Audio),
  Triple("wand", "adapt", Route.Adapt),
)

@Composable
private fun Home() {
  val ctx = LocalContext.current
  var access by remember { mutableStateOf(hasAccess(ctx)) }
  LifecycleResumeEffect(Unit) {
    access = hasAccess(ctx)
    onPauseOrDispose {}
  }
  // Android 8–10 has no "All files access": the classic storage permission is asked at runtime instead.
  val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access = it }
  LaunchedEffect(Unit) { if (!access && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) legacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }

  val gameDir = Store.s.gameDir
  var choosing by remember { mutableStateOf(false) }
  var found by remember { mutableStateOf<List<String>?>(null) }
  val root = Gta.storageRoot
  val picking = access && (gameDir == null || choosing)
  LaunchedEffect(picking) {
    if (!picking) return@LaunchedEffect
    found = null
    found = withContext(Dispatchers.IO) { runCatching { Gta.findGameDirs() }.getOrDefault(emptyList()) }
  }
  val choose = { d: String ->
    Store.update { copy(gameDir = d) }
    choosing = false
  }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        T(L["home.title"], size = 24f, bold = true)
        T(L["home.subtitle"], dim = true, size = 13.5f)
      }
      IconButton("heart", L["home.support"], c.accent) { Nav.push(Route.Support) }
      IconButton("settings", L["home.settings"]) { Nav.push(Route.Settings) }
    }

    when {
      !access -> Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          T(L["perm.title"], size = 17f, semi = true)
          T(L["perm.text"], dim = true)
          Button(L["perm.grant"], Modifier.fillMaxWidth()) { openStorageSettings(ctx) }
        }
      }
      picking -> Card(vertical = 8.dp) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          T(L["pick.title"], size = 17f, semi = true)
          T(L["pick.text"], dim = true, size = 13.5f)
        }
        val f = found
        when {
          f == null -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = c.accent)
            T(L["pick.scanning"], dim = true)
          }
          f.isEmpty() -> T(L["pick.nothing"], Modifier.padding(horizontal = 16.dp, vertical = 8.dp), dim = true)
          else -> for (d in f) ListRow(d.substringAfterLast('/'), d.removePrefix(root), "folder", active = d == gameDir) { choose(d) }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(L["pick.manual"], Modifier.weight(1f), "soft") { act { pickDir(root)?.let(choose) } }
          if (gameDir != null) Button(L["common.cancel"], kind = "ghost") { choosing = false }
        }
      }
      else -> {
        Card {
          ListRow(gameDir!!.substringAfterLast('/'), "${L["home.gameDir"]}: ${gameDir.removePrefix(root)}", "folder", right = {
            Button(L["home.change"], kind = "soft", small = true) { choosing = true }
          })
        }
        Card {
          TOOLS.forEachIndexed { i, (icon, key, route) ->
            if (i > 0) Divider()
            ListRow(L["home.$key"], L["home.${key}Hint"], icon) { Nav.push(route) }
          }
        }
      }
    }
    if (access) Card { ListRow(L["home.guide"], L["home.guideHint"], "info") { Nav.push(Route.Guide) } }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
      T(L["home.credit"], dim = true, size = 12.5f, center = true)
      T("sf-rp.com", Modifier.tap(label = "sf-rp.com") { openUrl(ctx, SFRP_URL) }.padding(4.dp), color = c.accent, size = 13f, semi = true)
      T(L["home.askSupport"], dim = true, size = 12.5f, center = true)
      Button(L["home.support"], kind = "soft", icon = "heart", small = true) { Nav.push(Route.Support) }
    }
  }
}
