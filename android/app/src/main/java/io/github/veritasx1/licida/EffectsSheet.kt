package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The four colour sliders (card 4, handbook p. 10) as an iOS sheet: Abbrechen · Farbeffekte · Übernehmen,
 *  the sliders, Zurücksetzen. The picture above changes live; everything else waits until done. */
@Composable
fun EffectsSheet(effects: Effects, onChange: (Effects) -> Unit, onCancel: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF21C1C1E))
        .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Color(0x66EBEBF5)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Abbrechen"), style = style(17f, 400, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onCancel).padding(4.dp))
            BasicText(tr("Farbeffekte"), style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            BasicText(tr("Übernehmen"), style = style(17f, 600, if (effects.isNeutral) Ink.secondary else Ink.yellow),
                modifier = Modifier.clickable(enabled = !effects.isNeutral, role = Role.Button, onClick = onApply).padding(4.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).padding(vertical = 4.dp)) {
            Line(tr("Helligkeit"), "${(effects.brightness * 100).roundToInt()}", (effects.brightness + 1) / 2) { onChange(effects.copy(brightness = it * 2 - 1)) }
            Line(tr("Kontrast"), "${(effects.contrast * 100).roundToInt()} %", (effects.contrast - 0.5f) / 1.5f) { onChange(effects.copy(contrast = 0.5f + it * 1.5f)) }
            Line(tr("Sättigung"), "${(effects.saturation * 100).roundToInt()} %", effects.saturation / 2) { onChange(effects.copy(saturation = it * 2)) }
            Line(tr("Farbton"), "${effects.hue.roundToInt()}°", (effects.hue + 180) / 360) { onChange(effects.copy(hue = it * 360 - 180)) }
        }
        BasicText(tr("Zurücksetzen"), style = style(17f, 400, if (effects.isNeutral) Ink.secondary else Ink.yellow).copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E))
                .clickable(enabled = !effects.isNeutral, role = Role.Button) { onChange(Effects()) }.padding(vertical = 12.dp))
    }
}

@Composable
private fun Line(label: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(16f), modifier = Modifier.width(92.dp))
        IosSlider(fraction.coerceIn(0f, 1f), onChange, label, Modifier.weight(1f))
        BasicText(value, style = style(14f, 500, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.width(52.dp))
    }
}
