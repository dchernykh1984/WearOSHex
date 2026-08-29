package com.dchernykh.hex.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun cell(
    size: Int,
    column: Int,
    row: Int,
) = row * size + column

/** Play a whole game out between two levels and report who won. */
private fun playOut(
    size: Int,
    red: Level,
    blue: Level,
    seed: Int,
): Game {
    val game = Game(size, swapRule = false)
    val random = Random(seed)
    while (!game.isFinished) {
        val level = if (game.turn == RED) red else blue
        val move = chooseMove(game, level, random)
        assertTrue("the search found no move on a board with empty cells", move >= 0)
        assertTrue("the search chose a cell it cannot play", game.play(move))
    }
    return game
}

class ChooseMoveTest {
    @Test
    fun `always plays a legal cell`() {
        for (level in Level.entries) {
            val game = Game(5, swapRule = false)
            while (!game.isFinished) {
                val move = chooseMove(game, level, Random(1))
                assertTrue(game.isLegalMove(move))
                game.play(move)
            }
        }
    }

    @Test
    fun `finishes a crossing it can finish this move, at every level`() {
        for (level in Level.entries) {
            // Red owns four of the five cells of column 2 and needs one more in the
            // bottom row. Two cells do it - the one below the chain and the one
            // below and to its left, because that is what a hex neighbourhood is -
            // so what matters is that the move played wins, not which of the two it
            // is.
            val game = Game(5, swapRule = false)
            for (row in 0 until 4) {
                game.play(cell(5, 2, row))
                game.play(cell(5, 0, row))
            }

            val move = chooseMove(game, level, Random(0))
            assertTrue("$level played nothing", game.play(move))
            assertEquals("$level missed the win", RED, game.winner)
        }
    }

    @Test
    fun `blocks the only cell that stops the opponent finishing`() {
        // Blue owns row 2 from the left edge as far as column 3 and needs one stone
        // in column 4. Two cells would normally do it, (4, 2) and (4, 1); red
        // already holds (4, 1), so exactly one is left - which is the case the
        // search is required to answer, because with two the game is gone anyway
        // and it may as well play on.
        fun position(): Game {
            val game = Game(5, swapRule = false)
            game.play(cell(5, 4, 1))
            game.play(cell(5, 0, 2))
            for (column in 0 until 3) {
                game.play(cell(5, column, 0))
                game.play(cell(5, column + 1, 2))
            }
            return game
        }

        assertEquals(RED, position().turn)
        for (level in listOf(Level.NORMAL, Level.HARD)) {
            assertEquals("$level let it through", cell(5, 4, 2), chooseMove(position(), level, Random(0)))
        }
    }

    @Test
    fun `has nothing to play on a finished game`() {
        val game = Game(5, swapRule = false)
        for (row in 0 until 5) {
            game.play(cell(5, 2, row))
            if (!game.isFinished) game.play(cell(5, 0, row))
        }

        assertEquals(-1, chooseMove(game, Level.HARD, Random(0)))
    }

    @Test
    fun `plays a whole game to a winner, on every board it offers`() {
        // Hex cannot be drawn, so every game ends with somebody joining its edges.
        for (size in BOARD_SIZES) {
            val game = playOut(size, Level.NORMAL, Level.NORMAL, seed = size)
            assertNotEquals(EMPTY, game.winner)
            assertTrue(game.moveCount <= size * size)
        }
    }

    @Test
    fun `plays better than it does at the easiest level`() {
        // Not a proof of strength, but it is the property that makes the levels
        // mean anything: over a handful of games the searching side should take
        // most of them off the one that drops stones at random.
        var searchWins = 0
        val games = 6
        for (seed in 0 until games) {
            val game = playOut(5, Level.NORMAL, Level.EASY, seed)
            if (game.winner == RED) searchWins += 1
        }

        assertTrue("the searching side won only $searchWins of $games", searchWins >= games - 1)
    }

    @Test
    fun `stays inside its budget on the largest board it plays`() {
        // The point of the budget is wall clock on a watch. This does not measure
        // time - a test machine says nothing about a watch CPU - but it does pin
        // that the search stops looking when it is told to.
        val game = Game(9, swapRule = false)
        game.play(cell(9, 4, 4))
        val budget = Level.HARD.nodeBudget(81)

        chooseMove(game, Level.HARD, Random(0))

        assertTrue("a budget of $budget is not a bound anybody set", budget in 1..90_000)
    }
}

class LevelTest {
    @Test
    fun `runs from no search at all to the deepest one`() {
        assertEquals(0, Level.EASY.depth)
        assertTrue(Level.NORMAL.depth < Level.HARD.depth)
        assertEquals(Level.NORMAL, Level.DEFAULT)
    }

    @Test
    fun `cycles through every level and back`() {
        var level = Level.entries.first()
        repeat(Level.entries.size) { level = level.next }
        assertEquals(Level.entries.first(), level)
    }

    @Test
    fun `reads back a stored level and falls back to the default`() {
        for (level in Level.entries) assertEquals(level, Level.fromStoredName(level.name))
        assertEquals(Level.DEFAULT, Level.fromStoredName(null))
        assertEquals(Level.DEFAULT, Level.fromStoredName("IMPOSSIBLE"))
    }

    @Test
    fun `spends about the same wall clock on a big board as on a small one`() {
        // The budget is divided by the board area, so the leaf count falls as the
        // board grows and each leaf costs more.
        assertTrue(Level.HARD.nodeBudget(25) > Level.HARD.nodeBudget(81))
        assertEquals(0, Level.EASY.nodeBudget(25))
    }

    @Test
    fun `never budgets fewer leaves than it has candidates at the root`() {
        for (level in Level.entries) {
            if (level.depth == 0) continue
            assertTrue(level.nodeBudget(121) >= level.width)
        }
    }
}

class ShouldSwapTest {
    @Test
    fun `takes a strong opening in the middle`() {
        val game = Game(7)
        game.play(cell(7, 3, 3))

        assertTrue(shouldSwap(game))
    }

    @Test
    fun `leaves an opening on the opener's own edge`() {
        // Red opens on the top row, which is already one of the two sides it is
        // trying to join, so the stone has bought almost nothing and taking it
        // over buys the same nothing.
        val game = Game(7)
        game.play(cell(7, 3, 0))

        assertFalse(shouldSwap(game))
    }

    @Test
    fun `leaves an opening out towards a corner`() {
        // What an opening is worth is how much of the middle it commands, and this
        // one is two thirds of the way out to the obtuse corner.
        val game = Game(9)
        game.play(cell(9, 7, 7))

        assertFalse(shouldSwap(game))
    }

    @Test
    fun `has nothing to take when the rule is off or the moment has passed`() {
        val off = Game(7, swapRule = false)
        off.play(cell(7, 3, 3))
        assertFalse(shouldSwap(off))

        val late = Game(7)
        late.play(cell(7, 3, 3))
        late.play(cell(7, 0, 0))
        assertFalse(shouldSwap(late))
    }
}
