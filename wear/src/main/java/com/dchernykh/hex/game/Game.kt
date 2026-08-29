package com.dchernykh.hex.game

// The rules of Hex, with nothing Android in them, so every rule is exercised by a
// unit test rather than by squinting at a watch.
//
// Players take turns placing one stone on any empty cell; stones are never moved
// or removed. Red joins the top and bottom edges, Blue the left and right ones,
// and the first to finish an unbroken chain between its own two edges wins. A
// filled Hex board always contains exactly one such chain, so the game can never
// be drawn.
//
// Because stones only ever appear, connectivity is a union-find: joining the new
// stone to its like-coloured neighbours - and to a virtual node per edge - turns
// "has anybody won?" into one comparison instead of a flood fill per move.
//
// Seats and colours are kept apart. Red always moves first, but the pie (swap)
// rule lets the second player take the opening stone for itself, which swaps
// which seat owns which colour without disturbing the colour order.

const val SEAT_FIRST = 0
const val SEAT_SECOND = 1

/**
 * A game in progress.
 *
 * Mutable, unlike the other ports in this family, and deliberately: the search
 * plays and unplays thousands of positions per move on a watch CPU, and a value
 * type would allocate a board for every one of them. The rules are still pure
 * functions of what they are handed, and the search never touches this object -
 * it works on a copy of the cells.
 */
class Game(
    size: Int,
    swapRule: Boolean = true,
) {
    val topology: Topology = topologyFor(size)
    val size: Int get() = topology.size

    val cells = ByteArray(topology.cellCount)

    // Four virtual nodes past the last cell, one per edge, so a chain that reaches
    // an edge is joined to it rather than scanned for.
    private val parent = IntArray(topology.cellCount + 4) { it }
    private val weight = IntArray(topology.cellCount + 4) { 1 }

    var turn: Byte = RED
        private set

    var winner: Byte = EMPTY
        private set

    var moveCount: Int = 0
        private set

    var lastMove: Int = -1
        private set

    val swapRule: Boolean = swapRule

    var swapUsed: Boolean = false
        private set

    /** The colour each seat plays. */
    private val seatColors = byteArrayOf(RED, BLUE)

    val isFinished: Boolean get() = winner != EMPTY

    private fun find(node: Int): Int {
        var root = node
        while (parent[root] != root) root = parent[root]
        // Path compression, so a long chain of stones stays cheap to query.
        var current = node
        while (parent[current] != root) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }

    private fun union(
        a: Int,
        b: Int,
    ) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA == rootB) return
        if (weight[rootA] < weight[rootB]) {
            parent[rootA] = rootB
            weight[rootB] += weight[rootA]
        } else {
            parent[rootB] = rootA
            weight[rootA] += weight[rootB]
        }
    }

    fun isLegalMove(cell: Int): Boolean =
        winner == EMPTY && cell >= 0 && cell < topology.cellCount && cells[cell] == EMPTY

    /**
     * Place the stone of whoever is to move, and report whether it went down. An
     * illegal cell changes nothing, so a stray tap on an occupied cell is ignored.
     */
    fun play(cell: Int): Boolean {
        if (!isLegalMove(cell)) return false

        val color = turn
        cells[cell] = color

        val base = cell * MAX_NEIGHBORS
        val count = topology.degree[cell].toInt()
        for (i in 0 until count) {
            val next = topology.neighbors[base + i]
            if (cells[next] == color) union(cell, next)
        }

        val startSlot = borderSlot(color, 0)
        val endSlot = borderSlot(color, 1)
        val mask = topology.edges[cell].toInt()
        if (mask and BORDER_MASKS[startSlot] != 0) union(cell, topology.cellCount + startSlot)
        if (mask and BORDER_MASKS[endSlot] != 0) union(cell, topology.cellCount + endSlot)

        moveCount++
        lastMove = cell

        if (find(topology.cellCount + startSlot) == find(topology.cellCount + endSlot)) {
            winner = color
        } else {
            turn = opponent(color)
        }
        return true
    }

    /** The pie rule is offered exactly once: to the second player, in reply to the opening stone. */
    fun canSwap(): Boolean = swapRule && !swapUsed && winner == EMPTY && moveCount == 1

    /**
     * Take the opening stone instead of answering it.
     *
     * The stone keeps its colour and the colour order is untouched - what changes
     * is which seat owns which colour, so the seat that opened is now the one to
     * move. Nothing on the board moves, which is exactly why the screen has to say
     * that it happened.
     */
    fun swapSides(): Boolean {
        if (!canSwap()) return false
        val first = seatColors[SEAT_FIRST]
        seatColors[SEAT_FIRST] = seatColors[SEAT_SECOND]
        seatColors[SEAT_SECOND] = first
        swapUsed = true
        return true
    }

    fun colorForSeat(seat: Int): Byte = seatColors[if (seat == SEAT_SECOND) SEAT_SECOND else SEAT_FIRST]

    fun seatForColor(color: Byte): Int = if (seatColors[SEAT_FIRST] == color) SEAT_FIRST else SEAT_SECOND

    /** Which seat has to act now. Meaningless once the game is over, so callers check first. */
    fun seatToMove(): Int = seatForColor(turn)
}
