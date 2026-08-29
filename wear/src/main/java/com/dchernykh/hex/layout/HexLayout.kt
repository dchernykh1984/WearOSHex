package com.dchernykh.hex.layout

import com.dchernykh.hex.game.clampSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Where the rhombus of hexagons sits, and which hexagon a finger landed on.
//
// A Hex board is a rhombus with 60 degree corners, so it is about 1.7 times as
// wide as it is tall. Shrinking a nine-cell board to fit a watch screen left cells
// under two millimetres across - readable, but not something a fingertip can hit.
// So the cell size is fixed at what is comfortable to tap and the board is allowed
// to be bigger than the screen instead: the player drags it around and taps the
// cell they want.
//
// Hexagons are pointy-topped, so a row of them is a straight horizontal line and
// each row is shifted half a cell right of the one above - which is what shears the
// square grid of cells into the rhombus Hex is played on.

private val SQRT3 = sqrt(3.0).toFloat()

/**
 * The hex size is the distance from a hexagon's centre to a corner. Neighbouring
 * centres are SQRT3 of those apart, so a cell is SQRT3 wide and 2 tall.
 *
 * 0.0687 of the screen is 32px on a 466px watch, which makes a cell 55px across -
 * the size the five-cell board already used, and the one that was comfortable to
 * play. Every board uses it, whatever its size.
 */
const val HEX_SIZE_RATIO = 0.0687f

/**
 * How much of a cell the drawn hexagon takes up, leaving a hairline of background
 * between neighbours so the tiling reads as separate cells.
 */
const val HEX_FILL = 0.94f

fun hexSizeFor(screenSize: Int): Int = maxOf(4, (screenSize * HEX_SIZE_RATIO).roundToInt())

private fun unitX(
    column: Int,
    row: Int,
): Float = (column + row / 2f) * SQRT3

private fun unitY(row: Int): Float = row * 1.5f

/** A point on the screen. */
data class Point(
    val x: Int,
    val y: Int,
)

/** How far the board may be dragged, on each axis. */
data class PanLimits(
    val x: Int,
    val y: Int,
)

/**
 * The board, laid out at a fixed cell size around its own centre.
 *
 * Offsets are relative to the middle of the board, so the screen can put that
 * middle wherever the panning has moved it to.
 */
class HexLayout(
    screenSize: Int,
    size: Int,
) {
    val size: Int = clampSize(size)
    val cellCount: Int = this.size * this.size
    val scale: Int = hexSizeFor(screenSize)
    val offsetsX = IntArray(cellCount)
    val offsetsY = IntArray(cellCount)

    /** Half the board's full extent, corners included, which the pan limits are measured against. */
    val halfWidth: Int
    val halfHeight: Int

    val cellWidth: Int = (SQRT3 * scale).roundToInt()
    val cellHeight: Int = 2 * scale

    init {
        // The acute corners are cell (0, 0) and cell (size-1, size-1); the middle
        // of the board is halfway between them.
        val centerX = unitX(this.size - 1, this.size - 1) / 2f
        val centerY = unitY(this.size - 1) / 2f
        for (row in 0 until this.size) {
            for (column in 0 until this.size) {
                val cell = row * this.size + column
                offsetsX[cell] = ((unitX(column, row) - centerX) * scale).roundToInt()
                offsetsY[cell] = ((unitY(row) - centerY) * scale).roundToInt()
            }
        }
        halfWidth = ((unitX(this.size - 1, this.size - 1) / 2f) * scale + (SQRT3 / 2f) * scale).roundToInt()
        halfHeight = ((unitY(this.size - 1) / 2f) * scale + scale).roundToInt()
    }
}

/**
 * The six corners of the hexagon drawn for a cell, given where the middle of the
 * board currently is on screen. Pointy topped: the first corner is straight up.
 */
fun hexCorners(
    layout: HexLayout,
    cell: Int,
    originX: Int,
    originY: Int,
): List<Point> {
    val radius = layout.scale * HEX_FILL
    val centerX = originX + layout.offsetsX[cell]
    val centerY = originY + layout.offsetsY[cell]
    val dx = ((SQRT3 / 2f) * radius).roundToInt()
    val dy = (radius / 2f).roundToInt()
    val top = radius.roundToInt()
    return listOf(
        Point(centerX, centerY - top),
        Point(centerX + dx, centerY - dy),
        Point(centerX + dx, centerY + dy),
        Point(centerX, centerY + top),
        Point(centerX - dx, centerY + dy),
        Point(centerX - dx, centerY - dy),
    )
}

fun cellCenterX(
    layout: HexLayout,
    cell: Int,
    originX: Int,
): Int = originX + layout.offsetsX[cell]

fun cellCenterY(
    layout: HexLayout,
    cell: Int,
    originY: Int,
): Int = originY + layout.offsetsY[cell]

/**
 * Which cell a touch landed on, or -1 for a touch outside the board.
 *
 * Hexagons tile the plane, so the hexagon a point falls in is the one whose centre
 * is nearest - the cells are exactly the Voronoi regions of their own centres.
 * Comparing squared distances keeps it to integer arithmetic, and a touch further
 * than one cell from every centre is off the board rather than snapped to its edge.
 */
fun cellAt(
    layout: HexLayout,
    originX: Int,
    originY: Int,
    x: Int,
    y: Int,
): Int {
    var best = -1
    var bestDistance = 0
    val limit = layout.scale * layout.scale
    for (cell in 0 until layout.cellCount) {
        val dx = x - (originX + layout.offsetsX[cell])
        val dy = y - (originY + layout.offsetsY[cell])
        val distance = dx * dx + dy * dy
        if (best < 0 || distance < bestDistance) {
            best = cell
            bestDistance = distance
        }
    }
    return if (best >= 0 && bestDistance <= limit) best else -1
}
