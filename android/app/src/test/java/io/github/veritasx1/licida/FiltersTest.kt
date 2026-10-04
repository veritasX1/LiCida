package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cards 9/10: the toolbox filters and the edit history. */
class FiltersTest {
    private fun c(r: Int, g: Int, b: Int) = Filters.argb(255, r, g, b)
    private fun rgb(p: Int) = Triple((p shr 16) and 255, (p shr 8) and 255, p and 255)

    /** A 40×30 test card: left half black, right half white, a red square. */
    private fun card() = Pixels(40, 30, IntArray(1200) { i ->
        val x = i % 40; val y = i / 40
        when { x in 25..30 && y in 10..15 -> c(255, 0, 0); x < 20 -> c(0, 0, 0); else -> c(255, 255, 255) }
    })

    @Test
    fun grayIsLinearLuminance() {
        val gray = Filters.apply(Pixels(1, 1, intArrayOf(c(255, 0, 0))), Filter.Gray)
        assertEquals(Triple(54, 54, 54), rgb(gray[0, 0]))  // 0.2126 · 255
    }

    @Test
    fun posterizeHasOnlyThatManyShades() {
        val ramp = Pixels(256, 1, IntArray(256) { c(it, it, it) })
        for (levels in listOf(2, 4, 16)) {
            val shades = Filters.apply(ramp, Filter.Posterize, levels).argb.map { rgb(it).first }.toSet()
            assertEquals(levels, shades.size)
        }
        // 64 colours: every channel only 0, 85, 170 or 255 (4 × 4 × 4).
        val many = Pixels(4096, 1, IntArray(4096) { c((it * 37) % 256, (it * 101) % 256, (it * 13) % 256) })
        val reduced = Filters.apply(many, Filter.Colors64).argb.map { rgb(it) }
        assertTrue(reduced.all { (r, g, b) -> listOf(r, g, b).all { v -> v in setOf(0, 85, 170, 255) } })
        assertTrue(reduced.toSet().size in 50..64)
    }

    @Test
    fun thresholdTwiceInverts() {
        val once = Edits.render(card(), listOf(Step(Filter.Threshold)))
        val twice = Edits.render(card(), listOf(Step(Filter.Threshold), Step(Filter.Threshold)))
        assertEquals(Triple(0, 0, 0), rgb(once[2, 2]))
        assertEquals(Triple(255, 255, 255), rgb(twice[2, 2]))
        assertEquals(Triple(0, 0, 0), rgb(twice[35, 2]))
    }

    @Test
    fun edgesAreBlackLinesOnTransparent() {
        val edges = Filters.apply(card(), Filter.Edges)
        assertEquals(0, edges[5, 5] ushr 24)            // calm area: see-through
        assertEquals(255, edges[20, 5] ushr 24)         // the black/white border: a line
        assertEquals(Triple(0, 0, 0), rgb(edges[20, 5]))
    }

    @Test
    fun mirrorGridsTicksAndContrast() {
        val mirrored = Filters.apply(card(), Filter.Mirror)
        assertEquals(rgb(card()[0, 0]), rgb(mirrored[39, 0]))
        val gridded = Filters.grid(Pixels(30, 30, IntArray(900) { c(255, 255, 255) }), 3)
        assertEquals(Triple(0, 0, 0), rgb(gridded[10, 5]))     // a line every 10 px
        assertEquals(Triple(255, 255, 255), rgb(gridded[5, 5]))
        val ticked = Filters.apply(Pixels(200, 200, IntArray(40000) { c(255, 255, 255) }), Filter.Ticks)
        assertEquals(Triple(0, 0, 0), rgb(ticked[10, 10]))
        // Equalize spreads a dull ramp over the whole range.
        val dull = Pixels(100, 1, IntArray(100) { c(100 + it / 4, 100 + it / 4, 100 + it / 4) })
        val spread = Filters.apply(dull, Filter.Equalize).argb.map { rgb(it).first }
        assertEquals(0, spread.min()); assertEquals(255, spread.max())
        val stretched = Filters.apply(dull, Filter.Normalize).argb.map { rgb(it).first }
        assertTrue(stretched.max() - stretched.min() > 240)
    }

    @Test
    fun blurSmoothsAndStacks() {
        val once = Filters.apply(card(), Filter.Blur)
        val twice = Filters.apply(once, Filter.Blur)
        val edge = rgb(once[20, 5]).first
        assertTrue(edge in 1..254)                              // the border got soft
        assertTrue(rgb(twice[18, 5]).first > rgb(once[18, 5]).first)  // twice = softer
        // Every filter keeps the size and runs on a tiny and a plain picture.
        for (filter in Filter.entries) {
            val out = Filters.apply(card(), filter)
            assertEquals(40 to 30, out.width to out.height)
            Filters.apply(Pixels(1, 1, intArrayOf(c(9, 9, 9))), filter)
        }
    }

    @Test
    fun historyUndoRedoReplaceAndSlots() {
        var edits = Edits().add(Step(Filter.Gray)).add(Step(Filter.Blur)).add(Step(Filter.Posterize, 6))
        assertEquals(3, edits.position)
        edits = edits.undo().undo()
        assertEquals(listOf("Graustufen"), edits.active.map { it.label })
        assertTrue(edits.canRedo)
        edits = edits.add(Step(Filter.Sepia))                   // replaces Blur and Tontrennung
        assertEquals(listOf("Graustufen", "Sepia"), edits.steps.map { it.label })
        assertEquals(false, edits.canRedo)
        assertEquals(0, edits.reset().position)
        assertEquals(edits, Edits.decode(edits.encode()))
        val replayed = Edits().replay(listOf(Step(Filter.Blur), Step(Filter.Posterize, 6)))
        assertEquals(listOf("Weichzeichnen", "Tontrennung (6)"), replayed.active.map { it.label })
        val slot = Slot("Aquarell", replayed.active)
        assertEquals(slot, Slot.decode(slot.encode(), 0))
        assertEquals("Platz 2", Slot.decode(null, 1).name)
    }
}
