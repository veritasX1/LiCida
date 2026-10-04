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
        Symbol.Hand -> {
            // Two fingers spreading: the pinch hint.
            path { m(8f, 15f); l(4.5f, 8.5f); m(4.5f, 8.5f); l(4.5f, 12f); m(4.5f, 8.5f); l(8f, 8.5f) }
            path { m(16f, 9f); l(19.5f, 15.5f); m(19.5f, 15.5f); l(19.5f, 12f); m(19.5f, 15.5f); l(16f, 15.5f) }
            drawCircle(color, 2.2f * u, p(10.5f, 13.5f), style = stroke)
            drawCircle(color, 2.2f * u, p(13.5f, 10.5f), style = stroke)
        }
    }
}
