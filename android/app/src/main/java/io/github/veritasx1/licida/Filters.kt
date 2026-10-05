package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A picture as plain pixels (ARGB ints) – the filters work on this, without Android, so they are
 *  tested directly (FiltersTest). */
class Pixels(val width: Int, val height: Int, val argb: IntArray = IntArray(width * height)) {
    fun copy() = Pixels(width, height, argb.copyOf())
    operator fun get(x: Int, y: Int) = argb[y * width + x]
}

/** The toolbox of Camera Lucida (handbook p. 27–29). Each filter works on what you see now, so they
 *  stack; some take a value (Tontrennung: shades 2–16). The ids are stored in the history and in the
 *  three custom slots – never rename them. */
enum class Filter(val id: String, val label: String, val hint: String, val takesValue: Boolean = false, val defaultValue: Int = 0) {
    /** Opens the four colour sliders (card 4); as a step it carries their values. */
    ColourEffects("effects", tr("Farbeffekte"), tr("Helligkeit, Kontrast, Sättigung, Farbton")),
    /** Opens the palette editor (card 11); as a step it carries its palette. */
    ColourPalette("palette", tr("Farbpalette"), tr("Wenige Farben, einzeln ein- und ausblendbar")),
    Colors64("colors64", tr("64 Farben"), tr("Auf 64 Farben reduziert")),
    Sepia("sepia", tr("Sepia"), tr("Bräunlich wie alte Fotos")),
    Gray("gray", tr("Graustufen"), tr("Ohne Farbe")),
    Posterize("posterize", tr("Tontrennung"), tr("2 bis 16 Grautöne"), takesValue = true, defaultValue = 4),
    Wash("wash", tr("Lasurwerte"), tr("Grauwerte zum Schichten von Lasuren")),
    ComicColor("comic", tr("Comic"), tr("Wenig Farben, Pastell, schwarze Tusche")),
    ComicBw("comicbw", tr("Comic s/w"), tr("Schwarzweiß mit Tusche")),
    Neon("neon", tr("Neon"), tr("Leuchtende Linien auf Schwarz")),
    Blur("blur", tr("Weichzeichnen"), tr("Glättet – mehrmals für mehr")),
    Edges("edges", tr("Kanten"), tr("Schwarze Linien, sonst durchsichtig")),
    Threshold("threshold", tr("Schwellwert"), tr("Nur Schwarz und Weiß – nochmal: umgekehrt")),
    Normalize("normalize", tr("Normalisieren"), tr("Grau mit gedehntem Kontrast")),
    Equalize("equalize", tr("Ausgleichen"), tr("Mehr Kontrast, gleichmäßig verteilt")),
    Mirror("mirror", tr("Spiegeln"), tr("Links und rechts tauschen")),
    Ticks("ticks", tr("Eckmarken"), tr("Passkreuze in den Ecken")),
    GridSmall("gridsmall", tr("Kleines Raster"), tr("Gleich große Quadrate")),
    GridLarge("gridlarge", tr("Großes Raster"), tr("Große Felder zum Aufteilen"));

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

object Filters {
    private fun a(c: Int) = c ushr 24
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    private val BLACK = argb(255, 0, 0, 0)
    private val WHITE = argb(255, 255, 255, 255)

    /** Linear luminance (Rec. 709) – handbook: "uses linear algorithm to convert color to grayscale". */
    fun luma(c: Int) = (0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)).roundToInt()

    /** One step; `previous` is the filter applied right before (Schwellwert twice in a row inverts). */
    fun apply(image: Pixels, filter: Filter, value: Int = filter.defaultValue, previous: Filter? = null): Pixels = when (filter) {
        Filter.ColourEffects -> Effects(saturation = 1.6f, contrast = 1.15f).apply(image)  // preview; the real step carries its values
        Filter.ColourPalette -> Palette.find(image, Palette.DEFAULT_COLOURS).render(image)  // preview; the real step carries its palette
        Filter.Colors64 -> map(image) { c -> argb(a(c), q(r(c), 4), q(g(c), 4), q(b(c), 4)) }
        Filter.Sepia -> map(image) { c ->
            val rr = r(c); val gg = g(c); val bb = b(c)
            argb(a(c), (0.393 * rr + 0.769 * gg + 0.189 * bb).roundToInt(), (0.349 * rr + 0.686 * gg + 0.168 * bb).roundToInt(),
                (0.272 * rr + 0.534 * gg + 0.131 * bb).roundToInt())
        }
        Filter.Gray -> map(image) { c -> luma(c).let { argb(a(c), it, it, it) } }
        Filter.Posterize -> map(image) { c -> q(luma(c), value.coerceIn(2, 16)).let { argb(a(c), it, it, it) } }
        Filter.Wash -> wash(image)
        Filter.ComicColor -> ink(map(image) { c ->
            argb(a(c), pastel(q(r(c), 4)), pastel(q(g(c), 4)), pastel(q(b(c), 4)))
        }, edges(blur(image)), 90)
        Filter.ComicBw -> ink(map(image) { c -> q(luma(c), 3).let { argb(a(c), it, it, it) } }, edges(blur(image)), 90)
        Filter.Neon -> neon(image)
        Filter.Blur -> blur(image)
        Filter.Edges -> edges(blur(image)).let { strength ->
            Pixels(image.width, image.height, IntArray(image.argb.size) { i -> if (strength[i] > 70) BLACK else 0 })
        }
        Filter.Threshold -> {
            val inverted = previous == Filter.Threshold
            map(image) { c -> if ((luma(c) >= 128) != inverted) WHITE else BLACK }
        }
        Filter.Normalize -> normalize(image)
        Filter.Equalize -> equalize(image)
        Filter.Mirror -> Pixels(image.width, image.height, IntArray(image.argb.size) { i ->
            val y = i / image.width; val x = i % image.width
            image.argb[y * image.width + (image.width - 1 - x)]
        })
        Filter.Ticks -> ticks(image)
        Filter.GridSmall -> grid(image, 8)
        Filter.GridLarge -> grid(image, 3)
    }

    private inline fun map(image: Pixels, f: (Int) -> Int) = Pixels(image.width, image.height, IntArray(image.argb.size) { f(image.argb[it]) })

    /** Quantize 0..255 to `levels` evenly spaced values (0 and 255 included). */
    fun q(value: Int, levels: Int): Int {
        val step = 255.0 / (levels - 1)
        return ((value / step).roundToInt() * step).roundToInt().coerceIn(0, 255)
    }

    private fun pastel(value: Int) = (value * 0.8 + 255 * 0.2).roundToInt()

    /** Gaussian blur, radius 2 (1-4-6-4-1), separable; the edge pixels repeat. */
    fun blur(image: Pixels): Pixels {
        val w = image.width; val h = image.height
        val kernel = intArrayOf(1, 4, 6, 4, 1)
        fun pass(src: IntArray, horizontal: Boolean): IntArray = IntArray(src.size) { i ->
            val x = i % w; val y = i / w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (k in -2..2) {
                val c = if (horizontal) src[y * w + (x + k).coerceIn(0, w - 1)] else src[(y + k).coerceIn(0, h - 1) * w + x]
                val weight = kernel[k + 2]
                sa += a(c) * weight; sr += r(c) * weight; sg += g(c) * weight; sb += b(c) * weight
            }
            argb(sa / 16, sr / 16, sg / 16, sb / 16)
        }
        return Pixels(w, h, pass(pass(image.argb, true), false))
    }

    /** Edge strength 0..255 per pixel (Sobel on luminance). */
    fun edges(image: Pixels): IntArray {
        val w = image.width; val h = image.height
        val l = IntArray(image.argb.size) { luma(image.argb[it]) }
        fun at(x: Int, y: Int) = l[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        return IntArray(l.size) { i ->
            val x = i % w; val y = i / w
            val gx = -at(x - 1, y - 1) - 2 * at(x - 1, y) - at(x - 1, y + 1) + at(x + 1, y - 1) + 2 * at(x + 1, y) + at(x + 1, y + 1)
            val gy = -at(x - 1, y - 1) - 2 * at(x, y - 1) - at(x + 1, y - 1) + at(x - 1, y + 1) + 2 * at(x, y + 1) + at(x + 1, y + 1)
            min(255, (sqrt((gx * gx + gy * gy).toDouble()) / 4).roundToInt())
        }
    }

    /** Black "ink" where the edges are strong enough. */
    private fun ink(image: Pixels, strength: IntArray, limit: Int) =
        Pixels(image.width, image.height, IntArray(image.argb.size) { i -> if (strength[i] > limit) BLACK else image.argb[i] })

    /** Brightened colors only along the lines, on black. */
    private fun neon(image: Pixels): Pixels {
        val strength = edges(blur(image))
        return Pixels(image.width, image.height, IntArray(image.argb.size) { i ->
            val c = image.argb[i]
            val m = max(r(c), max(g(c), b(c))).coerceAtLeast(1)
            val boost = 255.0 / m  // full brightness, same hue
            val s = min(1.0, strength[i] / 80.0)
            argb(255, (r(c) * boost * s).roundToInt(), (g(c) * boost * s).roundToInt(), (b(c) * boost * s).roundToInt())
        })
    }

    /** Wash values: five grey values as layers of the same transparent wash – light 1 layer, dark 4 (handbook p. 28). */
    private fun wash(image: Pixels): Pixels {
        val layer = 0.78  // each wash lets this much light through
        return map(image) { c ->
            val layers = 4 - (luma(c) * 5 / 256)          // 0 (white paper) … 4 (darkest)
            val v = (255 * Math.pow(layer, layers.toDouble())).roundToInt()
            argb(a(c), v, v, v)
        }
    }

    private fun percentile(histogram: IntArray, total: Int, fraction: Double): Int {
        var sum = 0
        for (v in 0..255) { sum += histogram[v]; if (sum >= total * fraction) return v }
        return 255
    }

    private fun histogram(image: Pixels) = IntArray(256).also { h -> image.argb.forEach { h[luma(it)]++ } }

    /** Grey with the contrast stretched between the darkest and lightest percent. */
    private fun normalize(image: Pixels): Pixels {
        val h = histogram(image)
        val low = percentile(h, image.argb.size, 0.01)
        val high = max(low + 1, percentile(h, image.argb.size, 0.99))
        return map(image) { c -> ((luma(c) - low) * 255.0 / (high - low)).roundToInt().coerceIn(0, 255).let { argb(a(c), it, it, it) } }
    }

    /** Histogram equalization: the grey values spread evenly over 0–255. */
    private fun equalize(image: Pixels): Pixels {
        val h = histogram(image)
        val cdf = IntArray(256)
        var sum = 0
        for (v in 0..255) { sum += h[v]; cdf[v] = sum }
        val first = cdf.first { it > 0 }
        val total = image.argb.size
        return map(image) { c ->
            val v = if (total == first) luma(c) else ((cdf[luma(c)] - first) * 255.0 / (total - first)).roundToInt()
            argb(a(c), v, v, v)
        }
    }

    private fun line(out: IntArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, thickness: Int, color: Int) {
        for (y in min(y0, y1) - thickness / 2..max(y0, y1) + thickness / 2) for (x in min(x0, x1) - thickness / 2..max(x0, x1) + thickness / 2) {
            if (x in 0 until w && y in 0 until h) out[y * w + x] = color
        }
    }

    /** Cross hairs near each corner – reference marks for aligning later (handbook p. 28). */
    private fun ticks(image: Pixels): Pixels {
        val out = image.argb.copyOf()
        val w = image.width; val h = image.height
        val inset = max(8, min(w, h) / 20)
        val arm = max(6, min(w, h) / 30)
        val thickness = max(1, min(w, h) / 400)
        for ((cx, cy) in listOf(inset to inset, w - 1 - inset to inset, inset to h - 1 - inset, w - 1 - inset to h - 1 - inset)) {
            line(out, w, h, cx - arm, cy, cx + arm, cy, thickness, BLACK)
            line(out, w, h, cx, cy - arm, cx, cy + arm, thickness, BLACK)
        }
        return Pixels(w, h, out)
    }

    /** Equal squares: `across` cells along the shorter side (handbook p. 28: small and large grid). */
    fun grid(image: Pixels, across: Int): Pixels {
        val out = image.argb.copyOf()
        val w = image.width; val h = image.height
        val cell = min(w, h) / across.toDouble()
        val thickness = max(1, min(w, h) / 500)
        var x = 0.0
        while (x <= w) { line(out, w, h, x.roundToInt(), 0, x.roundToInt(), h - 1, thickness, BLACK); x += cell }
        var y = 0.0
        while (y <= h) { line(out, w, h, 0, y.roundToInt(), w - 1, y.roundToInt(), thickness, BLACK); y += cell }
        return Pixels(w, h, out)
    }
}

/** The four colour sliders of the setup (card 4, handbook p. 10): brightness −1…1, contrast 0.5…2, saturation 0…2,
 *  hue −180…180°. One 4×5 colour matrix (as Android's ColorMatrix): shown live by the graphics chip while sliding,
 *  computed on the pixels when taken over. */
data class Effects(val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f, val hue: Float = 0f) {
    val isNeutral get() = brightness == 0f && contrast == 1f && saturation == 1f && hue == 0f

    fun matrix(): FloatArray {
        // Saturation (luminance weights Rec. 709), then hue rotation (about the grey axis), then contrast and brightness.
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        val s = saturation
        val sat = floatArrayOf(lr * (1 - s) + s, lg * (1 - s), lb * (1 - s), lr * (1 - s), lg * (1 - s) + s, lb * (1 - s), lr * (1 - s), lg * (1 - s), lb * (1 - s) + s)
        val a = Math.toRadians(hue.toDouble())
        val c = kotlin.math.cos(a).toFloat(); val n = kotlin.math.sin(a).toFloat()
        val rot = floatArrayOf(
            lr + c * (1 - lr) + n * (-lr), lg + c * (-lg) + n * (-lg), lb + c * (-lb) + n * (1 - lb),
            lr + c * (-lr) + n * 0.143f, lg + c * (1 - lg) + n * 0.140f, lb + c * (-lb) + n * (-0.283f),
            lr + c * (-lr) + n * (-(1 - lr)), lg + c * (-lg) + n * lg, lb + c * (1 - lb) + n * lb)
        val m = FloatArray(9) { i -> (0 until 3).sumOf { k -> (rot[(i / 3) * 3 + k] * sat[k * 3 + i % 3]).toDouble() }.toFloat() }
        val offset = 128f * (1 - contrast) + brightness * 100f
        return floatArrayOf(m[0] * contrast, m[1] * contrast, m[2] * contrast, 0f, offset,
            m[3] * contrast, m[4] * contrast, m[5] * contrast, 0f, offset,
            m[6] * contrast, m[7] * contrast, m[8] * contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f)
    }

    fun apply(image: Pixels): Pixels {
        val m = matrix()
        return Pixels(image.width, image.height, IntArray(image.argb.size) { i ->
            val p = image.argb[i]
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            Filters.argb(p ushr 24, (m[0] * r + m[1] * g + m[2] * b + m[4]).roundToInt(), (m[5] * r + m[6] * g + m[7] * b + m[9]).roundToInt(),
                (m[10] * r + m[11] * g + m[12] * b + m[14]).roundToInt())
        })
    }

    fun encode() = "$brightness;$contrast;$saturation;$hue"

    companion object {
        fun decode(text: String?): Effects? = text?.split(";")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let { Effects(it[0], it[1], it[2], it[3]) }
    }
}
