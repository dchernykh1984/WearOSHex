package com.dchernykh.hex.layout

import com.dchernykh.hex.game.BOARD_SIZES
import com.dchernykh.hex.ui.MIN_CAP
import com.dchernykh.hex.ui.SCREEN_PADDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The round sizes Wear OS watches actually come in, small to large. */
private val SCREENS = listOf(384, 416, 454, 466, 480)

private fun bandFor(screen: Int) = screen - 2 * maxOf(MIN_CAP, (screen * 0.24f).roundToInt())

class HexLayoutTest {
    @Test
    fun `gives every board the same cell size, whatever its size`() {
        // The whole point: a nine-cell board shrunk to fit would have cells under
        // two millimetres across, so the cell stays tappable and the board is
        // allowed to be bigger than the screen instead.
        val sizes = BOARD_SIZES.map { HexLayout(466, it).scale }.toSet()

        assertEquals(1, sizes.size)
    }

    @Test
    fun `lays the cells out as a rhombus, each row shifted half a cell right`() {
        val layout = HexLayout(466, 5)
        val row0col0 = layout.offsetsX[0]
        val row1col0 = layout.offsetsX[5]

        // One row down is half a cell to the right, which is what shears the square
        // grid into the rhombus Hex is played on. Within a pixel, because both are
        // rounded to whole ones.
        assertTrue(abs(layout.cellWidth / 2 - (row1col0 - row0col0)) <= 1)
        assertTrue(layout.offsetsY[5] > layout.offsetsY[0])
    }

    @Test
    fun `centres the board on its own middle`() {
        for (size in BOARD_SIZES) {
            val layout = HexLayout(466, size)
            val first = layout.cellCount - 1

            // The two acute corners are equally far from the middle, in opposite
            // directions.
            assertTrue(abs(-layout.offsetsX[0] - layout.offsetsX[first]) <= 1)
            assertTrue(abs(-layout.offsetsY[0] - layout.offsetsY[first]) <= 1)
        }
    }

    @Test
    fun `is wider than it is tall, as a sixty degree rhombus is`() {
        val layout = HexLayout(466, 9)

        assertTrue(layout.halfWidth > layout.halfHeight)
    }

    @Test
    fun `draws a pointy-topped hexagon of six corners`() {
        val layout = HexLayout(466, 5)
        val corners = hexCorners(layout, 12, 233, 233)

        assertEquals(6, corners.size)
        // The first corner is straight up from the centre.
        assertEquals(233, corners[0].x)
        assertTrue(corners[0].y < 233)
        // And the fourth is straight down, the same distance.
        assertEquals(233, corners[3].x)
        assertEquals(233 - corners[0].y, corners[3].y - 233)
    }

    @Test
    fun `finds the cell a touch landed on`() {
        val layout = HexLayout(466, 7)
        val origin = 233

        for (cell in 0 until layout.cellCount) {
            val x = origin + layout.offsetsX[cell]
            val y = origin + layout.offsetsY[cell]
            assertEquals("a touch on the middle of $cell", cell, cellAt(layout, origin, origin, x, y))
        }
    }

    @Test
    fun `reports nothing for a touch well off the board`() {
        val layout = HexLayout(466, 5)

        assertEquals(-1, cellAt(layout, 233, 233, 5000, 5000))
    }

    @Test
    fun `snaps a touch to the nearest cell, not to the one it overlaps`() {
        // Hexagons tile the plane, so the cells are the Voronoi regions of their
        // own centres and the nearest centre is the right answer everywhere inside
        // the board.
        val layout = HexLayout(466, 7)
        val origin = 233
        val cell = 24
        val x = origin + layout.offsetsX[cell] + layout.scale / 4
        val y = origin + layout.offsetsY[cell] - layout.scale / 4

        assertEquals(cell, cellAt(layout, origin, origin, x, y))
    }

    @Test
    fun `clamps a board size the rest of the code is not written for`() {
        assertEquals(3, HexLayout(466, 1).size)
        assertEquals(11, HexLayout(466, 99).size)
    }
}

class PanningTest {
    @Test
    fun `barely moves a board that already fits`() {
        // Five cells across clears the bezel where it sits on the size the game was
        // drawn for, so it does not move at all there. On the smallest round
        // watches it is a couple of pixels too wide and is allowed exactly those,
        // which is the limit doing its job rather than a board that wanders.
        assertEquals(0, panLimits(HexLayout(466, 5), 466, SCREEN_PADDING, bandFor(466)).x)

        for (screen in SCREENS) {
            val limits = panLimits(HexLayout(screen, 5), screen, SCREEN_PADDING, bandFor(screen))
            assertTrue("a five-cell board wandered on $screen", limits.x <= 4)
        }
    }

    @Test
    fun `lets a board bigger than the screen be dragged`() {
        for (screen in SCREENS) {
            val layout = HexLayout(screen, 9)
            val limits = panLimits(layout, screen, SCREEN_PADDING, bandFor(screen))

            assertTrue("a nine-cell board should move on $screen", limits.x > 0)
            assertTrue(limits.y > 0)
        }
    }

    @Test
    fun `lets every cell be brought out from behind the rim`() {
        // The property the pan limits exist for. The obvious limit - until the
        // board's edge meets the viewport's - leaves the acute corners under the
        // bezel on a nine-cell board, which is a game with cells nobody can play.
        for (screen in SCREENS) {
            for (size in BOARD_SIZES) {
                val layout = HexLayout(screen, size)
                val band = bandFor(screen)
                for (cell in 0 until layout.cellCount) {
                    val pan = panToCell(layout, cell, screen, SCREEN_PADDING, band)
                    assertTrue(
                        "cell $cell of $size stays hidden on a $screen screen",
                        isCellFullyVisible(layout, cell, pan.x, pan.y, screen, SCREEN_PADDING, band),
                    )
                }
            }
        }
    }

    @Test
    fun `never pans past its own limits`() {
        val screen = 466
        val layout = HexLayout(screen, 9)
        val band = bandFor(screen)
        val limits = panLimits(layout, screen, SCREEN_PADDING, band)

        for (cell in 0 until layout.cellCount) {
            val pan = panToCell(layout, cell, screen, SCREEN_PADDING, band)
            assertTrue(abs(pan.x) <= limits.x)
            assertTrue(abs(pan.y) <= limits.y)
        }
    }

    @Test
    fun `holds a pan inside its limits`() {
        assertEquals(40, clampPan(90, 40))
        assertEquals(-40, clampPan(-90, 40))
        assertEquals(12, clampPan(12, 40))
        assertEquals(0, clampPan(90, 0))
    }

    @Test
    fun `measures how far a cell sits from the middle`() {
        val layout = HexLayout(466, 7)
        val middle = layout.cellCount / 2

        assertEquals(0f, cellReach(layout, middle, 0, 0), 1f)
        assertTrue(cellReach(layout, 0, 0, 0) > 0f)
    }

    @Test
    fun `paints what is on screen and skips what is not`() {
        val screen = 466
        val layout = HexLayout(screen, 9)

        assertTrue(isCellDrawable(layout, layout.cellCount / 2, 0, 0, screen))
        // Dragged far enough away, a cell stops being worth painting.
        assertTrue(!isCellDrawable(layout, 0, 4000, 4000, screen))
    }

    @Test
    fun `keeps a fully visible cell inside the circle and the band`() {
        val screen = 466
        val layout = HexLayout(screen, 9)
        val band = bandFor(screen)
        val centre = screen / 2f

        for (cell in 0 until layout.cellCount) {
            if (!isCellFullyVisible(layout, cell, 0, 0, screen, SCREEN_PADDING, band)) continue
            val x = centre + layout.offsetsX[cell]
            val y = centre + layout.offsetsY[cell]
            assertTrue(hypot(x - centre, y - centre) + layout.scale * HEX_FILL <= centre - SCREEN_PADDING + 1)
        }
    }

    @Test
    fun `moves the board when a cell needs it and leaves it alone when it does not`() {
        val screen = 466
        val layout = HexLayout(screen, 9)
        val band = bandFor(screen)
        val middle = layout.cellCount / 2

        assertEquals(Point(0, 0), panToCell(layout, middle, screen, SCREEN_PADDING, band))
        assertNotEquals(Point(0, 0), panToCell(layout, 0, screen, SCREEN_PADDING, band))
    }
}
