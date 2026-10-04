package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Card 4: the four colour sliders as one colour matrix. */
class EffectsTest {
    private fun c(r: Int, g: Int, b: Int) = Filters.argb(255, r, g, b)
    private fun rgb(p: Int) = Triple((p shr 16) and 255, (p shr 8) and 255, p and 255)
    private fun one(effects: Effects, colour: Int) = rgb(effects.apply(Pixels(1, 1, intArrayOf(colour)))[0, 0])

    @Test
    fun neutralChangesNothing() {
        for (colour in listOf(c(0, 0, 0), c(255, 255, 255), c(200, 40, 90), c(12, 140, 230))) {
            val (r, g, b) = one(Effects(), colour); val (er, eg, eb) = rgb(colour)
            assertTrue(abs(r - er) <= 1 && abs(g - eg) <= 1 && abs(b - eb) <= 1)
        }
        assertTrue(Effects().isNeutral)
    }

    @Test
    fun eachSliderDoesItsJob() {
        assertTrue(one(Effects(brightness = 0.5f), c(100, 100, 100)).first > 140)            // brighter
        val flat = one(Effects(contrast = 0.5f), c(30, 30, 30)).first
        val steep = one(Effects(contrast = 2f), c(30, 30, 30)).first
        assertTrue(flat > 30 && steep < 30)                                                  // contrast about middle grey
        val (r, g, b) = one(Effects(saturation = 0f), c(200, 40, 90))
        assertTrue(abs(r - g) <= 1 && abs(g - b) <= 1)                                       // no saturation: grey
        val turned = one(Effects(hue = 120f), c(220, 30, 30))
        assertTrue("Rot um 120° sollte grünlich werden: $turned", turned.second > turned.first && turned.second > turned.third)
        assertEquals(one(Effects(hue = 360f), c(200, 40, 90)).first.toDouble(), 200.0, 2.0)  // a full turn: the same
    }

    @Test
    fun keptAsAStep() {
        val effects = Effects(0.2f, 1.3f, 0.8f, -30f)
        assertEquals(effects, Effects.decode(effects.encode()))
        val edits = Edits().add(Step.effects(effects))
        assertEquals(edits, Edits.decode(edits.encode()))
        assertEquals(effects, edits.active.single().effects)
    }
}
