package io.github.veritasx1.licida

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A colour palette (card 11, handbook p. 34–40): the colours found in the picture ("before", the upper half of
 *  each swatch), what each becomes ("after", the lower half), the ones taken out, and how pixels are mapped:
 *  Ersetzen – each pixel to its nearest "before" colour, shown as that colour's "after" colour;
 *  Angleichen – each pixel straight to the nearest "after" colour. */
data class Palette(val before: List<Int>, val after: List<Int> = before, val removed: Set<Int> = emptySet(), val replace: Boolean = true) {
    val size get() = before.size
    private val active get() = before.indices.filter { it !in removed }

    /** Each pixel's palette entry (index) – the colour layers while drawing are built from these. */
    fun indices(image: Pixels): IntArray {
        val targets = if (replace) before else after
        val use = active.ifEmpty { before.indices.toList() }
        val labs = use.map { Colours.lab(targets[it]) }
        val cache = HashMap<Int, Int>()
        return IntArray(image.argb.size) { i ->
            val c = image.argb[i]
            if (c ushr 24 < 8) -1 else {
                // Colours differing only in the lowest bits look the same: one search per 15-bit colour.
                val key = (c shr 3) and 0x1F1F1F
                cache.getOrPut(key) {
                    val lab = Colours.lab(c)
                    use[labs.indices.minByOrNull { Colours.distance(lab, labs[it]) }!!]
                }
            }
        }
    }

    /** The picture in the palette's "after" colours; hidden layers (index in `hidden`) become transparent. */
    fun render(image: Pixels, hidden: Set<Int> = emptySet()): Pixels {
        val index = indices(image)
        return Pixels(image.width, image.height, IntArray(index.size) { i ->
            val k = index[i]
            if (k < 0 || k in hidden) 0 else after[k] or (0xFF shl 24)
        })
    }

    fun encode() = before.joinToString(",") { Integer.toHexString(it and 0xFFFFFF) } + "|" + after.joinToString(",") { Integer.toHexString(it and 0xFFFFFF) } +
        "|" + removed.joinToString(",") + "|" + (if (replace) "r" else "m")

    companion object {
        const val DEFAULT_COLOURS = 8
        const val MOST = 64

        fun decode(text: String?): Palette? {
            val parts = text?.split("|") ?: return null
            if (parts.size < 4) return null
            fun colours(list: String) = list.split(",").filter { it.isNotBlank() }.map { (it.toLong(16).toInt()) or (0xFF shl 24) }
            val before = colours(parts[0])
            val after = colours(parts[1])
            if (before.isEmpty() || before.size != after.size) return null
            return Palette(before, after, parts[2].split(",").mapNotNull { it.toIntOrNull() }.toSet(), parts[3] != "m")
        }

        /** The `count` colours that best represent the picture (k-means in Lab, k-means++ start, fixed seed:
         *  the same picture always gives the same palette). Fewer if the picture has fewer distinct colours. */
        fun find(image: Pixels, count: Int): Palette {
            val k = count.coerceIn(2, MOST)
            val samples = sample(image, 24000)
            val labs = samples.map { Colours.lab(it) }
            if (labs.isEmpty()) return Palette(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
            val random = java.util.Random(4711)
            val centres = mutableListOf(labs[random.nextInt(labs.size)])
            val nearest = DoubleArray(labs.size) { Colours.distance(labs[it], centres[0]) }
            while (centres.size < k) {
                val total = nearest.sum()
                if (total <= 1e-9) break  // fewer distinct colours than asked
                var pick = random.nextDouble() * total
                var chosen = 0
                while (chosen < labs.size - 1 && pick > nearest[chosen]) { pick -= nearest[chosen]; chosen++ }
                centres += labs[chosen]
                for (i in labs.indices) nearest[i] = min(nearest[i], Colours.distance(labs[i], labs[chosen]))
            }
            repeat(12) {
                val sums = Array(centres.size) { DoubleArray(3) }
                val counts = IntArray(centres.size)
                for (lab in labs) {
                    val c = centres.indices.minByOrNull { Colours.distance(lab, centres[it]) }!!
                    sums[c][0] += lab[0]; sums[c][1] += lab[1]; sums[c][2] += lab[2]; counts[c]++
                }
                for (c in centres.indices) if (counts[c] > 0) centres[c] = doubleArrayOf(sums[c][0] / counts[c], sums[c][1] / counts[c], sums[c][2] / counts[c])
            }
            val colours = centres.map { Colours.fromLab(it) }.distinct().sortedWith(compareBy({ Colours.hue(it) }, { Colours.lab(it)[0] }))
            return Palette(colours)
        }

        private fun sample(image: Pixels, most: Int): List<Int> {
            val step = max(1, image.argb.size / most)
            return (image.argb.indices step step).map { image.argb[it] }.filter { it ushr 24 >= 8 }
        }

        /** The palette as a colour wheel (a pie, sorted by hue or brightness) to print or keep (handbook p. 37). */
        fun wheel(colours: List<Int>, size: Int, byHue: Boolean): Pixels {
            val sorted = if (byHue) colours.sortedBy { Colours.hue(it) } else colours.sortedBy { Colours.lab(it)[0] }
            val centre = size / 2f
            val white = Filters.argb(255, 255, 255, 255)
            return Pixels(size, size, IntArray(size * size) { i ->
                val dx = i % size - centre; val dy = i / size - centre
                if (dx * dx + dy * dy > centre * centre * 0.92f) white else {
                    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450) % 360
                    sorted[((angle / 360 * sorted.size).toInt()).coerceIn(0, sorted.size - 1)] or (0xFF shl 24)
                }
            })
        }
    }
}

/** Colour arithmetic: sRGB ↔ CIE Lab (D65) – distances there match what the eye sees. */
object Colours {
    private fun linear(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
    private fun gamma(v: Double): Int { val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055; return (c * 255).roundToInt().coerceIn(0, 255) }
    private fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
    private fun fInverse(t: Double) = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)

    fun lab(argb: Int): DoubleArray {
        val r = linear((argb shr 16) and 255); val g = linear((argb shr 8) and 255); val b = linear(argb and 255)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun fromLab(lab: DoubleArray): Int {
        val fy = (lab[0] + 16) / 116; val fx = fy + lab[1] / 500; val fz = fy - lab[2] / 200
        val x = fInverse(fx) * 0.95047; val y = fInverse(fy); val z = fInverse(fz) * 1.08883
        val r = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val g = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val b = 0.0557 * x - 0.2040 * y + 1.0570 * z
        return Filters.argb(255, gamma(r.coerceIn(0.0, 1.0)), gamma(g.coerceIn(0.0, 1.0)), gamma(b.coerceIn(0.0, 1.0)))
    }

    fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]; val da = a[1] - b[1]; val db = a[2] - b[2]
        return dl * dl + da * da + db * db
    }

    /** Hue 0–360 (greys sort first). */
    fun hue(argb: Int): Double {
        val r = ((argb shr 16) and 255) / 255.0; val g = ((argb shr 8) and 255) / 255.0; val b = (argb and 255) / 255.0
        val high = max(r, max(g, b)); val low = min(r, min(g, b))
        if (high - low < 0.06) return -1.0 + high / 10
        val h = when (high) {
            r -> 60 * (((g - b) / (high - low)) % 6)
            g -> 60 * ((b - r) / (high - low) + 2)
            else -> 60 * ((r - g) / (high - low) + 4)
        }
        return (h + 360) % 360
    }

    /** HSB ↔ colour, for the colour picker's sliders. */
    fun fromHsb(hue: Float, saturation: Float, brightness: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

    fun toHsb(argb: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
}
