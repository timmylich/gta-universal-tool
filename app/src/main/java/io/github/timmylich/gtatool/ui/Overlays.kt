/**
 * App-wide overlays: dialogs (`alert`, `ask`), the busy indicator (`work { }`) and the bottom sheet.
 * Hosts are mounted once in MainActivity. `work` runs blocking file work on a background thread with a
 * spinner over the UI and turns errors into a dialog.
 */
package io.github.timmylich.gtatool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.NoSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [style]: default | cancel | destructive. The cancel button also runs on dismiss. */
data class DialogButton(val text: String, val style: String = "default", val onClick: (() -> Unit)? = null)

private data class DialogData(val title: String, val message: String?, val buttons: List<DialogButton>)

private object Dialogs {
  val queue = mutableStateListOf<DialogData>()
}

fun alert(title: String, message: String? = null, buttons: List<DialogButton> = emptyList()) {
  Dialogs.queue += DialogData(title, message, buttons)
}

/** Yes/no question; true on the confirm button. */
suspend fun ask(title: String, message: String, confirm: String, destructive: Boolean = false): Boolean {
  val r = CompletableDeferred<Boolean>()
  alert(title, message, listOf(
    DialogButton(currentDict["common.cancel"], "cancel") { r.complete(false) },
    DialogButton(confirm, if (destructive) "destructive" else "default") { r.complete(true) },
  ))
  return r.await()
}

private val scope = MainScope()

/** Starts UI work (dialogs, `work`) from a click handler. */
fun act(block: suspend () -> Unit) {
  scope.launch { block() }
}

private object Busy {
  var label by mutableStateOf<String?>(null)
}

/** Runs [block] on a background thread behind a spinner; null (after an error dialog) when it fails. */
suspend fun <T> work(block: () -> T): T? {
  Busy.label = currentDict["common.working"]
  return try {
    withContext(Dispatchers.IO) { block() }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    val msg = if (e is NoSpace) currentDict.t("common.noSpace", bytes(e.need)) else e.message ?: e.toString()
    alert(currentDict["common.error"], msg)
    null
  } finally {
    Busy.label = null
  }
}

@Composable
fun OverlayHost() {
  Busy.label?.let { label ->
    Dialog({}, DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
      Column(
        Modifier.background(c.card, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
        T(label)
      }
    }
  }
  val d = Dialogs.queue.firstOrNull() ?: return
  val buttons = d.buttons.ifEmpty { listOf(DialogButton(L["common.ok"])) }
  val press = { b: DialogButton? ->
    Dialogs.queue.remove(d)
    b?.onClick?.invoke()
  }
  Dialog({ press(buttons.firstOrNull { it.style == "cancel" }) }) {
    Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      T(d.title, size = 18f, semi = true)
      if (d.message != null) Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { T(d.message, dim = true, size = 14.5f) }
      val kind = { b: DialogButton -> when (b.style) { "cancel" -> "ghost"; "destructive" -> "danger"; else -> "primary" } }
      if (buttons.size <= 2) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in buttons) Button(b.text, Modifier.weight(1f), kind(b)) { press(b) }
      } else Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in buttons) Button(b.text, Modifier.fillMaxWidth(), kind(b)) { press(b) }
      }
    }
  }
}

/** Bottom sheet with a dimmed backdrop; drag down or tap outside to close. Compose it only while open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = c.card,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(44.dp, 5.dp).background(c.border, RoundedCornerShape(3.dp))) },
  ) { content() }
}
