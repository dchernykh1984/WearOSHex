package com.dchernykh.hex.game

import androidx.annotation.StringRes
import com.dchernykh.hex.R

// What the game remembers between runs.
//
// Two seats at one watch, or one seat against the watch; a board size; and the pie
// rule. Each is an enum rather than a stored number, so a build that adds a board
// size cannot be confused by what an older build wrote - an unknown name reads as
// the default rather than as whatever happens to sit at that index.

/** Two seats at one watch, or one seat against the watch. */
enum class Mode(
    @param:StringRes val labelRes: Int,
) {
    TWO_PLAYERS(R.string.mode_two),
    COMPUTER(R.string.mode_cpu),
    ;

    val next: Mode get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Playing against the watch is what a lone owner can do straight away. */
        val DEFAULT = COMPUTER

        fun fromStoredName(name: String?): Mode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The boards the settings screen offers. The label is digits only - "7x7" - so it
 * needs no translating.
 */
enum class BoardSize(
    val cells: Int,
) {
    SMALL(5),
    MEDIUM(7),
    LARGE(9),
    ;

    val label: String get() = "${cells}x$cells"

    val next: BoardSize get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = MEDIUM

        fun fromStoredName(name: String?): BoardSize = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The pie rule, on or off.
 *
 * It is on by default because it is how Hex is normally played - without it the
 * player who opens has a proven advantage - but it takes some explaining, and a
 * player who would rather just keep the stone they put down can turn it off.
 */
enum class SwapRule(
    val enabled: Boolean,
    @param:StringRes val labelRes: Int,
) {
    OFF(enabled = false, labelRes = R.string.swap_off),
    ON(enabled = true, labelRes = R.string.swap_on),
    ;

    val next: SwapRule get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = ON

        fun fromStoredName(name: String?): SwapRule = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
