package com.dchernykh.hex.layout

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

// How far the board may be dragged, and what counts as being able to see a cell.
//
// A board bigger than the screen is only playable if every cell can be brought out
// from behind the rim, and a round screen makes that harder than a rectangular
// viewport would: the corners of the rectangle are under the bezel, so the board
// has to be draggable further than "until its edge meets the viewport's".

/** The radius a cell's centre has to stay inside for the whole hexagon to clear the bezel. */
private fun safeReach(
    layout: HexLayout,
    screenSize: Int,
    padding: Int,
): Float = screenSize / 2f - maxOf(0, padding) - layout.scale * HEX_FILL

/**
 * How far a cell's centre may sit above or below the middle before the hexagon runs
 * off the band the board is drawn in. The round screen is not the only thing that
 * hides a cell: the board is painted in a band that stops short of both caps, and a
 * cell past its edge is not drawn at all.
 */
private fun bandRoom(
    layout: HexLayout,
    bandHeight: Int,
): Float = maxOf(0f, bandHeight / 2f - layout.cellHeight / 2f)

/** How far a cell's centre is from the middle of the screen at the given pan. */
fun cellReach(
    layout: HexLayout,
    cell: Int,
    panX: Int,
    panY: Int,
): Float {
    val dx = (panX + layout.offsetsX[cell]).toFloat()
    val dy = (panY + layout.offsetsY[cell]).toFloat()
    return sqrt(dx * dx + dy * dy)
}

/**
 * How far the board may be dragged.
 *
 * The obvious answer - until the board's edge meets the viewport's - is wrong on a
 * round watch, because the corners of that rectangle are under the bezel. The board
 * has to be draggable that bit further, or the cells in its own two acute corners
 * can never be brought out from behind the rim: on a nine-cell board they stopped
 * thirty-six pixels short of clearing it.
 *
 * So the limit is what the furthest cell needs in order to reach the visible
 * circle, taken along the direction it lies in and split into the two axes the drag
 * is clamped on. A board whose cells all clear the bezel where it sits does not
 * move at all, which is why five cells across still sits still.
 */
fun panLimits(
    layout: HexLayout,
    screenSize: Int,
    padding: Int,
    bandHeight: Int,
): PanLimits {
    val safe = safeReach(layout, screenSize, padding)
    val room = bandRoom(layout, bandHeight)
    var limitX = 0
    var limitY = 0
    for (cell in 0 until layout.cellCount) {
        val offsetX = layout.offsetsX[cell]
        val offsetY = layout.offsetsY[cell]
        val reach = cellReach(layout, cell, 0, 0)
        if (reach > safe && reach > 0f) {
            val needed = reach - safe
            limitX = maxOf(limitX, ceil((needed * abs(offsetX)) / reach).toInt())
            limitY = maxOf(limitY, ceil((needed * abs(offsetY)) / reach).toInt())
        }
        // And enough to lift a cell into the band, which on a tall board runs out
        // before the circle does.
        limitY = maxOf(limitY, ceil(abs(offsetY) - room).toInt())
    }
    return PanLimits(limitX, limitY)
}

fun clampPan(
    value: Int,
    limit: Int,
): Int = value.coerceIn(-limit, limit)

/**
 * Whether the whole of a cell clears the bezel: what "you can see this one" has to
 * mean on a round screen, and what decides whether the watch's answer needs the
 * board moved to show it.
 */
@Suppress("LongParameterList")
fun isCellFullyVisible(
    layout: HexLayout,
    cell: Int,
    panX: Int,
    panY: Int,
    screenSize: Int,
    padding: Int,
    bandHeight: Int,
): Boolean {
    if (abs(panY + layout.offsetsY[cell]) > bandRoom(layout, bandHeight)) return false
    return cellReach(layout, cell, panX, panY) <= safeReach(layout, screenSize, padding)
}

/**
 * Whether any part of a cell is on the screen at all, which is what decides whether
 * it is worth drawing. Generous on purpose: a hexagon half under the rim still has
 * a half worth painting.
 */
fun isCellDrawable(
    layout: HexLayout,
    cell: Int,
    panX: Int,
    panY: Int,
    screenSize: Int,
): Boolean = cellReach(layout, cell, panX, panY) <= screenSize / 2f + layout.scale

/**
 * The pan that brings a cell out from behind the rim, clamped to the limits.
 *
 * Aims at the middle of the screen and settles for as close as it can get, which
 * the limits guarantee is close enough to clear the bezel.
 */
fun panToCell(
    layout: HexLayout,
    cell: Int,
    screenSize: Int,
    padding: Int,
    bandHeight: Int,
): Point {
    val limits = panLimits(layout, screenSize, padding, bandHeight)
    return Point(
        x = clampPan(-layout.offsetsX[cell], limits.x),
        y = clampPan(-layout.offsetsY[cell], limits.y),
    )
}
