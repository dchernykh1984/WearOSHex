package com.dchernykh.hex.game

// How good a Hex position is, measured the cheap way.
//
// The yardstick is the classic Hex "two-distance": how many stones a player still
// has to place before it owns a connection the opponent cannot cut. It is a
// shortest-path computation with one twist - a cell counts as reached only once
// TWO of its neighbours have been reached, because a route that hangs on a single
// cell is a route the opponent simply takes. Links that can never be broken (a
// stone touching its own edge, or two stones of the same colour already joined)
// count for both at once.
//
// Setting `required` to 1 instead turns the same routine into the plain shortest
// path: how many stones until a connection at all. That is a weaker measure of
// strength, but it is exact about who is lost - a player with no path left has
// already been cut - so the two together tell the search everything it needs.
//
// Cost per call is O(cells): each cell settles once, and the 0/1 step costs let a
// double-ended queue stand in for a priority queue. Nothing is allocated; the
// caller passes buffers in and gets them back filled.

/** Farther than any real route on any board this app plays. */
const val UNREACHABLE = 30000

/** Returned for a finished position: far above any positional score, so a win is never traded away. */
const val WIN_SCORE = 1000000

/** A connection that needs no more stones: the two-distance of a player who has already won. */
const val CONNECTED = 0

/**
 * The buffers the distance passes need.
 *
 * One set is enough for a whole search: the fields are consumed before the search
 * recurses, so a deeper node may safely overwrite them.
 */
open class Scratch(
    val topology: Topology,
) {
    val hits = IntArray(topology.cellCount + 1)

    /**
     * Each cell is queued at most once, and a zero-cost step pushes to the front
     * while a one-cost step pushes to the back, so starting in the middle leaves
     * room for either.
     */
    val queue = IntArray(2 * topology.cellCount + 8)

    val fields = Array(4) { IntArray(topology.cellCount + 1) }
}

/**
 * Fill [out] with the distance from [player]'s [side] edge to every cell, and
 * return the distance all the way across to the opposite edge ([UNREACHABLE] when
 * there is no route left). `out[cellCount]` holds that same crossing distance, so
 * a caller that keeps the field keeps the total with it.
 *
 * One function on purpose, and the only place in this port carrying a suppression.
 * This is the innermost loop of the search: it runs four times per evaluated
 * position and tens of thousands of times per move on a watch CPU. Its three
 * stages - seeding the source edge, settling a cell, relaxing its neighbours -
 * share the queue, the hits array and the head and tail cursors, so pulling them
 * apart means either threading six buffers and two cursors through three
 * functions or allocating a holder per call. Either would cost more than it
 * bought, and the stages are marked out by the comments below.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun distanceField(
    topology: Topology,
    cells: ByteArray,
    player: Byte,
    side: Int,
    required: Int,
    scratch: Scratch,
    out: IntArray,
): Int {
    val cellCount = topology.cellCount
    val neighbors = topology.neighbors
    val degree = topology.degree
    val edges = topology.edges
    val hits = scratch.hits
    val queue = scratch.queue
    val foe = opponent(player)
    val targetMask = BORDER_MASKS[borderSlot(player, if (side != 0) 0 else 1)]

    for (i in 0..cellCount) {
        out[i] = UNREACHABLE
        hits[i] = 0
    }

    // The queue never holds more than one entry per cell, so a window of that many
    // slots on each side of the start point can absorb every push.
    var head = cellCount + 4
    var tail = head

    // An edge cannot be taken, so a cell touching the source edge is supported by
    // it on its own: seed those cells as settled, one stone away if they are still
    // empty and none at all if they already hold the player's stone.
    for (cell in topology.borders[borderSlot(player, side)]) {
        if (cells[cell] == foe) continue
        val cost = if (cells[cell] == player) 0 else 1
        hits[cell] = required
        out[cell] = cost
        if (cost == 0) {
            head -= 1
            queue[head] = cell
        } else {
            queue[tail] = cell
            tail += 1
        }
    }

    while (head < tail) {
        val cell = queue[head]
        head += 1
        val distance = out[cell]

        if (edges[cell].toInt() and targetMask != 0) {
            hits[cellCount] += if (cells[cell] == player) 2 else 1
            if (hits[cellCount] >= required) {
                // Cells settle in non-decreasing order, so the first crossing found
                // is the shortest and the rest of the board cannot improve on it.
                out[cellCount] = distance
                return distance
            }
        }

        val base = cell * MAX_NEIGHBORS
        val count = degree[cell].toInt()
        for (i in 0 until count) {
            val next = neighbors[base + i]
            if (out[next] != UNREACHABLE || cells[next] == foe) continue
            hits[next] += if (cells[cell] == player && cells[next] == player) 2 else 1
            if (hits[next] >= required) {
                val cost = if (cells[next] == player) 0 else 1
                out[next] = distance + cost
                if (cost == 0) {
                    head -= 1
                    queue[head] = next
                } else {
                    queue[tail] = next
                    tail += 1
                }
            }
        }
    }

    return UNREACHABLE
}

private fun clamp(
    distance: Int,
    cap: Int,
): Int = if (distance > cap) cap else distance

/**
 * How good the position is for [player], in whole numbers: positive is winning.
 *
 * The two-distance difference leads, and the plain path difference breaks the ties
 * it leaves - which is most of them on a quiet board, and all of them once one
 * side is squeezed hard enough that no uncuttable route is left at all.
 */
fun evaluate(
    topology: Topology,
    cells: ByteArray,
    player: Byte,
    scratch: Scratch,
): Int {
    val foe = opponent(player)
    val fields = scratch.fields

    val mine = distanceField(topology, cells, player, 0, 2, scratch, fields[0])
    if (mine == CONNECTED) return WIN_SCORE
    val theirs = distanceField(topology, cells, foe, 0, 2, scratch, fields[1])
    if (theirs == CONNECTED) return -WIN_SCORE

    val cap = 4 * topology.size
    val safeTerm = clamp(theirs, cap) - clamp(mine, cap)
    val minePath = distanceField(topology, cells, player, 0, 1, scratch, fields[2])
    val theirsPath = distanceField(topology, cells, foe, 0, 1, scratch, fields[3])
    val pathTerm = clamp(theirsPath, cap) - clamp(minePath, cap)

    return safeTerm * (2 * cap + 1) + pathTerm
}
