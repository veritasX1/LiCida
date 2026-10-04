package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Card 11: finding a palette, mapping (Ersetzen / Angleichen), taking colours out, layers, keeping it in the history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaletteTest {
    private fun c(r: Int, g: Int, b: Int) = Filters.argb(255, r, g, b)
    private val red = c(220, 30, 30); private val blue = c(30, 60, 210); private val yellow = c(240, 210, 40); private val white = c(250, 250, 250)

    /** Four flat areas with a little noise. */
    private fun picture() = Pixels(80, 80, IntArray(6400) { i ->
        val x = i % 80; val y = i / 80
        val base = when { x < 40 && y < 40 -> red; x >= 40 && y < 40 -> blue; x < 40 -> yellow; else -> white }
        val n = (i * 7919 % 9) - 4
        c(((base shr 16) and 255) + n, ((base shr 8) and 255) + n, (base and 255) + n)
    })

    @Test
    fun findsTheMainColours() {
        val palette = Palette.find(picture(), 4)
        assertEquals(4, palette.size)
        for (wanted in listOf(red, blue, yellow, white)) {
            assertTrue("fehlt: ${Integer.toHexString(wanted)} in ${palette.before.map(Integer::toHexString)}",
                palette.before.any { Colours.distance(Colours.lab(it), Colours.lab(wanted)) < 30 })
        }
        // Asked for more colours than there are: it still works (the same colours, maybe fewer).
        assertTrue(Palette.find(Pixels(4, 1, intArrayOf(red, red, blue, blue)), 16).size <= 16)
    }

    @Test
    fun replaceMatchRemoveAndLayers() {
        val image = picture()
        val found = Palette.find(image, 4)
        val redIndex = found.before.indices.minByOrNull { Colours.distance(Colours.lab(found.before[it]), Colours.lab(red)) }!!
        // Ersetzen: red becomes green, everything else stays.
        val green = c(20, 200, 60)
        val replaced = found.copy(after = found.after.toMutableList().also { it[redIndex] = green }).render(image)
        assertEquals(green, replaced[5, 5]); assertEquals(found.before[found.before.indices.first { it != redIndex && Colours.distance(Colours.lab(found.before[it]), Colours.lab(blue)) < 30 }], replaced[60, 5])
        // Angleichen: every pixel takes the nearest *after* colour – with red changed to pure blue there is no red any more.
        val afterColours = found.after.toMutableList().also { it[redIndex] = c(0, 0, 255) }
        val matched = found.copy(after = afterColours, replace = false).render(image)
        assertTrue(matched.argb.all { it in afterColours })
        assertTrue(matched[5, 5] != found.before[redIndex])
        // Taken out: red pixels go to the nearest remaining colour.
        val without = found.copy(removed = setOf(redIndex)).render(image)
        assertTrue(found.before.indexOf(without[5, 5]) != redIndex)
        // A hidden layer is transparent – the paper shows through while drawing.
        val layered = found.render(image, hidden = setOf(redIndex))
        assertEquals(0, layered[5, 5] ushr 24)
        assertEquals(255, layered[60, 60] ushr 24)
    }

    @Test
    fun keptInHistoryAndSlots() {
        val palette = Palette.find(picture(), 4).let { it.copy(after = it.after.mapIndexed { i, colour -> if (i == 0) c(1, 2, 3) else colour }, removed = setOf(3)) }
        assertEquals(palette, Palette.decode(palette.encode()))
        val step = Step.palette(palette)
        assertEquals(step, Step.decode(step.encode()))
        val edits = Edits().add(Step(Filter.Blur)).add(step)
        assertEquals(edits, Edits.decode(edits.encode()))       // the ";" and "|" of the palette survive
        assertEquals(Slot("Mit Palette", edits.active), Slot.decode(Slot("Mit Palette", edits.active).encode(), 0))
        assertEquals("Farbpalette (3 Farben)", step.label)
        val rendered = Edits.render(picture(), edits.active)
        assertTrue(rendered.argb.toSet().size <= 3)              // only the palette's three remaining colours
        // The colour wheel to print: as many colours as the palette, on white.
        val wheel = Palette.wheel(palette.after, 200, byHue = true)
        assertEquals(Filters.argb(255, 255, 255, 255), wheel[0, 0])
    }
}
