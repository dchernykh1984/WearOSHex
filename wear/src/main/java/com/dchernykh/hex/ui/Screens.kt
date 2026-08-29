package com.dchernykh.hex.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.dchernykh.hex.HexUiState
import com.dchernykh.hex.HexViewModel
import com.dchernykh.hex.R
import com.dchernykh.hex.game.BLUE
import com.dchernykh.hex.game.EMPTY
import com.dchernykh.hex.game.Mode
import com.dchernykh.hex.game.RED
import com.dchernykh.hex.layout.centeredBox
import com.dchernykh.hex.layout.Box as LayoutBox

// The start menu and the two caps the play screen writes in. They live one file
// away from the shell that hosts them because they are what changes when the game
// gains a screen, and the shell is what does not.

@Composable
fun StartMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    state: HexUiState,
    viewModel: HexViewModel,
) {
    val settings = state.settings
    val items = mutableListOf<MenuItem>()
    items += MenuItem.Line(metrics.big, ColorText, stringResource(R.string.app_name))
    items += MenuItem.Gap(metrics.gap)
    items += MenuItem.Action(metrics.button, stringResource(settings.mode.labelRes), viewModel::cycleMode)
    // The level only means anything when there is a watch to play against.
    if (settings.mode == Mode.COMPUTER) {
        items += MenuItem.Action(metrics.button, stringResource(settings.level.labelRes), viewModel::cycleLevel)
    }
    items += MenuItem.Action(metrics.button, settings.boardSize.label, viewModel::cycleBoardSize)
    items += MenuItem.Action(metrics.button, stringResource(settings.swapRule.labelRes), viewModel::cycleSwapRule)
    items += MenuItem.Gap(metrics.gap)
    items += MenuItem.Action(metrics.button, stringResource(R.string.play), viewModel::startGame)
    items += MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.hint))

    MenuOverlay(screenSize, metrics, items)
}

/**
 * The two caps: who is to move (or who won) above the board, and the way out below
 * it - with the swap offer in its place while the pie rule is on the table.
 */
@Composable
fun PlayCaps(
    screenSize: Int,
    cap: Int,
    metrics: MenuMetrics,
    state: HexUiState,
    viewModel: HexViewModel,
) {
    val topHeight = metrics.small
    val top = centeredBox(screenSize, (cap - topHeight) / 2, topHeight, metrics.maxWidth, SCREEN_PADDING)
    MenuLine(top, statusColor(state), statusText(state), fraction = 0.86f)

    val bottomHeight = metrics.button
    val bottomTop = screenSize - cap + (cap - bottomHeight) / 2
    val row = centeredBox(screenSize, bottomTop, bottomHeight, metrics.maxWidth, SCREEN_PADDING)

    // The way back to the menu is always there, and whatever the game is waiting
    // for stands beside it: the pie rule while it is on offer, a fresh game once
    // this one is over. Replacing the menu button with either of those would leave
    // the one screen a player might want to leave with no way off it.
    val extra: Pair<String, () -> Unit>? =
        when {
            // The pie rule, offered to whoever is about to answer the opening
            // stone - which against the watch is the watch, and it decides for
            // itself.
            state.canSwap && state.settings.mode == Mode.TWO_PLAYERS ->
                stringResource(R.string.swap) to viewModel::swapSides
            state.winner != EMPTY -> stringResource(R.string.again) to viewModel::startGame
            else -> null
        }

    val menu = stringResource(R.string.menu) to viewModel::showMenu
    val buttons = if (extra == null) listOf(menu) else listOf(extra, menu)
    val gap = if (buttons.size > 1) metrics.gap else 0
    val width = (row.w - gap * (buttons.size - 1)) / buttons.size
    buttons.forEachIndexed { index, (label, action) ->
        PillButton(
            box = LayoutBox(row.x + index * (width + gap), row.y, width, row.h),
            text = label,
            onClick = action,
        )
    }
}

/**
 * What the top cap says.
 *
 * Against the watch the two seats are named You and Watch rather than by colour,
 * because that is how a player thinks of them; between two people the colours are
 * the only names there are. A swap gets a line of its own, because nothing on the
 * board moves when it happens and the screen is the only place it can show.
 */
@Composable
private fun statusText(state: HexUiState): String {
    val againstWatch = state.settings.mode == Mode.COMPUTER
    return when {
        state.winner != EMPTY && againstWatch ->
            stringResource(if (isPlayers(state, state.winner)) R.string.win_you else R.string.win_cpu)
        state.winner == RED -> stringResource(R.string.win_red)
        state.winner == BLUE -> stringResource(R.string.win_blue)
        state.thinking -> stringResource(R.string.thinking)
        state.swapAnnounced -> stringResource(R.string.swapped)
        againstWatch ->
            stringResource(if (isPlayers(state, state.turn)) R.string.turn_you else R.string.turn_cpu)
        state.turn == RED -> stringResource(R.string.turn_red)
        else -> stringResource(R.string.turn_blue)
    }
}

private fun statusColor(state: HexUiState): Color =
    when {
        state.thinking || state.swapAnnounced -> ColorMuted
        state.winner == RED || (state.winner == EMPTY && state.turn == RED) -> ColorRed
        else -> ColorBlue
    }

/** Whether a colour is the one the person holding the watch is playing. */
private fun isPlayers(
    state: HexUiState,
    color: Byte,
): Boolean = state.firstSeatColor == color
