package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Card 13: the time-lapse's timing, size and colour conversion. */
class TimelapseTest {
    @Test
    fun timing() {
        assertEquals(2000L, TimelapseSettings(speed = 60).intervalMillis)      // 60×: a picture every 2 s → 30 per film second
        assertEquals(10_000L, TimelapseSettings(speed = 300).intervalMillis)
        assertTrue(TimelapseSettings(speed = 2).choppy && !TimelapseSettings(speed = 5).choppy)
        val s = TimelapseSettings(speed = 120, height = 720, quality = 2, recordUi = true, ignoreZoom = false)
        assertEquals(s, TimelapseSettings.decode(s.encode()))
        assertTrue(TimelapseSettings(quality = 2).bitrate(720, 1600) > TimelapseSettings(quality = 0).bitrate(720, 1600))
    }

    @Test
    fun sizeFitsTheScreen() {
        val (w, h) = Timelapse.size(1080, 2400, 1080)
        assertEquals(1920, h)                      // the long side capped at 1920
        assertEquals(0, w % 16); assertEquals(1080.0 / 2400, w.toDouble() / h, 0.02)
        assertEquals(Pair(720, 1600), Timelapse.size(1080, 2400, 720))      // 720p upright: 720 across
        assertEquals(Pair(1600, 720), Timelapse.size(2400, 1080, 720))      // turned: 720 high
        assertEquals(Pair(720, 1280), Timelapse.size(720, 1280, 1080))      // never more than the screen
    }

    @Test
    fun yuvOfKnownColours() {
        val y = ByteArray(4); val u = ByteArray(1); val v = ByteArray(1)
        Timelapse.toYuv(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2, y, u, v)
        assertEquals(235, y[0].toInt() and 255); assertEquals(128, u[0].toInt() and 255); assertEquals(128, v[0].toInt() and 255)  // white
        Timelapse.toYuv(IntArray(4) { 0xFF000000.toInt() }, 2, 2, y, u, v)
        assertEquals(16, y[0].toInt() and 255)                                                                                   // black
        Timelapse.toYuv(IntArray(4) { 0xFFFF0000.toInt() }, 2, 2, y, u, v)
        assertTrue((v[0].toInt() and 255) > 200 && (u[0].toInt() and 255) < 128)                                               // red: much V
    }
}
