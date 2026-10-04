package io.github.veritasx1.licida

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** LiCida's symbols, drawn in the manner of Apple's SF Symbols (regular weight, round caps and joins,
 *  on a 24-unit grid) – Material icons would break the Apple look. Named after their SF counterparts. */
enum class Symbol {
    Photo,          // photo.on.rectangle
    Folder,         // folder
    Camera,         // camera
    RotateRight,    // rotate.right
    Save,           // square.and.arrow.down
    Pencil,         // pencil.tip / the draw button
    ChevronLeft,    // chevron.left
    Focus,          // viewfinder
    Sun,            // sun.max (exposure)
    Hand,           // hand.draw – the hint for gestures
    Aperture,       // camera.aperture – the camera settings
    Checkmark,      // checkmark
    Filters,        // camera.filters – the toolbox
    Undo,           // arrow.uturn.backward
    Redo,           // arrow.uturn.forward
    History,        // clock.arrow.circlepath
    Pipette,        // eyedropper
    Import,         // square.and.arrow.down.on.square – colours from a photo
    Wheel,          // paintpalette – the palette
    More,           // chevron.up – the draw mode's further tools
    Flashlight,     // flashlight.on.fill
    Share,          // square.and.arrow.up
    Ellipsis,       // ellipsis.circle – the setup's further choices
    Sessions,       // clock.arrow.trianglehead – a kept session: tray with arrow down
    Gear,           // gearshape – the settings page
    Maximize,       // arrow.up.left.and.arrow.down.right – the largest drawing
    Reset,          // arrow.counterclockwise – camera back to the start
}

@Composable
fun SymbolIcon(symbol: Symbol, color: Color, size: Dp = 22.dp, weight: Float = 1.7f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawSymbol(symbol, color, weight) }
}

fun DrawScope.drawSymbol(symbol: Symbol, color: Color, weight: Float = 1.7f) {
    val u = size.minDimension / 24f
    val stroke = Stroke(width = weight * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * u, cy * u, x * u, y * u)
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

    when (symbol) {
        Symbol.Photo -> {
            box(5f, 6f, 16f, 13f, 2.5f)
            path { m(3f, 16f); l(3f, 6.5f); q(3f, 4f, 5.5f, 4f); l(17f, 4f) }
            path { m(6.5f, 17f); l(10.5f, 12.5f); l(13.5f, 15.5f); l(15.5f, 13.5f); l(19.5f, 17f) }
            drawCircle(color, 1.4f * u, p(16.5f, 9.5f))
        }
        Symbol.Folder -> path {
            m(3f, 7f); q(3f, 5f, 5f, 5f); l(9.5f, 5f); l(11.5f, 7.5f); l(19f, 7.5f); q(21f, 7.5f, 21f, 9.5f)
            l(21f, 17f); q(21f, 19f, 19f, 19f); l(5f, 19f); q(3f, 19f, 3f, 17f); close()
            m(3f, 10f); l(21f, 10f)
        }
        Symbol.Camera -> {
            path {
                m(3f, 9f); q(3f, 7f, 5f, 7f); l(7.5f, 7f); l(9f, 5f); l(15f, 5f); l(16.5f, 7f); l(19f, 7f)
                q(21f, 7f, 21f, 9f); l(21f, 17f); q(21f, 19f, 19f, 19f); l(5f, 19f); q(3f, 19f, 3f, 17f); close()
            }
            drawCircle(color, 3.6f * u, p(12f, 12.8f), style = stroke)
        }
        Symbol.RotateRight -> {
            box(4f, 10f, 11f, 10f, 2f)
            path { m(10f, 6.5f); q(17f, 4f, 19.5f, 11.5f) }
            path { m(17.2f, 10.2f); l(19.6f, 12f); l(21.2f, 9.4f) }
        }
        Symbol.Save -> {
            path { m(12f, 3.5f); l(12f, 14f); m(8f, 10.5f); l(12f, 14.5f); l(16f, 10.5f) }
            path { m(8.5f, 8f); l(6f, 8f); q(4f, 8f, 4f, 10f); l(4f, 18.5f); q(4f, 20.5f, 6f, 20.5f); l(18f, 20.5f); q(20f, 20.5f, 20f, 18.5f); l(20f, 10f); q(20f, 8f, 18f, 8f); l(15.5f, 8f) }
        }
        Symbol.Pencil -> {
            rotate(45f, p(12f, 12f)) {
                box(9.6f, 2.5f, 4.8f, 14f, 1f)
                path { m(9.6f, 16.5f); l(12f, 21.5f); l(14.4f, 16.5f) }
                path { m(9.6f, 5.5f); l(14.4f, 5.5f) }
            }
        }
        Symbol.ChevronLeft -> path { m(15f, 4.5f); l(7.5f, 12f); l(15f, 19.5f) }
        Symbol.Focus -> {
            path { m(3.5f, 8.5f); l(3.5f, 5.5f); q(3.5f, 3.5f, 5.5f, 3.5f); l(8.5f, 3.5f) }
            path { m(15.5f, 3.5f); l(18.5f, 3.5f); q(20.5f, 3.5f, 20.5f, 5.5f); l(20.5f, 8.5f) }
            path { m(20.5f, 15.5f); l(20.5f, 18.5f); q(20.5f, 20.5f, 18.5f, 20.5f); l(15.5f, 20.5f) }
            path { m(8.5f, 20.5f); l(5.5f, 20.5f); q(3.5f, 20.5f, 3.5f, 18.5f); l(3.5f, 15.5f) }
            drawCircle(color, 1.4f * u, p(12f, 12f))
        }
        Symbol.Sun -> {
            drawCircle(color, 4f * u, p(12f, 12f), style = stroke)
            for (index in 0 until 8) {
                val angle = Math.toRadians(index * 45.0)
                val inner = 7f
                val outer = 9.6f
                drawLine(color, p(12f + inner * Math.cos(angle).toFloat(), 12f + inner * Math.sin(angle).toFloat()),
                    p(12f + outer * Math.cos(angle).toFloat(), 12f + outer * Math.sin(angle).toFloat()), weight * u, StrokeCap.Round)
            }
        }
        Symbol.Aperture -> {
            drawCircle(color, 9f * u, p(12f, 12f), style = stroke)
            // Six blades: lines from the rim, each turned 60°, around a small hexagon.
            for (index in 0 until 6) {
                val a = Math.toRadians(index * 60.0 - 90.0)
                val b = Math.toRadians(index * 60.0 - 30.0)
                drawLine(color, p(12f + 9f * Math.cos(a).toFloat(), 12f + 9f * Math.sin(a).toFloat()),
                    p(12f + 3.6f * Math.cos(b).toFloat(), 12f + 3.6f * Math.sin(b).toFloat()), weight * u, StrokeCap.Round)
            }
        }
        Symbol.Checkmark -> path { m(5f, 12.5f); l(10f, 17.5f); l(19f, 6.5f) }
        Symbol.Filters -> {
            drawCircle(color, 5.6f * u, p(12f, 8.6f), style = stroke)
            drawCircle(color, 5.6f * u, p(8.6f, 14.6f), style = stroke)
            drawCircle(color, 5.6f * u, p(15.4f, 14.6f), style = stroke)
        }
        Symbol.Undo -> {
            path { m(8.5f, 5f); l(4.5f, 9f); l(8.5f, 13f) }
            path { m(4.8f, 9f); l(14.5f, 9f); q(20f, 9f, 20f, 14.5f); q(20f, 20f, 14.5f, 20f); l(10f, 20f) }
        }
        Symbol.Redo -> {
            path { m(15.5f, 5f); l(19.5f, 9f); l(15.5f, 13f) }
            path { m(19.2f, 9f); l(9.5f, 9f); q(4f, 9f, 4f, 14.5f); q(4f, 20f, 9.5f, 20f); l(14f, 20f) }
        }
        Symbol.History -> {
            path { m(4.2f, 12f); q(4.2f, 4.2f, 12f, 4.2f); q(19.8f, 4.2f, 19.8f, 12f); q(19.8f, 19.8f, 12f, 19.8f); q(7.2f, 19.8f, 5.4f, 16f) }
            path { m(2.4f, 9.8f); l(4.2f, 12.4f); l(6.6f, 10f) }
            path { m(12f, 7.5f); l(12f, 12.4f); l(15.2f, 14.4f) }
        }
        Symbol.Pipette -> {
            // The bulb top right, the collar, the glass tube down to the tip.
            path { m(14.2f, 5.6f); q(16.6f, 3.2f, 18.9f, 5.1f); q(20.8f, 7.4f, 18.4f, 9.8f) }
            path { m(12.6f, 7.2f); l(16.8f, 11.4f) }
            path { m(15.2f, 9.8f); l(7.2f, 17.8f); q(5.6f, 19.4f, 4.4f, 19.6f); q(4.6f, 18.4f, 6.2f, 16.8f); l(14.2f, 8.8f) }
        }
        Symbol.Import -> {
            box(7f, 3.5f, 13.5f, 13.5f, 2.5f)
            path { m(13.75f, 6.5f); l(13.75f, 13f); m(10.8f, 10.2f); l(13.75f, 13.2f); l(16.7f, 10.2f) }
            path { m(4f, 7.5f); l(4f, 18f); q(4f, 20.5f, 6.5f, 20.5f); l(17f, 20.5f) }
        }
        Symbol.Wheel -> {
            path { m(12f, 3.5f); q(3.5f, 3.5f, 3.5f, 12f); q(3.5f, 20.5f, 12f, 20.5f); q(14f, 20.5f, 14f, 18.5f); q(14f, 16f, 16.5f, 16f); l(18f, 16f)
                q(20.5f, 16f, 20.5f, 12.5f); q(20.5f, 3.5f, 12f, 3.5f) }
            drawCircle(color, 1.4f * u, p(8f, 10f)); drawCircle(color, 1.4f * u, p(11.5f, 7f)); drawCircle(color, 1.4f * u, p(15.5f, 8.5f))
            drawCircle(color, 1.4f * u, p(8.5f, 14.5f))
        }
        Symbol.More -> path { m(5f, 15f); l(12f, 8f); l(19f, 15f) }
        Symbol.Flashlight -> {
            path { m(8f, 3.5f); l(16f, 3.5f); l(16f, 7f); l(14f, 10.5f); l(14f, 20.5f); l(10f, 20.5f); l(10f, 10.5f); l(8f, 7f); close() }
            path { m(8f, 7f); l(16f, 7f) }
            drawCircle(color, 1.1f * u, p(12f, 14f))
        }
        Symbol.Share -> {
            path { m(12f, 14.5f); l(12f, 3.5f); m(8f, 7.5f); l(12f, 3.5f); l(16f, 7.5f) }
            path { m(8.5f, 10f); l(6f, 10f); q(4f, 10f, 4f, 12f); l(4f, 18.5f); q(4f, 20.5f, 6f, 20.5f); l(18f, 20.5f); q(20f, 20.5f, 20f, 18.5f); l(20f, 12f); q(20f, 10f, 18f, 10f); l(15.5f, 10f) }
        }
        Symbol.Ellipsis -> {
            drawCircle(color, 9.5f * u, p(12f, 12f), style = stroke)
            for (x in listOf(8f, 12f, 16f)) drawCircle(color, 1.35f * u, p(x, 12f))
        }
        Symbol.Sessions -> {
            path { m(12f, 3.5f); l(12f, 13f); m(8.5f, 9.5f); l(12f, 13f); l(15.5f, 9.5f) }
            path { m(3.5f, 14f); l(3.5f, 18.5f); q(3.5f, 20.5f, 5.5f, 20.5f); l(18.5f, 20.5f); q(20.5f, 20.5f, 20.5f, 18.5f); l(20.5f, 14f)
                m(3.5f, 14f); l(8f, 14f); l(9f, 16.5f); l(15f, 16.5f); l(16f, 14f); l(20.5f, 14f) }
        }
        Symbol.Gear -> {
            // Eight teeth around a ring, a hole in the middle (gearshape).
            for (index in 0 until 8) {
                val a = Math.toRadians(index * 45.0)
                drawLine(color, p(12f + 6.6f * Math.cos(a).toFloat(), 12f + 6.6f * Math.sin(a).toFloat()),
                    p(12f + 9.2f * Math.cos(a).toFloat(), 12f + 9.2f * Math.sin(a).toFloat()), weight * 1.6f * u, StrokeCap.Round)
            }
            drawCircle(color, 6.6f * u, p(12f, 12f), style = stroke)
            drawCircle(color, 2.6f * u, p(12f, 12f), style = stroke)
        }
        Symbol.Maximize -> {
            path { m(4f, 10f); l(4f, 4f); l(10f, 4f); m(4f, 4f); l(10.5f, 10.5f) }
            path { m(20f, 14f); l(20f, 20f); l(14f, 20f); m(20f, 20f); l(13.5f, 13.5f) }
        }
        Symbol.Reset -> {
            path { m(5.2f, 9f); q(7.5f, 4.2f, 12.5f, 4.2f); q(19.8f, 4.6f, 19.8f, 12f); q(19.8f, 19.8f, 12f, 19.8f); q(6.4f, 19.8f, 4.6f, 14.8f) }
            path { m(4.6f, 4.6f); l(5f, 9.4f); l(9.6f, 8.6f) }
        }
        Symbol.Hand -> {
            // Two fingers spreading: the pinch hint.
            path { m(8f, 15f); l(4.5f, 8.5f); m(4.5f, 8.5f); l(4.5f, 12f); m(4.5f, 8.5f); l(8f, 8.5f) }
            path { m(16f, 9f); l(19.5f, 15.5f); m(19.5f, 15.5f); l(19.5f, 12f); m(19.5f, 15.5f); l(16f, 15.5f) }
            drawCircle(color, 2.2f * u, p(10.5f, 13.5f), style = stroke)
            drawCircle(color, 2.2f * u, p(13.5f, 10.5f), style = stroke)
        }
    }
}
