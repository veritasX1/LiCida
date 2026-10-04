package io.github.veritasx1.licida

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The draw mode's further tools (card 12, handbook p. 32) – what is on stays on until switched off. */
data class Tools(val torch: Boolean = false, val split: Boolean = false, val splitAt: Float = 0.5f,
                 val flicker: Boolean = false, val flickerSpeed: Float = 0.4f, val sessionButton: Boolean = true)

/** The further tools as an iOS sheet (handbook p. 32: slides up, a tap elsewhere closes it). */
@Composable
fun MoreToolsSheet(tools: Tools, hasTorch: Boolean, onTools: (Tools) -> Unit, onSave: () -> Unit, onShare: () -> Unit,
                   onClose: () -> Unit, extra: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF21C1C1E))
            .clickable(enabled = false) {}.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)
            .verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Color(0x66EBEBF5)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Werkzeuge beim Zeichnen", style = style(17f, 600), modifier = Modifier.weight(1f))
                BasicText("Fertig", style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onClose).padding(4.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E))) {
                SwitchRow("Taschenlampe", if (hasTorch) "Licht auf die Zeichenfläche" else "Diese Kamera hat kein Licht", tools.torch, hasTorch) { onTools(tools.copy(torch = it)) }
                Line()
                SwitchRow("Geteilte Ansicht", "Links die Vorlage, rechts die Kamera – zum Farbvergleich", tools.split, true) { onTools(tools.copy(split = it)) }
                Line()
                SwitchRow("Flimmern", "Vorlage blendet ein und aus – stimmt die Farbe, verschwindet das Flimmern", tools.flicker, true) { onTools(tools.copy(flicker = it)) }
                if (tools.flicker) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Tempo", style = style(15f), modifier = Modifier.width(64.dp))
                    IosSlider(tools.flickerSpeed, { onTools(tools.copy(flickerSpeed = it)) }, "Tempo des Flimmerns", Modifier.weight(1f))
                }
                Line()
                SwitchRow("Knopf „Sitzung sichern“", "Oben rechts beim Zeichnen", tools.sessionButton, true) { onTools(tools.copy(sessionButton = it)) }
                extra()
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Wide("In Fotos sichern", Modifier.weight(1f), onSave)
                Box(Modifier.width(8.dp))
                Wide("Teilen …", Modifier.weight(1f), onShare)
            }
            BasicText("Sichern und Teilen nehmen ein Bild deiner Zeichenfläche auf, ohne Knöpfe. Geteilt wird nur, wohin du es selbst schickst.",
                style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp))
        }
    }
}

@Composable
fun SwitchRow(label: String, detail: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            BasicText(label, style = style(17f, 400, if (enabled) Ink.white else Ink.secondary))
            BasicText(detail, style = style(13f, 400, Ink.secondary))
        }
        IosSwitch(on && enabled, enabled = enabled, description = label, onChange = onChange)
    }
}

@Composable
fun Line() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Ink.separator))

@Composable
private fun Wide(label: String, modifier: Modifier, onClick: () -> Unit) {
    BasicText(label, style = style(17f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp))
}

/** The split's handle: a thin line with a round grip, dragged left and right (handbook p. 32). */
@Composable
fun SplitHandle(at: Float, onMove: (Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val x = (at * width).roundToInt()
        val grip = with(LocalDensity.current) { 22.dp.toPx() }.roundToInt()
        Box(Modifier.offset { IntOffset(x - 1, 0) }.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
        Box(Modifier.align(Alignment.CenterStart).offset { IntOffset(x - grip, 0) }.size(44.dp).clip(CircleShape).background(Color.White)
            .semantics { contentDescription = "Teiler verschieben" }
            .pointerInput(width) { detectDragGestures { change, drag -> change.consume(); onMove(((at * width + drag.x) / width).coerceIn(0.05f, 0.95f)) } },
            contentAlignment = Alignment.Center) {
            BasicText("‹ ›", style = style(15f, 700, Color.Black))
        }
    }
}

/** What the screen shows right now, as a picture (PixelCopy – includes the camera's view). */
fun captureScreen(activity: Activity, onDone: (Bitmap?) -> Unit) {
    val view = activity.window.decorView
    if (view.width == 0) return onDone(null)
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    runCatching {
        PixelCopy.request(activity.window, bitmap, { result -> onDone(if (result == PixelCopy.SUCCESS) bitmap else null) }, Handler(Looper.getMainLooper()))
    }.onFailure { onDone(null) }
}
