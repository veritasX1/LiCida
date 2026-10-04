package io.github.veritasx1.licida

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Apple's colors as the Camera app uses them: white on black, yellow for what is active. */
object Ink {
    val white = Color.White
    val secondary = Color(0x99EBEBF5)
    val yellow = Color(0xFFFFD60A)   // systemYellow (dark) – Camera's active state
    val red = Color(0xFFFF453A)
    val glass = Color(0x59000000)    // round buttons floating over the picture
    val glassStrong = Color(0xB3000000)
    val bar = Color(0x99000000)      // the bottom bar over the camera
    val card = Color(0xE61C1C1E)     // sheets and cards (secondarySystemBackground, dark)
    val separator = Color(0x33FFFFFF)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Inter = FontFamily(listOf(400, 500, 600, 700).map { weight ->
    Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})

fun style(size: Float, weight: Int = 400, color: Color = Ink.white, tabular: Boolean = false) = TextStyle(
    fontFamily = Inter, fontSize = size.sp, fontWeight = FontWeight(weight), color = color,
    fontFeatureSettings = if (tabular) "tnum" else null, letterSpacing = if (size >= 28) (-0.5).sp else 0.sp)

/** A round glass button floating over the picture (Camera's flash and Live buttons): 44 pt target. */
@Composable
fun GlassButton(symbol: Symbol, description: String, enabled: Boolean = true, active: Boolean = false, size: Dp = 44.dp,
                modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(size).clip(CircleShape).background(Ink.glass)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description }.alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center) {
        SymbolIcon(symbol, if (active) Ink.yellow else Ink.white, size = size * 0.5f)
    }
}

/** The big round button in the middle of the bar – like Camera's shutter, here "Zeichnen". */
@Composable
fun DrawButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(78.dp).border(4.dp, Ink.white.copy(alpha = if (enabled) 1f else 0.35f), CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Zeichnen", onClick = onClick)
        .semantics { contentDescription = "Zeichnen" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Ink.white.copy(alpha = if (enabled) 1f else 0.35f)),
            contentAlignment = Alignment.Center) {
            SymbolIcon(Symbol.Pencil, Color.Black, size = 30.dp, weight = 1.9f)
        }
    }
}

/** An iOS slider: thin track, white fill on the left, round white thumb with a soft shadow. */
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, description: String, modifier: Modifier = Modifier, onRelease: () -> Unit = {}) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)
    Box(modifier.fillMaxWidth().height(44.dp)
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
            setProgress { target -> change(target.coerceIn(0f, 1f)); true }
        }
        .pointerInput(Unit) {
            detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)); release() }
        }
        .pointerInput(Unit) {
            detectDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { pointer, _ ->
                pointer.consume()
                change((pointer.position.x / size.width).coerceIn(0f, 1f))
            }
        }) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val thumb = 14.dp.toPx()
            val y = size.height / 2
            val left = thumb
            val right = size.width - thumb
            val x = left + (right - left) * current
            val track = 4.dp.toPx()
            drawRoundRect(Color(0x66787880), Offset(left, y - track / 2), Size(right - left, track), CornerRadius(track / 2))
            drawRoundRect(Ink.white, Offset(left, y - track / 2), Size(x - left, track), CornerRadius(track / 2))
            drawCircle(Color(0x33000000), thumb + 1.dp.toPx(), Offset(x, y + 1.dp.toPx()))
            drawCircle(Ink.white, thumb, Offset(x, y))
        }
    }
}

/** A sheet or page takes the touches on it (nothing underneath reacts), without merging its contents into one
 *  element for TalkBack – which `clickable(enabled = false)` would do. */
fun Modifier.keepTouches(): Modifier = this.pointerInput(Unit) {}
