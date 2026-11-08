/** Colours (system / light / dark: one background for app bar, content and system bars) and app-wide locals. */
package io.github.timmylich.gtatool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.timmylich.gtatool.i18n.Dict

@Immutable
data class Palette(
  val dark: Boolean,
  val bg: Color,
  val card: Color,
  val cardAlt: Color,
  val text: Color,
  val textDim: Color,
  val border: Color,
  val accent: Color,
  val onAccent: Color,
  val accentSoft: Color,
  val danger: Color,
  val warn: Color,
  val ok: Color,
)

fun Color.a(alpha: Float) = copy(alpha = alpha)

private val ACCENT_DARK = Color(0xFFFF8A1F)
private val ACCENT_LIGHT = Color(0xFFE8730C)

fun palette(dark: Boolean) = if (dark) Palette(
  true, Color(0xFF111214), Color(0xFF1C1D21), Color(0xFF26272C), Color(0xFFF2F2F3), Color(0xFF8F9097), Color(0xFF2A2B30),
  ACCENT_DARK, Color(0xFF1A1206), ACCENT_DARK.a(0.18f), Color(0xFFFF5A4E), Color(0xFFFFC247), Color(0xFF3DD68C),
) else Palette(
  false, Color(0xFFF6F6F8), Color(0xFFFFFFFF), Color(0xFFECECF0), Color(0xFF141416), Color(0xFF6B6B73), Color(0xFFE2E2E6),
  ACCENT_LIGHT, Color(0xFFFFFFFF), ACCENT_LIGHT.a(0.13f), Color(0xFFE3261B), Color(0xFFC98A0E), Color(0xFF0E9F5A),
)

val LocalPalette = staticCompositionLocalOf { palette(true) }
val LocalDict = staticCompositionLocalOf<Dict> { error("no dictionary") }

val c: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
val L: Dict @Composable @ReadOnlyComposable get() = LocalDict.current

/** Dictionary for code outside composition (dialogs from coroutines). */
lateinit var currentDict: Dict
