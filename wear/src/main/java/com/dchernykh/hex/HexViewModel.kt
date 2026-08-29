package com.dchernykh.hex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.dchernykh.hex.game.BoardSize
import com.dchernykh.hex.game.EMPTY
import com.dchernykh.hex.game.Game
import com.dchernykh.hex.game.Mode
import com.dchernykh.hex.game.SEAT_FIRST
import com.dchernykh.hex.game.SEAT_SECOND
import com.dchernykh.hex.game.chooseMove
import com.dchernykh.hex.game.shouldSwap
import com.dchernykh.hex.store.Settings
import com.dchernykh.hex.store.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Which of the two screens is in front. */
enum class Screen { MENU, PLAYING }

/**
 * Everything the screen draws.
 *
 * [cells] is a copy of the board rather than the game's own array, so Compose sees
 * a new value after every stone and nothing can mutate what is being painted. A
 * list rather than a ByteArray, because an array compares by identity and would
 * make the data class's own equals useless - and eighty-one boxed bytes once per
 * move is nothing next to hand-writing equality and having to remember every field
 * added to this class afterwards.
 */
data class HexUiState(
    val screen: Screen = Screen.MENU,
    val settings: Settings = Settings(),
    val cells: List<Byte> = emptyList(),
    val boardCells: Int = BoardSize.DEFAULT.cells,
    val turn: Byte = EMPTY,
    val winner: Byte = EMPTY,
    val lastMove: Int = -1,
    val moveCount: Int = 0,
    /** The seat whose turn it is, which against the watch decides You or Watch. */
    val seatToMove: Int = SEAT_FIRST,
    /** The colour each seat plays; the pie rule can swap them. */
    val firstSeatColor: Byte = EMPTY,
    val thinking: Boolean = false,
    /** Whether the pie rule is on offer to the seat about to move. */
    val canSwap: Boolean = false,
    val swapAnnounced: Boolean = false,
    /**
     * The stone the watch has just answered with, until the screen has had a
     * chance to bring it into view. The board may well have been dragged away from
     * where the watch played, and leaving the player to hunt for what changed on a
     * board of eighty-one cells is no way to run a game.
     */
    val revealCell: Int = -1,
    val panX: Int = 0,
    val panY: Int = 0,
)

/**
 * The game as the screen sees it.
 *
 * [searchDispatcher] is where the computer's move is worked out. The hard level is
 * allowed ninety thousand leaf evaluations, which is a visible pause on a watch
 * CPU, and a pause on the main thread is a frozen screen rather than a thinking one.
 */
class HexViewModel(
    private val store: SettingsStore,
    private val random: Random = Random.Default,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HexUiState())
    val uiState: StateFlow<HexUiState> = _uiState.asStateFlow()

    private var game: Game? = null
    private var thinker: Job? = null

    // Every touch of the settings goes through this, each waiting on the one before.
    private var settings: Job = Job().apply { complete() }

    init {
        settings =
            viewModelScope.launch {
                val stored = store.read()
                _uiState.update { it.copy(settings = stored, boardCells = stored.boardSize.cells) }
            }
    }

    fun cycleMode() = updateSettings { it.copy(mode = it.mode.next) }

    fun cycleLevel() = updateSettings { it.copy(level = it.level.next) }

    fun cycleBoardSize() = updateSettings { it.copy(boardSize = it.boardSize.next) }

    fun cycleSwapRule() = updateSettings { it.copy(swapRule = it.swapRule.next) }

    private fun updateSettings(change: (Settings) -> Settings) {
        val next = change(_uiState.value.settings)
        _uiState.update { it.copy(settings = next, boardCells = next.boardSize.cells) }
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                store.write(next)
            }
    }

    /** Deal a fresh board and, when the watch has the opening, let it play. */
    fun startGame() {
        stopThinking()
        val current = _uiState.value.settings
        val fresh = Game(current.boardSize.cells, swapRule = current.swapRule.enabled)
        game = fresh
        _uiState.update {
            it.copy(screen = Screen.PLAYING, swapAnnounced = false, panX = 0, panY = 0, revealCell = -1)
        }
        publish(fresh)
        maybeThink()
    }

    fun showMenu() {
        stopThinking()
        _uiState.update { it.copy(screen = Screen.MENU) }
    }

    /** Drag the board. The caller has already clamped the pan to what the board allows. */
    fun pan(
        x: Int,
        y: Int,
    ) {
        _uiState.update { it.copy(panX = x, panY = y, revealCell = -1) }
    }

    /**
     * Move the board so the watch's answer is on screen, and mark it shown.
     *
     * The screen works out where to move to, because the geometry is its business;
     * this only records that the stone no longer needs revealing, so the board is
     * not dragged back the moment the player moves it themselves.
     */
    fun revealed(
        x: Int,
        y: Int,
    ) {
        _uiState.update { it.copy(panX = x, panY = y, revealCell = -1) }
    }

    /**
     * Put a stone down.
     *
     * Refused while the watch is thinking, and while it is the watch's turn: a tap
     * that landed in the middle of somebody else's move is a tap nobody meant.
     */
    fun play(cell: Int) {
        val current = game ?: return
        val state = _uiState.value
        if (state.screen != Screen.PLAYING || state.thinking) return
        if (isComputersTurn(current)) return
        if (!current.play(cell)) return
        _uiState.update { it.copy(swapAnnounced = false) }
        publish(current)
        maybeThink()
    }

    /** Take the opening stone instead of answering it, when the pie rule offers it. */
    fun swapSides() {
        val current = game ?: return
        if (_uiState.value.thinking || !current.canSwap()) return
        if (!current.swapSides()) return
        publish(current)
        maybeThink()
    }

    override fun onCleared() {
        stopThinking()
        super.onCleared()
    }

    private fun isComputersTurn(current: Game): Boolean =
        _uiState.value.settings.mode == Mode.COMPUTER &&
            !current.isFinished &&
            current.seatToMove() == SEAT_SECOND

    /**
     * Let the watch answer, if it is its turn.
     *
     * The pie rule is offered to the second seat, which against the watch is the
     * watch: it decides for itself whether to take the opening stone, and the screen
     * says so, because nothing on the board moves when it happens.
     */
    private fun maybeThink() {
        val current = game ?: return
        if (!isComputersTurn(current)) return
        val level = _uiState.value.settings.level

        _uiState.update { it.copy(thinking = true) }
        thinker =
            viewModelScope.launch {
                if (current.canSwap() && shouldSwap(current)) {
                    current.swapSides()
                    _uiState.update { it.copy(thinking = false, swapAnnounced = true) }
                    publish(current)
                    return@launch
                }
                // Off the main thread: the hard level is a visible pause, and a
                // pause on the main thread is a frozen screen rather than a
                // thinking one.
                val move = withContext(searchDispatcher) { chooseMove(current, level, random) }
                val played = move >= 0 && current.play(move)
                _uiState.update { it.copy(thinking = false, revealCell = if (played) move else -1) }
                publish(current)
            }
    }

    private fun stopThinking() {
        thinker?.cancel()
        thinker = null
        _uiState.update { it.copy(thinking = false) }
    }

    /** Copy the board into the state, so what is drawn cannot change under the drawing. */
    private fun publish(current: Game) {
        _uiState.update {
            it.copy(
                cells = current.cells.toList(),
                boardCells = current.size,
                turn = current.turn,
                winner = current.winner,
                lastMove = current.lastMove,
                moveCount = current.moveCount,
                seatToMove = current.seatToMove(),
                firstSeatColor = current.colorForSeat(SEAT_FIRST),
                canSwap = current.canSwap(),
            )
        }
    }

    companion object {
        fun factory(store: SettingsStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return HexViewModel(store) as T
                }
            }
    }
}
