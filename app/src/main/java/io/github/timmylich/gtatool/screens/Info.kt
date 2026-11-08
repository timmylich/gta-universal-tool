/** Settings, the "Support the author" page and the one-time donation prompt. */
package io.github.timmylich.gtatool.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.timmylich.gtatool.BuildConfig
import io.github.timmylich.gtatool.ISSUES_URL
import io.github.timmylich.gtatool.REPO_URL
import io.github.timmylich.gtatool.TELEGRAM_URL
import io.github.timmylich.gtatool.TRIBUTE_TELEGRAM_URL
import io.github.timmylich.gtatool.TRIBUTE_WEB_URL
import io.github.timmylich.gtatool.WALLETS
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.lib.daysSinceInstall
import io.github.timmylich.gtatool.lib.pendingMilestone
import io.github.timmylich.gtatool.ui.Button
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.Icon
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.Labeled
import io.github.timmylich.gtatool.ui.ListRow
import io.github.timmylich.gtatool.ui.Nav
import io.github.timmylich.gtatool.ui.Route
import io.github.timmylich.gtatool.ui.Screen
import io.github.timmylich.gtatool.ui.Segmented
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.Toggle
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.c
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.tap
import io.github.timmylich.gtatool.ui.work

@Composable
fun SettingsScreen() {
  val s = Store.s
  val ctx = LocalContext.current
  Screen(L["settings.title"]) {
    Card(vertical = 6.dp) {
      Labeled(L["settings.language"]) {
        Segmented(s.language, listOf("system" to L["settings.system"], "ru" to "Русский", "en" to "English")) { v -> Store.update { copy(language = v) } }
      }
      Labeled(L["settings.theme"]) {
        Segmented(s.theme, listOf("system" to L["settings.system"], "light" to L["settings.light"], "dark" to L["settings.dark"])) { v -> Store.update { copy(theme = v) } }
      }
    }
    Card(vertical = 6.dp) {
      Toggle(L["settings.backup"], s.backup, L["settings.backupHint"]) { v -> Store.update { copy(backup = v) } }
      Toggle(L["img.newWarnTitle"], !s.imgWarned) { v -> Store.update { copy(imgWarned = !v) } }
    }
    Card {
      ListRow(L["settings.clearCache"], icon = "eraser") {
        act { work { Gta.clearCache() }; alert(currentDict["common.done"], currentDict["settings.cleared"]) }
      }
    }
    Card {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(L["settings.about"], semi = true)
        T(L["settings.aboutText"], dim = true, size = 13.5f)
        T("${L["settings.version"]} ${BuildConfig.VERSION_NAME} · AGPL-3.0-or-later", dim = true, size = 13f)
      }
    }
    Card { ListRow(L["settings.source"], REPO_URL.removePrefix("https://"), "code") { openUrl(ctx, REPO_URL) } }
  }
}

@Composable
private fun HeartBadge(size: Int) {
  Box(Modifier.size(size.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Icon("heart", (size / 2).dp, c.accent) }
}

@Composable
fun Support() {
  val ctx = LocalContext.current
  var copied by remember { mutableStateOf<String?>(null) }
  Screen(L["home.support"]) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      HeartBadge(64)
      T(L["donation.screenTitle"], size = 18f, semi = true, center = true)
      T(L["donation.screenText"], dim = true, center = true, size = 14f)
    }
    T(L["donation.tribute"], semi = true, size = 16f)
    T(L["donation.tributeHint"], dim = true, size = 13f)
    Button(L["donation.tributeTelegram"], Modifier.fillMaxWidth(), icon = "send") { openUrl(ctx, TRIBUTE_TELEGRAM_URL) }
    Button(L["donation.tributeWeb"], Modifier.fillMaxWidth(), "soft", "globe") { openUrl(ctx, TRIBUTE_WEB_URL) }
    T(L["donation.crypto"], Modifier.padding(top = 10.dp), semi = true, size = 16f)
    for (w in WALLETS) {
      val done = copied == w.id
      Column(
        Modifier.fillMaxWidth().tap(scaleTo = 0.98f, label = "${L["donation.wallets.${w.id}"]}: ${L["donation.copy"]}") {
          (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(w.id, w.address))
          copied = w.id
        }.background(c.cardAlt, RoundedCornerShape(16.dp)).border(1.dp, if (done) c.accent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          T(L["donation.wallets.${w.id}"], Modifier.weight(1f), semi = true)
          Icon(if (done) "check" else "copy", 18.dp, if (done) c.accent else c.textDim)
        }
        T(w.networks, dim = true, size = 12f)
        T(if (done) L["donation.copied"] else w.address, mono = true, size = 12f, color = if (done) c.accent else c.text)
      }
    }
    T(L["donation.evmNote"], dim = true, size = 11f)
    T(L["donation.otherWays"], Modifier.padding(top = 8.dp), semi = true, size = 16f)
    Button(L["donation.star"], Modifier.fillMaxWidth(), "ghost", "star") { openUrl(ctx, REPO_URL) }
    Button(L["donation.telegram"], Modifier.fillMaxWidth(), "ghost", "send") { openUrl(ctx, TELEGRAM_URL) }
    Button(L["donation.report"], Modifier.fillMaxWidth(), "ghost", "bug") { openUrl(ctx, ISSUES_URL) }
  }
}

/** Shown once on install-age milestones (lib/Donation.kt). */
@Composable
fun DonationPrompt() {
  val s = Store.s
  val milestone = pendingMilestone(daysSinceInstall(s.installedAt), s.donationShown) ?: return
  val ctx = LocalContext.current
  val close = { Store.update { copy(donationShown = donationShown + milestone) } }
  val go = { f: () -> Unit -> close(); f() }
  Dialog(onDismissRequest = close) {
    Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(28.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HeartBadge(56) }
      T(L.t("donation.title", L.n("common.days", milestone)), Modifier.fillMaxWidth(), size = 18f, semi = true, center = true)
      T(L["donation.text"], Modifier.fillMaxWidth().padding(bottom = 6.dp), dim = true, center = true, size = 14f)
      Button(L["donation.money"], Modifier.fillMaxWidth(), icon = "heart") { go { Nav.push(Route.Support) } }
      Button(L["donation.star"], Modifier.fillMaxWidth(), "soft", "star") { go { openUrl(ctx, REPO_URL) } }
      Button(L["donation.telegram"], Modifier.fillMaxWidth(), "soft", "send") { go { openUrl(ctx, TELEGRAM_URL) } }
      Button(L["donation.later"], Modifier.fillMaxWidth(), "ghost") { close() }
    }
  }
}
