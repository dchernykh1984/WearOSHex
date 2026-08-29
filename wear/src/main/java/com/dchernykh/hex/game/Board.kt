package com.dchernykh.hex.game

import kotlin.math.abs

// The shape of a Hex board: how the cells of an n x n rhombus are numbered, which
// cells touch each other, and which cells sit on which player's edge.
//
// Hex is played on a rhombus of hexagons. Red owns the first and the last row and
// wins by joining them; Blue owns the first and the last column. The four corner
// cells belong to one edge of each player, which is why a cell can carry two edge
// bits at once.
//
// Cells are addressed as `row * size + column`, and the six neighbours of
// (column, row) are the axial hex directions:
//
//     (c-1, r  )  (c+1, r  )
//     (c  , r-1)  (c+1, r-1)
//     (c  , r+1)  (c-1, r+1)
//
// Everything here depends on the board size alone, so it is built once per size
// and cached: the search reads these tables constantly, and a watch has no cycles
// to spare for rebuilding them.

/** Cell contents. EMPTY is 0, so a freshly zeroed board is an empty one. */
const val EMPTY: Byte = 0
const val RED: Byte = 1
const val BLUE: Byte = 2

const val MIN_SIZE = 3
const val MAX_SIZE = 11
const val DEFAULT_SIZE = 7

/**
 * The boards the settings screen offers. Nine cells across is as fine as a round
 * watch screen can draw and still leave something to tap; five is a quick game
 * that fits in a lift ride.
 */
val BOARD_SIZES = intArrayOf(5, 7, 9)

/**
 * Every cell has at most six neighbours, so adjacency is one flat array with a
 * fixed stride rather than an array of arrays: a single allocation, and no pointer
 * chasing per lookup.
 */
const val MAX_NEIGHBORS = 6

/** Edge membership, as bits in [Topology.edges]. */
const val EDGE_RED_START = 1
const val EDGE_RED_END = 2
const val EDGE_BLUE_START = 4
const val EDGE_BLUE_END = 8

/** Indexed by [borderSlot]: red start, red end, blue start, blue end. */
val BORDER_MASKS = intArrayOf(EDGE_RED_START, EDGE_RED_END, EDGE_BLUE_START, EDGE_BLUE_END)

private val DIRECTIONS =
    arrayOf(
        intArrayOf(-1, 0),
        intArrayOf(1, 0),
        intArrayOf(0, -1),
        intArrayOf(1, -1),
        intArrayOf(0, 1),
        intArrayOf(-1, 1),
    )

fun opponent(player: Byte): Byte = if (player == RED) BLUE else RED

/** A stored or user-supplied size, forced into the range the rest of this is written for. */
fun clampSize(size: Int): Int = size.coerceIn(MIN_SIZE, MAX_SIZE)

/**
 * Which of the four edges a (player, side) pair names.
 *
 * Side 0 is the edge a player's chain starts from, side 1 the edge it has to
 * reach; the two are interchangeable, so the search can walk the board from
 * either end.
 */
fun borderSlot(
    player: Byte,
    side: Int,
): Int = (if (player == RED) 0 else 2) + (if (side != 0) 1 else 0)

fun borderMask(
    player: Byte,
    side: Int,
): Int = BORDER_MASKS[borderSlot(player, side)]

/** Everything about a board of one size that never changes while it is played. */
class Topology(
    val size: Int,
) {
    val cellCount = size * size
    val neighbors = IntArray(cellCount * MAX_NEIGHBORS)
    val degree = ByteArray(cellCount)
    val edges = ByteArray(cellCount)
    val centerBias = IntArray(cellCount)
    val borders: Array<IntArray>

    init {
        val collected = Array(4) { mutableListOf<Int>() }
        for (row in 0 until size) {
            for (column in 0 until size) {
                val cell = row * size + column
                var count = 0
                for (direction in DIRECTIONS) {
                    val c = column + direction[0]
                    val r = row + direction[1]
                    if (c in 0 until size && r in 0 until size) {
                        neighbors[cell * MAX_NEIGHBORS + count] = r * size + c
                        count++
                    }
                }
                degree[cell] = count.toByte()

                var mask = 0
                if (row == 0) {
                    mask = mask or EDGE_RED_START
                    collected[0].add(cell)
                }
                if (row == size - 1) {
                    mask = mask or EDGE_RED_END
                    collected[1].add(cell)
                }
                if (column == 0) {
                    mask = mask or EDGE_BLUE_START
                    collected[2].add(cell)
                }
                if (column == size - 1) {
                    mask = mask or EDGE_BLUE_END
                    collected[3].add(cell)
                }
                edges[cell] = mask.toByte()

                // Hex distance from the middle of the board, in doubled
                // coordinates so an even-sized board - whose middle falls between
                // cells - still yields whole numbers. Used only to break ties
                // between equally rated moves, where Hex theory says the more
                // central cell is the better one.
                val dq = 2 * column - (size - 1)
                val dr = 2 * row - (size - 1)
                centerBias[cell] = abs(dq) + abs(dr) + abs(dq + dr)
            }
        }
        borders = Array(4) { collected[it].toIntArray() }
    }
}

private val topologies = HashMap<Int, Topology>()

/**
 * The topology for a board size, built once and kept. Sizes are clamped, so the
 * cache holds at most [MAX_SIZE] - [MIN_SIZE] + 1 entries.
 */
fun topologyFor(size: Int): Topology = topologies.getOrPut(clampSize(size)) { Topology(clampSize(size)) }
