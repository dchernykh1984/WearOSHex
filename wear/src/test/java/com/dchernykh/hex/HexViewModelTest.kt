package com.dchernykh.hex

import com.dchernykh.hex.game.BLUE
import com.dchernykh.hex.game.BoardSize
import com.dchernykh.hex.game.EMPTY
import com.dchernykh.hex.game.Level
import com.dchernykh.hex.game.Mode
import com.dchernykh.hex.game.RED
import com.dchernykh.hex.game.SEAT_FIRST
import com.dchernykh.hex.game.SwapRule
import com.dchernykh.hex.store.Settings
import com.dchernykh.hex.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** An in-memory stand-in for the watch's storage. */
private class FakeSettingsStore(
    var settings: Settings = Settings(),
) : SettingsStore {
    var writes = 0
        private set

    override suspend fun read(): Settings = settings

    override suspend fun write(settings: Settings) {
        this.settings = settings
        writes++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HexViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The search runs on the same test dispatcher as everything else, so
     * advanceUntilIdle waits for the watch's move instead of racing it.
     */
    private fun viewModel(
        store: SettingsStore = FakeSettingsStore(),
        seed: Int = 0,
    ) = HexViewModel(store, Random(seed), dispatcher)

    @Test
    fun `opens on the menu, set up as it was left`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(Mode.TWO_PLAYERS, Level.HARD, BoardSize.LARGE, SwapRule.OFF),
                )
            val model = viewModel(store)

            advanceUntilIdle()

            assertEquals(Screen.MENU, model.uiState.value.screen)
            assertEquals(store.settings, model.uiState.value.settings)
            assertEquals(9, model.uiState.value.boardCells)
        }

    @Test
    fun `walks each setting and remembers it`() =
        runTest(dispatcher) {
            val store = FakeSettingsStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.cycleMode()
            model.cycleLevel()
            model.cycleBoardSize()
            model.cycleSwapRule()
            advanceUntilIdle()

            val shown = model.uiState.value.settings
            assertEquals(Mode.DEFAULT.next, shown.mode)
            assertEquals(Level.DEFAULT.next, shown.level)
            assertEquals(BoardSize.DEFAULT.next, shown.boardSize)
            assertEquals(SwapRule.DEFAULT.next, shown.swapRule)
            assertEquals(shown, store.settings)
        }

    @Test
    fun `deals a board of the size that was chosen`() =
        runTest(dispatcher) {
            val store = FakeSettingsStore(Settings(mode = Mode.TWO_PLAYERS, boardSize = BoardSize.SMALL))
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(Screen.PLAYING, model.uiState.value.screen)
            assertEquals(25, model.uiState.value.cells.size)
            assertEquals(RED, model.uiState.value.turn)
            assertEquals(0, model.uiState.value.moveCount)
        }

    @Test
    fun `puts a stone down and passes the turn`() =
        runTest(dispatcher) {
            val store = FakeSettingsStore(Settings(mode = Mode.TWO_PLAYERS, swapRule = SwapRule.OFF))
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.play(12)
            advanceUntilIdle()

            assertEquals(RED, model.uiState.value.cells[12])
            assertEquals(BLUE, model.uiState.value.turn)
            assertEquals(12, model.uiState.value.lastMove)
        }

    @Test
    fun `ignores a tap on a cell that is taken`() =
        runTest(dispatcher) {
            val store = FakeSettingsStore(Settings(mode = Mode.TWO_PLAYERS, swapRule = SwapRule.OFF))
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            model.play(12)
            advanceUntilIdle()

            model.play(12)
            advanceUntilIdle()

            assertEquals(1, model.uiState.value.moveCount)
        }

    @Test
    fun `lets the watch answer, and hands the turn back`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(mode = Mode.COMPUTER, level = Level.NORMAL, swapRule = SwapRule.OFF),
                )
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.play(12)
            advanceUntilIdle()

            // Two stones on the board and it is the player's turn again.
            assertEquals(2, model.uiState.value.moveCount)
            assertFalse(model.uiState.value.thinking)
            assertEquals(SEAT_FIRST, model.uiState.value.seatToMove)
        }

    @Test
    fun `takes the opening stone itself when the pie rule is on`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(mode = Mode.COMPUTER, level = Level.NORMAL, swapRule = SwapRule.ON),
                )
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            // A strong opening in the middle of a 7x7 board is one the watch takes.
            model.play(24)
            advanceUntilIdle()

            val state = model.uiState.value
            assertTrue("the swap was not announced", state.swapAnnounced)
            // Nothing on the board moved: the stone is still there and still red.
            assertEquals(RED, state.cells[24])
            assertEquals(1, state.moveCount)
            // What changed is who owns which colour, so it is the player's turn.
            assertEquals(BLUE, state.firstSeatColor)
            assertEquals(SEAT_FIRST, state.seatToMove)
        }

    @Test
    fun `never takes an opening on the opener's own edge`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(mode = Mode.COMPUTER, level = Level.NORMAL, swapRule = SwapRule.ON),
                )
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            // The top row is red's own edge, so the stone bought almost nothing.
            model.play(3)
            advanceUntilIdle()

            assertFalse(model.uiState.value.swapAnnounced)
            assertEquals(2, model.uiState.value.moveCount)
        }

    @Test
    fun `plays a whole game against the watch to a winner`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(
                        mode = Mode.COMPUTER,
                        level = Level.EASY,
                        boardSize = BoardSize.SMALL,
                        swapRule = SwapRule.OFF,
                    ),
                )
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            var guard = 0
            while (model.uiState.value.winner == EMPTY && guard < 100) {
                val free =
                    model.uiState.value.cells
                        .indexOfFirst { it == EMPTY }
                if (free < 0) break
                model.play(free)
                advanceUntilIdle()
                guard++
            }

            // Hex cannot be drawn, so somebody joined their edges.
            assertNotEquals(EMPTY, model.uiState.value.winner)
        }

    @Test
    fun `refuses a stone while the watch is still thinking`() =
        runTest(dispatcher) {
            val store =
                FakeSettingsStore(
                    Settings(mode = Mode.COMPUTER, level = Level.HARD, swapRule = SwapRule.OFF),
                )
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.play(24)
            // Not advanced: the search has been launched and has not finished, so
            // the watch still owns the turn.
            assertTrue(model.uiState.value.thinking)
            model.play(0)

            advanceUntilIdle()
            assertEquals(EMPTY, model.uiState.value.cells[0])
        }

    @Test
    fun `drags the board and goes back to the menu`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.pan(30, -20)
            assertEquals(30, model.uiState.value.panX)
            assertEquals(-20, model.uiState.value.panY)

            model.showMenu()
            assertEquals(Screen.MENU, model.uiState.value.screen)
            assertFalse(model.uiState.value.thinking)
        }

    @Test
    fun `starts a fresh board each time, centred again`() =
        runTest(dispatcher) {
            val store = FakeSettingsStore(Settings(mode = Mode.TWO_PLAYERS, swapRule = SwapRule.OFF))
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            model.play(12)
            advanceUntilIdle()
            model.pan(30, 30)

            model.startGame()
            advanceUntilIdle()

            assertEquals(0, model.uiState.value.moveCount)
            assertEquals(EMPTY, model.uiState.value.cells[12])
            assertEquals(0, model.uiState.value.panX)
            assertEquals(0, model.uiState.value.panY)
        }
}
