/** Backups of game files (/GTAUniversalTool/backup): restore the original or delete the copy. */
package io.github.timmylich.gtatool.screens

import androidx.compose.runtime.Composable
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.BackupItem
import io.github.timmylich.gtatool.lib.Gta
import io.github.timmylich.gtatool.ui.Card
import io.github.timmylich.gtatool.ui.DialogButton
import io.github.timmylich.gtatool.ui.Divider
import io.github.timmylich.gtatool.ui.Empty
import io.github.timmylich.gtatool.ui.L
import io.github.timmylich.gtatool.ui.ListRow
import io.github.timmylich.gtatool.ui.Loading
import io.github.timmylich.gtatool.ui.Screen
import io.github.timmylich.gtatool.ui.T
import io.github.timmylich.gtatool.ui.act
import io.github.timmylich.gtatool.ui.alert
import io.github.timmylich.gtatool.ui.ask
import io.github.timmylich.gtatool.ui.currentDict
import io.github.timmylich.gtatool.ui.work
import java.text.DateFormat
import java.util.Date

@Composable
fun Backups() {
  val list = loaded("backups") { Gta.backupList() }
  val restore = { b: BackupItem ->
    act {
      val d = currentDict
      if (!ask(d["backups.restore"], d.t("backups.restoreAsk", shortPath(b.original)), d["backups.restore"], true)) return@act
      work { Gta.backupRestore(b) } ?: return@act
      Changes.bump()
      alert(d["common.done"], d["backups.restored"])
    }
  }
  val delete = { b: BackupItem ->
    act {
      val d = currentDict
      if (!ask(d["common.delete"], d.t("backups.deleteAsk", b.name), d["common.delete"], true)) return@act
      work { Gta.backupDelete(b) }
      Changes.bump()
    }
  }
  Screen(L["backups.title"]) {
    T(L["backups.hint"], dim = true, size = 13f)
    if (list == null) Loading()
    else Card {
      if (list.isEmpty()) Empty(L["backups.none"])
      list.forEachIndexed { i, b ->
        if (i > 0) Divider()
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(b.time))
        ListRow(b.name, "${shortPath(b.original)}\n${bytes(b.size)} · $date", "folder") {
          val d = currentDict
          alert(b.name, shortPath(b.original), listOf(
            DialogButton(d["backups.restore"]) { restore(b) },
            DialogButton(d["backups.delete"], "destructive") { delete(b) },
            DialogButton(d["common.cancel"], "cancel"),
          ))
        }
      }
    }
  }
}
