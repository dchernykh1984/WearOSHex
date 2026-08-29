package com.dchernykh.hex.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun cell(
    size: Int,
    column: Int,
    row: Int,
) = row * size + column

class TopologyTest {
    @Test
    fun `numbers the cells row by row`() {
        val topology = topologyFor(5)

        assertEquals(25, topology.cellCount)
        assertEquals(5, topology.size)
    }

    @Test
    fun `gives a middle cell all six neighbours and a corner fewer`() {
        val topology = topologyFor(5)

        assertEquals(6, topology.degree[cell(5, 2, 2)].toInt())
        // The two obtuse corners keep three neighbours; the acute ones keep two.
        assertTrue(topology.degree[cell(5, 0, 0)].toInt() < 6)
        assertTrue(topology.degree[cell(5, 4, 4)].toInt() < 6)
    }

    @Test
    fun `makes adjacency mutual`() {
        val topology = topologyFor(5)
        for (from in 0 until topology.cellCount) {
            for (i in 0 until topology.degree[from]) {
                val to = topology.neighbors[from * MAX_NEIGHBORS + i]
                val back =
                    (0 until topology.degree[to]).any {
                        topology.neighbors[to * MAX_NEIGHBORS + it] == from
                    }
                assertTrue("$from touches $to but not the other way round", back)
            }
        }
    }

    @Test
    fun `gives red the first and last rows and blue the first and last columns`() {
        val topology = topologyFor(5)

        assertTrue(topology.edges[cell(5, 2, 0)].toInt() and EDGE_RED_START != 0)
        assertTrue(topology.edges[cell(5, 2, 4)].toInt() and EDGE_RED_END != 0)
        assertTrue(topology.edges[cell(5, 0, 2)].toInt() and EDGE_BLUE_START != 0)
        assertTrue(topology.edges[cell(5, 4, 2)].toInt() and EDGE_BLUE_END != 0)
    }

    @Test
    fun `gives a corner one edge of each player, which is how Hex counts them`() {
        val topology = topologyFor(5)
        val topLeft = topology.edges[cell(5, 0, 0)].toInt()

        assertTrue(topLeft and EDGE_RED_START != 0)
        assertTrue(topLeft and EDGE_BLUE_START != 0)
    }

    @Test
    fun `puts the middle of the board at the lowest bias`() {
        val topology = topologyFor(5)
        val middle = topology.centerBias[cell(5, 2, 2)]

        assertEquals(0, middle)
        assertTrue(topology.centerBias[cell(5, 0, 0)] > middle)
    }

    @Test
    fun `builds a board size once and keeps it`() {
        assertTrue(topologyFor(7) === topologyFor(7))
    }

    @Test
    fun `clamps a size the rest of the code is not written for`() {
        assertEquals(MIN_SIZE, clampSize(0))
        assertEquals(MAX_SIZE, clampSize(99))
        assertEquals(7, clampSize(7))
        assertEquals(MIN_SIZE, topologyFor(1).size)
    }
}

class PlayTest {
    @Test
    fun `opens with red and alternates`() {
        val game = Game(5)

        assertEquals(RED, game.turn)
        assertTrue(game.play(0))
        assertEquals(BLUE, game.turn)
        assertTrue(game.play(1))
        assertEquals(RED, game.turn)
    }

    @Test
    fun `refuses a cell that is taken or is not on the board`() {
        val game = Game(5)
        game.play(0)

        assertFalse(game.play(0))
        assertFalse(game.play(-1))
        assertFalse(game.play(25))
        assertEquals(1, game.moveCount)
    }

    @Test
    fun `wins for red on a chain from the top row to the bottom`() {
        val game = Game(5)
        // Red walks straight down column 2; blue answers out of the way.
        for (row in 0 until 5) {
            assertTrue(game.play(cell(5, 2, row)))
            if (game.isFinished) break
            assertTrue(game.play(cell(5, 0, row)))
        }

        assertEquals(RED, game.winner)
        assertTrue(game.isFinished)
    }

    @Test
    fun `wins for blue on a chain from the left column to the right`() {
        val game = Game(5)
        // Red plays along the top row, which is its own edge and joins nothing.
        game.play(cell(5, 0, 0))
        for (column in 0 until 5) {
            assertTrue(game.play(cell(5, column, 2)))
            if (game.isFinished) break
            assertTrue(game.play(cell(5, column, 4)))
        }

        assertEquals(BLUE, game.winner)
    }

    @Test
    fun `refuses to play on once it is over`() {
        val game = Game(5)
        for (row in 0 until 5) {
            game.play(cell(5, 2, row))
            if (!game.isFinished) game.play(cell(5, 0, row))
        }

        assertTrue(game.isFinished)
        assertFalse(game.play(cell(5, 4, 4)))
    }

    @Test
    fun `remembers the stone it played last`() {
        val game = Game(5)
        game.play(7)

        assertEquals(7, game.lastMove)
    }
}

class SwapTest {
    @Test
    fun `offers the pie rule once, in reply to the opening stone`() {
        val game = Game(5)

        assertFalse("nothing has been played yet", game.canSwap())
        game.play(12)
        assertTrue(game.canSwap())
        game.play(0)
        assertFalse("the moment has passed", game.canSwap())
    }

    @Test
    fun `never offers it when it is switched off`() {
        val game = Game(5, swapRule = false)
        game.play(12)

        assertFalse(game.canSwap())
        assertFalse(game.swapSides())
    }

    @Test
    fun `moves nothing on the board, only who owns which colour`() {
        val game = Game(5)
        game.play(12)
        val before = game.cells.copyOf()

        assertTrue(game.swapSides())

        assertArrayEquals(before, game.cells)
        // The stone keeps its colour and the colour order is untouched; what
        // changes is that the seat which opened is now the one to move.
        assertEquals(BLUE, game.turn)
        assertEquals(SEAT_FIRST, game.seatToMove())
        assertEquals(BLUE, game.colorForSeat(SEAT_FIRST))
        assertEquals(RED, game.colorForSeat(SEAT_SECOND))
    }

    @Test
    fun `is offered only once`() {
        val game = Game(5)
        game.play(12)
        assertTrue(game.swapSides())

        assertFalse(game.canSwap())
        assertFalse(game.swapSides())
    }

    private fun assertArrayEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        assertEquals(expected.toList(), actual.toList())
    }
}
