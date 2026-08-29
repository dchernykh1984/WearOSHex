package com.dchernykh.hex.ui

import androidx.compose.ui.graphics.Color

// The colours, carried over unchanged from the Zepp OS original so the two
// versions of the game look like the same game.

/**
 * Pixels kept between anything drawn and the bezel, and the height reserved at the
 * top and the bottom for the status line and the buttons.
 *
 * A Hex rhombus is far wider than it is tall, so on a round screen these caps come
 * out at about a quarter of the diameter each without the board having to give
 * anything up; the reserve is there so that stays true on a screen shape nobody has
 * seen yet.
 */
const val SCREEN_PADDING = 8
const val MIN_CAP = 96

val ColorBackground = Color(0xFF000000)

/**
 * An empty cell, and an empty cell on one of the four edges.
 *
 * Tinting the border cells is how each player is shown which two sides are theirs.
 * The four corner cells carry an edge of each player, which is exactly how Hex
 * counts them, so they get a colour of their own rather than being forced into one
 * side or the other.
 */
val ColorCell = Color(0xFF232A31)
val ColorCellRedEdge = Color(0xFF4D2028)
val ColorCellBlueEdge = Color(0xFF1B3550)
val ColorCellBothEdges = Color(0xFF3A2B4A)

val ColorRed = Color(0xFFF4593C)
val ColorBlue = Color(0xFF3F8EF0)

/** The dot marking the stone played last, dark enough to read on either colour. */
val ColorMark = Color(0xFF0D1117)

val ColorText = Color(0xFFFFFFFF)
val ColorMuted = Color(0xFF9AA4AB)
val ColorButton = Color(0xFF1D262C)
val ColorButtonPressed = Color(0xFF2F3D46)

/**
 * How far a finger may travel and still count as a tap rather than a drag.
 *
 * A fingertip never lands and lifts on exactly one pixel, so without some slack a
 * board that can be dragged would swallow half the taps meant to place a stone.
 */
const val DRAG_SLOP = 8f
