/** Shared UI primitives. Small and theme-aware; screens compose them. */
package io.github.timmylich.gtatool.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Springy scale-down on press; no ripple. Used by every tappable element for consistent feedback. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(
  enabled: Boolean = true,
  scaleTo: Float = 0.96f,
  label: String? = null,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val scale by animateFloatAsState(
    if (pressed) scaleTo else 1f,
    spring(dampingRatio = if (pressed) 1f else 0.55f, stiffness = Spring.StiffnessHigh),
    label = "tap",
  )
  return this
    .graphicsLayer {
      scaleX = scale
      scaleY = scale
      alpha = if (!enabled) 0.4f else 1f
    }
    .combinedClickable(source, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
    .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

@Composable
fun T(
  text: String,
  modifier: Modifier = Modifier,
  size: Float = 15f,
  dim: Boolean = false,
  bold: Boolean = false,
  semi: Boolean = false,
  mono: Boolean = false,
  color: Color? = null,
  center: Boolean = false,
  maxLines: Int = Int.MAX_VALUE,
) {
  BasicText(
    text,
    modifier,
    style = TextStyle(
      color = color ?: if (dim) c.textDim else c.text,
      fontSize = size.sp,
      fontWeight = if (bold) FontWeight.Bold else if (semi) FontWeight.SemiBold else FontWeight.Normal,
      fontFamily = if (mono) FontFamily.Monospace else null,
      textAlign = if (center) TextAlign.Center else TextAlign.Start,
      lineHeight = (size * 1.35f).sp,
    ),
    maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
  )
}

/** Rounded surface; rows inside bring their own padding. */
@Composable
fun Card(modifier: Modifier = Modifier, vertical: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(vertical = vertical), content = content)
}

@Composable
fun Icon(name: String, size: Dp = 22.dp, color: Color, modifier: Modifier = Modifier) {
  Image(painterResource(ICONS[name] ?: ICONS.getValue("info")), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

/** primary | soft | ghost | danger */
@Composable
fun Button(
  title: String,
  modifier: Modifier = Modifier,
  kind: String = "primary",
  icon: String? = null,
  small: Boolean = false,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val bg = when (kind) {
    "primary" -> c.accent
    "soft" -> c.accentSoft
    "danger" -> c.danger.a(0.15f)
    else -> Color.Transparent
  }
  val fg = when (kind) {
    "primary" -> c.onAccent
    "danger" -> c.danger
    "soft" -> c.accent
    else -> c.text
  }
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .tap(enabled, label = title, onClick = onClick)
      .background(bg, shape)
      .then(if (kind == "ghost") Modifier.border(1.dp, c.border, shape) else Modifier)
      .padding(horizontal = 16.dp, vertical = if (small) 8.dp else 13.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, if (small) 16.dp else 18.dp, fg)
    T(title, size = if (small) 13.5f else 15f, semi = true, color = fg, maxLines = 1)
  }
}

@Composable
fun IconButton(icon: String, label: String, color: Color? = null, onClick: () -> Unit) {
  Box(Modifier.tap(scaleTo = 0.88f, label = label, onClick = onClick).padding(9.dp)) { Icon(icon, 22.dp, color ?: c.text) }
}

/** Rounded square behind an icon (lists, dialogs). */
@Composable
fun IconBadge(name: String, size: Dp = 38.dp, icon: Dp = 20.dp, color: Color? = null) {
  Box(Modifier.size(size).background(c.accentSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
    Icon(name, icon, color ?: c.accent)
  }
}

/** List row with an icon badge, title, subtitle and an optional right part. */
@Composable
fun ListRow(
  title: String,
  sub: String? = null,
  icon: String? = null,
  active: Boolean = false,
  right: (@Composable () -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit = {},
) {
  Row(
    Modifier.fillMaxWidth().tap(scaleTo = 0.98f, onLongClick = onLongClick, onClick = onClick)
      .background(if (active) c.accentSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (icon != null) IconBadge(icon)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      T(title, semi = true, maxLines = 1)
      if (sub != null) T(sub, dim = true, size = 12.5f, maxLines = 2)
    }
    right?.invoke()
  }
}

/** Row with a title, hint and an app-styled switch; the whole row is tappable. */
@Composable
fun Toggle(title: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().tap(scaleTo = 0.99f, label = title) { onChange(!value) }.padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      T(title)
      if (hint != null) T(hint, dim = true, size = 12.5f)
    }
    val p by animateFloatAsState(if (value) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "switch")
    Box(Modifier.size(46.dp, 28.dp).background(c.cardAlt, CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.CenterStart) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = p.coerceIn(0f, 1f) }.background(c.accent, CircleShape))
      Box(Modifier.padding(start = 3.dp).offset(x = (18 * p).dp).size(22.dp).background(Color.White, CircleShape))
    }
  }
}

/** Single choice as a wrapping row of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <V> Segmented(value: V, options: List<Pair<V, String>>, onChange: (V) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    for ((v, label) in options) {
      val on = v == value
      Box(Modifier.tap(label = label) { onChange(v) }.background(if (on) c.accent else c.cardAlt, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        T(label, size = 13.5f, semi = true, color = if (on) c.onAccent else c.text)
      }
    }
  }
}

/** A labelled block inside a card. */
@Composable
fun Labeled(label: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    T(label, semi = true, size = 14f)
    content()
    if (hint != null) T(hint, dim = true, size = 12.5f)
  }
}

@Composable
fun Input(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, mono: Boolean = false) {
  var focused by remember { mutableStateOf(false) }
  Box(
    modifier.fillMaxWidth().background(c.cardAlt, RoundedCornerShape(12.dp))
      .border(1.dp, if (focused) c.accent else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    val style = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = if (mono) FontFamily.Monospace else null)
    if (value.isEmpty() && placeholder != null) BasicText(placeholder, style = style.copy(color = c.textDim), maxLines = 1)
    BasicTextField(
      value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
      singleLine = true, textStyle = style, cursorBrush = SolidColor(c.accent),
      keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    )
  }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(c.border))

@Composable
fun Loading(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
  CircularProgressIndicator(color = c.accent)
}

@Composable
fun Empty(text: String) = T(text, Modifier.fillMaxWidth().padding(40.dp), dim = true, center = true)
