/**
 * Minimal stack navigation: screens slide in from the right over the ones below, which stay
 * composed (so lists keep their scroll and state, like a native stack).
 */
package io.github.timmylich.gtatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

sealed interface Route {
  data object Home : Route
  data object Textures : Route
  data class TexDb(val path: String) : Route
  /** [items]: (image file, texture name). */
  data class TexImport(val path: String, val items: List<Pair<String, String>>) : Route
  data object Img : Route
  data class ImgView(val path: String) : Route
  data object Audio : Route
  data class Osw(val path: String) : Route
  data object Adapt : Route
  data object Settings : Route
  data object Support : Route
  data object Guide : Route
}

class NavEntry(val route: Route, start: Float) {
  /** 0 = on screen, 1 = off to the right. */
  val offset = Animatable(start)
  var leaving = false
}

object Nav {
  val stack = mutableStateListOf(NavEntry(Route.Home, 0f))
  internal var animate: ((NavEntry) -> Unit)? = null

  fun push(r: Route) {
    stack += NavEntry(r, 1f)
  }

  fun pop() {
    val top = stack.lastOrNull() ?: return
    if (stack.size <= 1 || top.leaving) return
    top.leaving = true
    animate?.invoke(top) ?: stack.remove(top)
  }
}

@Composable
fun NavHost(screen: @Composable (Route) -> Unit) {
  val scope = rememberCoroutineScope()
  Nav.animate = { e ->
    scope.launch {
      e.offset.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
      Nav.stack.remove(e)
    }
  }
  BackHandler(Nav.stack.size > 1) { Nav.pop() }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = constraints.maxWidth.toFloat()
    for (e in Nav.stack) key(e) {
      LaunchedEffect(e) { if (e.offset.value > 0f && !e.leaving) e.offset.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
      Box(Modifier.fillMaxSize().graphicsLayer { translationX = e.offset.value * w }.background(c.bg)) { screen(e.route) }
    }
  }
}

/** Pushed screen without its own scrolling: header (back, title, actions), then [content]; [bottom] floats over it. */
@Composable
fun Frame(
  title: String,
  actions: @Composable RowScope.() -> Unit = {},
  bottom: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(Modifier.fillMaxSize().background(c.bg)) {
    Column(Modifier.fillMaxSize()) {
      Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton("left", L["common.back"]) { Nav.pop() }
        T(title, Modifier.weight(1f).padding(start = 4.dp), size = 19f, semi = true, maxLines = 1)
        actions()
      }
      Column(Modifier.fillMaxSize().imePadding(), content = content)
    }
    if (bottom != null) BottomBar(bottom)
  }
}

/** Pushed screen whose body is a scrolling column of cards. */
@Composable
fun Screen(
  title: String,
  actions: @Composable RowScope.() -> Unit = {},
  bottom: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) = Frame(title, actions, bottom) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    content()
    Spacer(Modifier.height(if (bottom != null) 84.dp else 8.dp))
  }
}

@Composable
private fun BoxScope.BottomBar(content: @Composable () -> Unit) {
  Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) { content() }
}
