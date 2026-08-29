package com.dchernykh.hex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import com.dchernykh.hex.HexUiState
import com.dchernykh.hex.HexViewModel
import com.dchernykh.hex.Screen
import com.dchernykh.hex.game.topologyFor
import com.dchernykh.hex.layout.HexLayout
import com.dchernykh.hex.layout.cellAt
import com.dchernykh.hex.layout.clampPan
import com.dchernykh.hex.layout.isCellFullyVisible
import com.dchernykh.hex.layout.panLimits
import com.dchernykh.hex.layout.panToCell
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The whole screen: the board, the two caps around it, and the menu when it is up.
 *
 * The board is drawn at a fixed cell size and may be bigger than the screen, so it
 * is dragged rather than shrunk - which is the whole reason a nine-cell board is
 * playable on a wrist at all.
 */
@Composable
fun HexApp(viewModel: HexViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val container = LocalWindowInfo.current.containerSize
    val screenSize = minOf(container.width, container.height)
    if (screenSize <= 0) return

    val layout = remember(screenSize, state.boardCells) { HexLayout(screenSize, state.boardCells) }
    val topology = remember(state.boardCells) { topologyFor(state.boardCells) }
    val menu = remember(screenSize) { MenuMetrics(screenSize) }
    // The band the board is painted in: the screen less a cap at each end for the
    // status line and the buttons.
    val cap = remember(screenSize) { maxOf(MIN_CAP, (screenSize * 0.24f).roundToInt()) }
    val band = screenSize - 2 * cap
    val limits = remember(layout, screenSize, band) { panLimits(layout, screenSize, SCREEN_PADDING, band) }

    // A game of Hex is thought about rather than rushed, and a ten-second display
    // timeout would black out mid-move.
    KeepScreenOnWhile(state.screen == Screen.PLAYING)

    BackHandler(enabled = state.screen != Screen.MENU) { viewModel.showMenu() }

    // The watch may well answer somewhere the board has been dragged away from, so
    // bring its stone into view rather than leaving the player to hunt for what
    // changed. The geometry is the screen's business, which is why the view model
    // only says which stone needs showing.
    LaunchedEffect(state.revealCell, layout, band) {
        val cell = state.revealCell
        if (cell < 0) return@LaunchedEffect
        if (isCellFullyVisible(layout, cell, state.panX, state.panY, screenSize, SCREEN_PADDING, band)) {
            viewModel.revealed(state.panX, state.panY)
        } else {
            val pan = panToCell(layout, cell, screenSize, SCREEN_PADDING, band)
            viewModel.revealed(pan.x, pan.y)
        }
    }

    MaterialTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(ColorBackground)
                    .boardGestures(state, layout, screenSize, limits, viewModel),
        ) {
            if (state.screen == Screen.PLAYING) {
                HexBoard(
                    layout = layout,
                    topology = topology,
                    cells = state.cells,
                    lastMove = state.lastMove,
                    screenSize = screenSize,
                    panX = state.panX,
                    panY = state.panY,
                    modifier = Modifier.fillMaxSize(),
                )
                PlayCaps(screenSize, cap, menu, state, viewModel)
            }

            if (state.screen == Screen.MENU) StartMenu(screenSize, menu, state, viewModel)
        }
    }
}

@Composable
private fun KeepScreenOnWhile(playing: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Dragging pans the board; tapping puts a stone down.
 *
 * The two never collide because a drag has to travel further than the slop before
 * it counts as one - a fingertip never lands and lifts on exactly one pixel, and
 * without that slack a board that can be dragged would swallow half the taps meant
 * to place a stone.
 */
private fun Modifier.boardGestures(
    state: HexUiState,
    layout: HexLayout,
    screenSize: Int,
    limits: com.dchernykh.hex.layout.PanLimits,
    viewModel: HexViewModel,
): Modifier =
    if (state.screen != Screen.PLAYING) {
        this
    } else {
        this
            .pointerInput(layout, limits) {
                var panX = 0
                var panY = 0
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        panX = viewModel.uiState.value.panX
                        panY = viewModel.uiState.value.panY
                        travelled = 0f
                    },
                ) { change, drag ->
                    travelled += abs(drag.x) + abs(drag.y)
                    if (travelled > DRAG_SLOP) {
                        change.consume()
                        panX = clampPan(panX + drag.x.roundToInt(), limits.x)
                        panY = clampPan(panY + drag.y.roundToInt(), limits.y)
                        viewModel.pan(panX, panY)
                    }
                }
            }.pointerInput(layout) {
                detectTapGestures { offset ->
                    val current = viewModel.uiState.value
                    val originX = screenSize / 2 + current.panX
                    val originY = screenSize / 2 + current.panY
                    val cell = cellAt(layout, originX, originY, offset.x.roundToInt(), offset.y.roundToInt())
                    if (cell >= 0) viewModel.play(cell)
                }
            }
    }
