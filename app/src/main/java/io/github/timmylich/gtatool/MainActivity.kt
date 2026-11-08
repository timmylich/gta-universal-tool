package io.github.timmylich.gtatool

import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.i18n.Dict
import io.github.timmylich.gtatool.i18n.resolveLang
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.screens.AppScreen
import io.github.timmylich.gtatool.screens.DonationPrompt
import io.github.timmylich.gtatool.ui.FilePickerHost
import io.github.timmylich.gtatool.ui.LocalDict
import io.github.timmylich.gtatool.ui.LocalPalette
import io.github.timmylich.gtatool.ui.NavHost
import io.github.timmylich.gtatool.ui.OverlayHost
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.palette
import java.io.File

class GtaApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Store.init(this)
    Gta.cacheDir = cacheDir
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { Root() }
  }
}

@Composable
private fun Root() {
  val ctx = LocalContext.current
  val s = Store.s
  val dark = when (s.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }
  val pal = remember(dark) { palette(dark) }
  val dict = remember(s.language) { Dict.of(ctx, resolveLang(s.language)) }
  currentDict = dict
  val activity = ctx as ComponentActivity
  LaunchedEffect(dark) {
    val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    activity.enableEdgeToEdge(bar, bar)
    activity.window.setBackgroundDrawable(ColorDrawable(pal.bg.toArgb()))
  }
  LaunchedEffect(s.backup) { Gta.backupRoot = if (s.backup) File(Gta.appDir("backup")) else null }
  CompositionLocalProvider(LocalPalette provides pal, LocalDict provides dict) {
    Box(Modifier.fillMaxSize().background(pal.bg)) {
      NavHost { AppScreen(it) }
      // After the stack, so its back handler and drawing come first.
      FilePickerHost()
      DonationPrompt()
      OverlayHost()
    }
  }
}
