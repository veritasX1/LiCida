package io.github.veritasx1.licida

import kotlin.math.cos
import kotlin.math.sin

/** A plane-to-plane perspective map as 3×3 numbers (row-major, like android.graphics.Matrix.setValues):
 *  (x, y) ↦ ((a·x + b·y + c) / (g·x + h·y + i), (d·x + e·y + f) / (g·x + h·y + i)). Plain math, tested. */
object Homography {
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun multiply(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9) { index ->
        val row = index / 3; val col = index % 3
        (0 until 3).sumOf { k -> (a[row * 3 + k] * b[k * 3 + col]).toDouble() }.toFloat()
    }

    fun apply(h: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = h[6] * x + h[7] * y + h[8]
        return Pair((h[0] * x + h[1] * y + h[2]) / w, (h[3] * x + h[4] * y + h[5]) / w)
    }

    fun translate(dx: Float, dy: Float) = floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f)
    fun scale(sx: Float, sy: Float) = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)

    /** The map taking four points `from` onto four points `to` (each x0,y0,…,x3,y3) – null if they are degenerate. */
    fun fromPoints(from: FloatArray, to: FloatArray): FloatArray? {
        // 8 unknowns (i = 1): two equations per point pair, solved by Gaussian elimination with pivoting.
        val m = Array(8) { DoubleArray(9) }
        for (p in 0 until 4) {
            val x = from[2 * p].toDouble(); val y = from[2 * p + 1].toDouble()
            val u = to[2 * p].toDouble(); val v = to[2 * p + 1].toDouble()
            m[2 * p] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            m[2 * p + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (col in 0 until 8) {
            val pivot = (col until 8).maxByOrNull { kotlin.math.abs(m[it][col]) }!!
            if (kotlin.math.abs(m[pivot][col]) < 1e-9) return null
            val swap = m[col]; m[col] = m[pivot]; m[pivot] = swap
            for (row in 0 until 8) if (row != col) {
                val factor = m[row][col] / m[col][col]
                for (k in col until 9) m[row][k] -= factor * m[col][k]
            }
        }
        return FloatArray(9) { if (it == 8) 1f else (m[it][8] / m[it][it]).toFloat() }
    }

    fun encode(h: FloatArray?) = h?.joinToString(",") ?: ""
    fun decode(text: String?): FloatArray? = text?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 9 }?.toFloatArray()
}

/** How the camera picture is straightened (cards 6/7, handbook p. 17–24) – for tilted stands and the front
 *  camera's mirror: a homography found automatically from the target (`auto`), then the manual tilt about
 *  the centre (−30…+30°), the "height" that exaggerates the tilt's stretch, and stretching. */
data class Correction(val tilt: Float = 0f, val height: Float = 2.5f, val stretchX: Float = 1f, val stretchY: Float = 1f,
                      val auto: FloatArray? = null) {
    val isPlain get() = tilt == 0f && stretchX == 1f && stretchY == 1f && auto == null

    /** The map for a picture of this size, about its centre. */
    fun matrix(width: Float, height: Float): FloatArray {
        val cx = width / 2; val cy = height / 2
        // Tilt: the picture plane turned about the horizontal axis, seen from `height` × picture height away.
        val theta = Math.toRadians(tilt.toDouble())
        val distance = (this.height * height).coerceAtLeast(1f)
        val tiltMap = floatArrayOf(1f, 0f, 0f, 0f, cos(theta).toFloat(), 0f, 0f, (sin(theta) / distance).toFloat(), 1f)
        var m = Homography.multiply(Homography.scale(stretchX, stretchY), tiltMap)
        m = Homography.multiply(Homography.translate(cx, cy), Homography.multiply(m, Homography.translate(-cx, -cy)))
        return if (auto != null) Homography.multiply(m, auto) else m
    }

    fun encode() = "$tilt;$height;$stretchX;$stretchY;${Homography.encode(auto)}"

    override fun equals(other: Any?) = other is Correction && encode() == other.encode()
    override fun hashCode() = encode().hashCode()

    companion object {
        const val MAX_TILT = 30f

        fun decode(text: String?): Correction {
            val parts = text?.split(";") ?: return Correction()
            if (parts.size < 5) return Correction()
            return Correction(parts[0].toFloatOrNull() ?: 0f, parts[1].toFloatOrNull() ?: 2.5f, parts[2].toFloatOrNull() ?: 1f,
                parts[3].toFloatOrNull() ?: 1f, Homography.decode(parts[4]))
        }

        /** The +/− stretch buttons: slow at first, faster the longer they are held (handbook p. 18). */
        fun stretchStep(heldMillis: Long): Float = when {
            heldMillis < 600 -> 0.002f
            heldMillis < 1500 -> 0.006f
            else -> 0.015f
        }
    }
}
