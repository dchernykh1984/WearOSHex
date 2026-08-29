package com.dchernykh.hex.game

// Which cells are worth searching at all.
//
// Rating the whole board costs four distance passes, which is cheaper than
// evaluating even one move per cell would be - and the handful of cells it picks
// out is what makes a search on a watch CPU affordable in the first place.

private fun clamp(
    distance: Int,
    cap: Int,
): Int = if (distance > cap) cap else distance

/**
 * Keep the [width] lowest-keyed cells seen so far, sorted, by insertion. Width is
 * a handful, so this beats sorting the whole board.
 */
private fun insert(
    moves: IntArray,
    keys: IntArray,
    count: Int,
    width: Int,
    cell: Int,
    key: Int,
): Int {
    if (count == width && key >= keys[count - 1]) return count
    var position = if (count < width) count else width - 1
    while (position > 0 && keys[position - 1] > key) {
        keys[position] = keys[position - 1]
        moves[position] = moves[position - 1]
        position -= 1
    }
    keys[position] = key
    moves[position] = cell
    return if (count < width) count + 1 else count
}

/**
 * Fill [moves] with the most promising empty cells, best first, and return how
 * many there are.
 *
 * A cell is rated by how long a crossing that runs through it would be, for both
 * players at once: the distance from one edge to the cell plus the distance from
 * the cell to the other edge, summed over the two players. The cells both sides
 * still need are the cells Hex is decided on, and ties go to the more central one.
 *
 * The rating deliberately uses the plain shortest path rather than the
 * two-distance the evaluation is built on. Two-distance answers a coarser question
 * and leaves most of the board on the same plateau, which is fine for judging a
 * position but no use for telling two moves apart; scoring the candidates by the
 * sharper measure was worth more here than another ply of search.
 */
fun orderMoves(
    context: SearchContext,
    cells: ByteArray,
    player: Byte,
    moves: IntArray,
    width: Int,
): Int {
    val topology = context.topology
    val fields = context.fields
    val foe = opponent(player)

    distanceField(topology, cells, player, 0, 1, context, fields[0])
    distanceField(topology, cells, player, 1, 1, context, fields[1])
    distanceField(topology, cells, foe, 0, 1, context, fields[2])
    distanceField(topology, cells, foe, 1, 1, context, fields[3])

    val cap = 4 * topology.size
    val bias = topology.centerBias
    val keys = context.keys
    val limit = width.coerceIn(1, Level.maxWidth)
    var count = 0

    for (cell in 0 until topology.cellCount) {
        if (cells[cell] != EMPTY) continue
        val mine = clamp(fields[0][cell], cap) + clamp(fields[1][cell], cap)
        val theirs = clamp(fields[2][cell], cap) + clamp(fields[3][cell], cap)
        // centerBias is always below 4 * size, so it decides the order only
        // between cells whose routes are equally long.
        val key = (mine + theirs) * (4 * topology.size) + bias[cell]
        count = insert(moves, keys, count, limit, cell, key)
    }

    return count
}
