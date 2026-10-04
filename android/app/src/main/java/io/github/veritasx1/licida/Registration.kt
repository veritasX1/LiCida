package io.github.veritasx1.licida

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** How the paper moved between two camera pictures: turned by `angle` degrees and scaled by `scale` about the
 *  picture's centre, then shifted by (`dx`, `dy`) pixels. A point of the old picture lands at
 *  centre + shift + scale·turn(point − centre) in the new one. `score` is the match (1 = identical). */
data class Shift(val dx: Float, val dy: Float, val scale: Float, val angle: Float, val score: Float = 1f) {
    /** The reference placed on the old picture, carried along to the new one (both turn about the screen's centre). */
    fun carry(placement: Placement): Placement {
        val a = Math.toRadians(angle.toDouble())
        val x = placement.offset.x; val y = placement.offset.y
        return Placement(placement.scale * scale, placement.rotation + angle, androidx.compose.ui.geometry.Offset(
            dx + scale * (x * cos(a) - y * sin(a)).toFloat(), dy + scale * (x * sin(a) + y * cos(a)).toFloat()))
    }
}

/** Finding a saved session's paper again (card 14, handbook p. 44): the snapshot taken when saving is matched
 *  against the camera now – coarse to fine, every turn, size and shift tried on a tiny copy, then refined on
 *  larger ones. Compared by normalised correlation, so another light does not matter. */
object Registration {
    private class Grey(val width: Int, val height: Int, val v: FloatArray) {
        operator fun get(x: Int, y: Int) = v[y * width + x]
    }

    /** A box-averaged grey copy `width` wide – averaging also smooths away noise and fine paper texture. */
    private fun grey(image: Pixels, width: Int, height: Int): Grey {
        val out = FloatArray(width * height)
        for (ty in 0 until height) {
            val y0 = ty * image.height / height; val y1 = max(y0 + 1, (ty + 1) * image.height / height)
            for (tx in 0 until width) {
                val x0 = tx * image.width / width; val x1 = max(x0 + 1, (tx + 1) * image.width / width)
                var sum = 0f; var n = 0
                var y = y0
                while (y < y1) {
                    var x = x0
                    while (x < x1) {
                        val p = image.argb[y * image.width + x]
                        sum += 0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)
                        n++; x++
                    }
                    y++
                }
                out[ty * width + tx] = sum / n
            }
        }
        return Grey(width, height, out)
    }

    /** Correlation of the new picture with the old one carried by the shift (shift in this level's pixels). */
    private fun score(old: Grey, new: Grey, dx: Float, dy: Float, scale: Float, angle: Float, step: Int): Float {
        val a = Math.toRadians(-angle.toDouble())
        val c = cos(a).toFloat() / scale; val s = sin(a).toFloat() / scale
        val cx = new.width / 2f; val cy = new.height / 2f
        val ox = old.width / 2f; val oy = old.height / 2f
        var n = 0; var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0
        var y = 0
        while (y < new.height) {
            var x = 0
            while (x < new.width) {
                // Where this new pixel was in the old picture.
                val px = x - cx - dx; val py = y - cy - dy
                val qx = (ox + c * px - s * py).toInt(); val qy = (oy + s * px + c * py).toInt()
                if (qx >= 0 && qy >= 0 && qx < old.width && qy < old.height) {
                    val va = old[qx, qy].toDouble(); val vb = new[x, y].toDouble()
                    sa += va; sb += vb; saa += va * va; sbb += vb * vb; sab += va * vb; n++
                }
                x += step
            }
            y += step
        }
        // At least a third of the picture has to overlap – a sliver can match anything.
        if (n < (new.width / step) * (new.height / step) / 3) return -1f
        val va = saa - sa * sa / n; val vb = sbb - sb * sb / n
        if (va <= 1e-6 || vb <= 1e-6) return -1f
        return ((sab - sa * sb / n) / sqrt(va * vb)).toFloat()
    }

    /** The shift from `old` to `new` (both the same camera picture size, else `old` is fitted to `new`), in `new`'s
     *  pixels; null when nothing matches well enough – then LiCida says so instead of guessing. */
    fun find(old: Pixels, new: Pixels, maxAngle: Float = 15f, minScore: Float = 0.5f): Shift? {
        val levels = listOf(48, 96, 192).map { w -> w to max(1, (w.toLong() * new.height / new.width).toInt()) }
        var best = Shift(0f, 0f, 1f, 0f, -1f)
        // Coarse: everything, on the tiny copy.
        run {
            val (w, h) = levels[0]
            val o = grey(old, w, h); val n = grey(new, w, h)
            val reach = (min(w, h) * 0.3f).toInt()
            var angle = -maxAngle
            while (angle <= maxAngle + 1e-3f) {
                var scale = 0.85f
                while (scale <= 1.151f) {
                    for (dy in -reach..reach) for (dx in -reach..reach) {
                        val sc = score(o, n, dx.toFloat(), dy.toFloat(), scale, angle, 2)
                        if (sc > best.score) best = Shift(dx.toFloat(), dy.toFloat(), scale, angle, sc)
                    }
                    scale += 0.05f
                }
                angle += 2.5f
            }
        }
        // Finer: around the best, on each larger copy (the shift doubles with the size).
        var angleStep = 1.25f; var scaleStep = 0.025f
        for (level in 1 until levels.size) {
            val (w, h) = levels[level]
            val factor = w.toFloat() / levels[level - 1].first
            val o = grey(old, w, h); val n = grey(new, w, h)
            val start = best.copy(dx = best.dx * factor, dy = best.dy * factor)
            best = start.copy(score = score(o, n, start.dx, start.dy, start.scale, start.angle, 1))
            repeat(2) {
                val around = best
                for (ia in -2..2) for (isc in -2..2) {
                    val angle = around.angle + ia * angleStep / 2; val scale = around.scale + isc * scaleStep / 2
                    for (dy in -2..2) for (dx in -2..2) {
                        val sx = around.dx + dx; val sy = around.dy + dy
                        val sc = score(o, n, sx, sy, scale, angle, if (level == levels.size - 1) 2 else 1)
                        if (sc > best.score) best = Shift(sx, sy, scale, angle, sc)
                    }
                }
            }
            angleStep /= 2; scaleStep /= 2
        }
        if (best.score < minScore) return null
        val full = new.width.toFloat() / levels.last().first
        return best.copy(dx = best.dx * full, dy = best.dy * full)
    }
}
