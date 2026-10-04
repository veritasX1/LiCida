package io.github.veritasx1.licida

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The target for straightening the camera (cards 6/7, handbook p. 17–24): an A4 sheet with four big black
 *  dots near the corners, a fine grid and a centre cross. Printed (or shown on another screen) and laid flat
 *  on the drawing surface; LiCida finds the four dots and straightens the camera picture. Instead of a
 *  printer one's own flat picture works too (a magazine): then its four corners are found. */
object Target {
    // A4 portrait in millimetres.
    const val WIDTH = 210f
    const val HEIGHT = 297f
    const val RADIUS = 18f
    /** The dots' centres: top left, top right, bottom left, bottom right. */
    val DOTS = floatArrayOf(35f, 35f, 175f, 35f, 35f, 262f, 175f, 262f)
    /** A small marker dot beside the top-left one: tells up from down and the mirror image (a mirror over the
     *  front camera turns the picture – the four big dots alone look the same either way). */
    val MARKER = floatArrayOf(70f, 35f)
    const val MARKER_RADIUS = 8f
    /** The dots form a rectangle this wide per unit of height. */
    val DOT_ASPECT = (DOTS[2] - DOTS[0]) / (DOTS[5] - DOTS[1])

    /** The target as plain pixels (the dots, the grid lightly, the centre cross) – tests and the ghost picture. */
    fun pixels(width: Int, height: Int): Pixels {
        val sx = width / WIDTH; val sy = height / HEIGHT
        val white = Filters.argb(255, 255, 255, 255); val black = Filters.argb(255, 0, 0, 0); val grid = Filters.argb(255, 200, 200, 200)
        return Pixels(width, height, IntArray(width * height) { i ->
            val x = (i % width) / sx; val y = (i / width) / sy
            val dot = (0 until 4).any { p -> val dx = x - DOTS[2 * p]; val dy = y - DOTS[2 * p + 1]; dx * dx + dy * dy <= RADIUS * RADIUS } ||
                ((x - MARKER[0]) * (x - MARKER[0]) + (y - MARKER[1]) * (y - MARKER[1]) <= MARKER_RADIUS * MARKER_RADIUS)
            val cross = (abs(x - WIDTH / 2) < 0.6f && abs(y - HEIGHT / 2) < 12f) || (abs(y - HEIGHT / 2) < 0.6f && abs(x - WIDTH / 2) < 12f)
            val line = (x % 10f < 0.35f) || (y % 10f < 0.35f)
            when { dot || cross -> black; line -> grid; else -> white }
        })
    }

    /** Otsu's threshold: the grey value that best splits dark from light. */
    fun otsu(luma: IntArray): Int {
        val histogram = IntArray(256).also { h -> luma.forEach { h[it]++ } }
        val total = luma.size
        val sum = (0..255).sumOf { it.toLong() * histogram[it] }
        var below = 0L; var count = 0; var best = 0.0; var threshold = 128
        for (t in 0..255) {
            count += histogram[t]
            if (count == 0) continue
            val above = total - count
            if (above == 0) break
            below += t.toLong() * histogram[t]
            val meanBelow = below.toDouble() / count
            val meanAbove = (sum - below).toDouble() / above
            val between = count.toDouble() * above * (meanBelow - meanAbove) * (meanBelow - meanAbove)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    private class Blob(var area: Int = 0, var sx: Long = 0, var sy: Long = 0, var minX: Int = Int.MAX_VALUE, var maxX: Int = -1,
                       var minY: Int = Int.MAX_VALUE, var maxY: Int = -1, var border: Boolean = false,
                       var tl: Int = Int.MAX_VALUE, var br: Int = Int.MIN_VALUE, var tr: Int = Int.MIN_VALUE, var bl: Int = Int.MAX_VALUE,
                       val corners: IntArray = IntArray(8))

    /** Connected regions of `mask` (4-neighbourhood), with their size, centre, box and extreme corners. */
    private fun blobs(mask: BooleanArray, width: Int, height: Int): List<Blob> {
        val label = IntArray(mask.size)
        val result = mutableListOf<Blob>()
        val queue = IntArray(mask.size)
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            val blob = Blob()
            result += blob
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = result.size
            while (head < tail) {
                val i = queue[head++]
                val x = i % width; val y = i / width
                blob.area++; blob.sx += x; blob.sy += y
                blob.minX = min(blob.minX, x); blob.maxX = max(blob.maxX, x); blob.minY = min(blob.minY, y); blob.maxY = max(blob.maxY, y)
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) blob.border = true
                if (x + y < blob.tl) { blob.tl = x + y; blob.corners[0] = x; blob.corners[1] = y }
                if (x - y > blob.tr) { blob.tr = x - y; blob.corners[2] = x; blob.corners[3] = y }
                if (x - y < blob.bl) { blob.bl = x - y; blob.corners[4] = x; blob.corners[5] = y }
                if (x + y > blob.br) { blob.br = x + y; blob.corners[6] = x; blob.corners[7] = y }
                for (n in intArrayOf(i - 1, i + 1, i - width, i + width)) {
                    if (n < 0 || n >= mask.size) continue
                    if ((n == i - 1 && x == 0) || (n == i + 1 && x == width - 1)) continue
                    if (mask[n] && label[n] == 0) { label[n] = result.size; queue[tail++] = n }
                }
            }
        }
        return result
    }

    /** What the camera sees of the printed target: the four dots' centres (TL, TR, BL, BR as seen) and the marker. */
    class Seen(val dots: FloatArray, val marker: Pair<Float, Float>?)

    fun seeDots(image: Pixels): Seen? {
        val luma = IntArray(image.argb.size) { Filters.luma(image.argb[it]) }
        val threshold = otsu(luma)
        val mask = BooleanArray(luma.size) { luma[it] <= threshold }  // Otsu's value belongs to the dark side
        val minArea = image.argb.size * 0.00005
        val round = blobs(mask, image.width, image.height).filter { blob ->
            val w = blob.maxX - blob.minX + 1; val h = blob.maxY - blob.minY + 1
            val fill = blob.area.toDouble() / (w * h)
            !blob.border && blob.area >= minArea && fill in 0.6..0.92 && w.toDouble() / h in 0.3..3.3
        }.sortedByDescending { it.area }
        val big = round.take(4)
        if (big.size < 4 || big.last().area < big.first().area * 0.2) return null
        val centres = big.map { Pair(it.sx.toFloat() / it.area, it.sy.toFloat() / it.area) }
        // The marker: a round blob beside one big dot and clearly smaller than that one (~1/5 of its area) –
        // compared with its neighbour, because in perspective near dots look much bigger than far ones.
        val marker = round.drop(4).firstOrNull { blob ->
            val x = blob.sx.toFloat() / blob.area; val y = blob.sy.toFloat() / blob.area
            val near = big.minByOrNull { (it.sx.toFloat() / it.area - x).let { d -> d * d } + (it.sy.toFloat() / it.area - y).let { d -> d * d } }!!
            val distance = kotlin.math.hypot((near.sx.toFloat() / near.area - x).toDouble(), (near.sy.toFloat() / near.area - y).toDouble())
            blob.area.toDouble() / near.area in 0.08..0.5 && distance < 4 * kotlin.math.sqrt(near.area.toDouble())
        }?.let { Pair(it.sx.toFloat() / it.area, it.sy.toFloat() / it.area) }
        return Seen(order(centres), marker)
    }

    /** The four dots, ordered as the target's TL, TR, BL, BR – turned and mirrored as the marker says. */
    fun findDots(image: Pixels): FloatArray? {
        val seen = seeDots(image) ?: return null
        return orient(seen.dots, seen.marker)
    }

    // The eight ways a rectangle can lie (turned, mirrored): which seen corner is the target's TL, TR, BL, BR.
    private val SYMMETRIES = listOf(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 1, 0),
        intArrayOf(0, 2, 1, 3), intArrayOf(2, 0, 3, 1), intArrayOf(1, 3, 0, 2), intArrayOf(3, 1, 2, 0))

    fun orient(dots: FloatArray, marker: Pair<Float, Float>?): FloatArray {
        if (marker == null) return dots
        val best = SYMMETRIES.minByOrNull { order ->
            val assigned = FloatArray(8) { i -> dots[2 * order[i / 2] + i % 2] }
            val h = Homography.fromPoints(assigned, DOTS) ?: return@minByOrNull Float.MAX_VALUE
            val (mx, my) = Homography.apply(h, marker.first, marker.second)
            (mx - MARKER[0]) * (mx - MARKER[0]) + (my - MARKER[1]) * (my - MARKER[1])
        }!!
        return FloatArray(8) { i -> dots[2 * best[i / 2] + i % 2] }
    }

    /** One's own flat picture (a magazine): the four corners of the largest region that stands out. */
    fun findCorners(image: Pixels): FloatArray? {
        val luma = IntArray(image.argb.size) { Filters.luma(image.argb[it]) }
        val threshold = otsu(luma)
        val candidates = listOf(BooleanArray(luma.size) { luma[it] <= threshold }, BooleanArray(luma.size) { luma[it] > threshold })
            .flatMap { blobs(it, image.width, image.height) }
            .filter { !it.border && it.area >= image.argb.size * 0.05 }
        val best = candidates.maxByOrNull { it.area } ?: return null
        val c = best.corners
        return floatArrayOf(c[0].toFloat(), c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat(), c[5].toFloat(), c[6].toFloat(), c[7].toFloat())
    }

    /** Four points sorted top left, top right, bottom left, bottom right. */
    fun order(points: List<Pair<Float, Float>>): FloatArray {
        val byY = points.sortedBy { it.second }
        val top = byY.take(2).sortedBy { it.first }
        val bottom = byY.drop(2).sortedBy { it.first }
        return floatArrayOf(top[0].first, top[0].second, top[1].first, top[1].second, bottom[0].first, bottom[0].second, bottom[1].first, bottom[1].second)
    }

    /** The straightening: the found points (TL, TR, BL, BR) become a true rectangle of `aspect` (width per height),
     *  as large as they were and where they were – the camera then seems to look straight down. */
    fun straighten(found: FloatArray, aspect: Float): FloatArray? {
        fun dist(a: Int, b: Int) = kotlin.math.hypot((found[2 * a] - found[2 * b]).toDouble(), (found[2 * a + 1] - found[2 * b + 1]).toDouble()).toFloat()
        val h = (dist(0, 2) + dist(1, 3)) / 2
        val w = h * aspect
        val cx = (found[0] + found[2] + found[4] + found[6]) / 4
        val cy = (found[1] + found[3] + found[5] + found[7]) / 4
        val rect = floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy - h / 2, cx - w / 2, cy + h / 2, cx + w / 2, cy + h / 2)
        return Homography.fromPoints(found, rect)
    }

    /** The target as an Android picture (A4 at `widthPx`, e.g. 2480 = 300 dpi) – for printing, Fotos and the ghost. */
    fun bitmap(widthPx: Int): android.graphics.Bitmap {
        val heightPx = (widthPx * HEIGHT / WIDTH).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val k = widthPx / WIDTH
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.rgb(200, 200, 200); paint.strokeWidth = maxOf(1f, 0.35f * k)
        var v = 0f
        while (v <= WIDTH) { canvas.drawLine(v * k, 0f, v * k, heightPx.toFloat(), paint); v += 10f }
        v = 0f
        while (v <= HEIGHT) { canvas.drawLine(0f, v * k, widthPx.toFloat(), v * k, paint); v += 10f }
        paint.color = android.graphics.Color.BLACK; paint.strokeWidth = 1.2f * k
        canvas.drawLine((WIDTH / 2 - 12) * k, HEIGHT / 2 * k, (WIDTH / 2 + 12) * k, HEIGHT / 2 * k, paint)
        canvas.drawLine(WIDTH / 2 * k, (HEIGHT / 2 - 12) * k, WIDTH / 2 * k, (HEIGHT / 2 + 12) * k, paint)
        for (p in 0 until 4) canvas.drawCircle(DOTS[2 * p] * k, DOTS[2 * p + 1] * k, RADIUS * k, paint)
        canvas.drawCircle(MARKER[0] * k, MARKER[1] * k, MARKER_RADIUS * k, paint)
        paint.textSize = 4.2f * k; paint.color = android.graphics.Color.rgb(90, 90, 90); paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("LiCida · Zielbild und Hilfsraster · flach auf die Zeichenfläche legen", WIDTH / 2 * k, (HEIGHT - 10) * k, paint)
        return bitmap
    }
}
