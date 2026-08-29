package com.dchernykh.hex.game

import kotlin.random.Random

// The computer opponent.
//
// A watch has no cycles to burn, so the search is bounded by work rather than by
// depth: an alpha-beta over a handful of candidate moves, deepened two plies at a
// time until a fixed budget of leaf evaluations runs out, keeping the best move of
// the last iteration that finished. (Two plies and not one because the evaluation
// says nothing about whose turn it is; see chooseMove.) Everything it touches is a
// flat integer buffer allocated once per board size and reused, so a whole move
// costs no allocations at all.
//
// Three things make that budget go far:
//
//   * Candidates. Every cell is rated by how close it lies to a route either
//     player still needs, and only the best handful are searched. Four distance
//     passes rate the whole board at once, which is cheaper than evaluating even
//     one move per cell would be.
//   * Tactics before search. A crossing that can be finished this move, by either
//     side, is read straight off those same distance passes, so no level ever has
//     to spend search on the one move that obviously has to be played.
//   * Termination. A move that wins is recognised by a flood fill from the stone
//     just placed, not by a full evaluation, and ends that branch immediately.

/** Beyond any score [evaluate] can return, so it is safe as an opening window. */
private const val INFINITE = WIN_SCORE * 2

/** Every buffer a search needs, sized for one board and reused while the size holds. */
class SearchContext(
    topology: Topology,
) : Scratch(topology) {
    val board = ByteArray(topology.cellCount)
    val mark = IntArray(topology.cellCount)
    val keys = IntArray(Level.maxWidth)
    val empties = IntArray(topology.cellCount)
    val moveLists = Array(Level.maxDepth + 1) { IntArray(Level.maxWidth) }
    var generation = 0
    var nodes = 0
    var nodeLimit = 0
    var width = 1
    var innerWidth = 1
    var aborted = false
    var threatCell = -1
}

private var cached: SearchContext? = null

private fun contextFor(topology: Topology): SearchContext {
    val existing = cached
    if (existing != null && existing.topology === topology) return existing
    val fresh = SearchContext(topology)
    cached = fresh
    return fresh
}

/**
 * Whether the stone just placed on [cell] completed a crossing chain for [color].
 * Only the group the new stone belongs to can have changed, so this walks that
 * group alone rather than the whole board.
 */
private fun connects(
    context: SearchContext,
    cells: ByteArray,
    color: Byte,
    cell: Int,
): Boolean {
    val topology = context.topology
    val startMask = borderMask(color, 0)
    val endMask = borderMask(color, 1)
    val mark = context.mark
    val queue = context.queue
    context.generation += 1
    val stamp = context.generation

    var head = 0
    var tail = 0
    queue[tail] = cell
    tail += 1
    mark[cell] = stamp
    var seen = topology.edges[cell].toInt()

    while (head < tail) {
        val current = queue[head]
        head += 1
        val base = current * MAX_NEIGHBORS
        val count = topology.degree[current].toInt()
        for (i in 0 until count) {
            val next = topology.neighbors[base + i]
            if (mark[next] == stamp || cells[next] != color) continue
            mark[next] = stamp
            seen = seen or topology.edges[next].toInt()
            queue[tail] = next
            tail += 1
        }
    }

    return seen and startMask != 0 && seen and endMask != 0
}

/**
 * Alpha-beta, scored from the point of view of whoever is to move.
 *
 * [ply] counts up from the root, and a win is worth that much less for every ply
 * it took to reach, so of two winning lines the shorter one is preferred.
 */
@Suppress("LongParameterList")
private fun negamax(
    context: SearchContext,
    cells: ByteArray,
    toMove: Byte,
    depth: Int,
    ply: Int,
    alpha: Int,
    beta: Int,
): Int {
    if (depth <= 0) {
        context.nodes += 1
        return evaluate(context.topology, cells, toMove, context)
    }

    val moves = context.moveLists[ply]
    val count = orderMoves(context, cells, toMove, moves, context.innerWidth)
    if (count == 0) {
        context.nodes += 1
        return evaluate(context.topology, cells, toMove, context)
    }

    val foe = opponent(toMove)
    // `best` is what this node will report; `lower` is the best either this node or
    // an ancestor has already secured, which is what the children are searched
    // against. They part company only until the first move comes back.
    var best = -INFINITE
    var lower = alpha

    for (i in 0 until count) {
        val move = moves[i]
        cells[move] = toMove
        val value =
            if (connects(context, cells, toMove, move)) {
                WIN_SCORE - ply
            } else {
                -negamax(context, cells, foe, depth - 1, ply + 1, -beta, -lower)
            }
        cells[move] = EMPTY

        if (value > best) best = value
        if (best > lower) lower = best
        // Two ways to stop, decided together so the loop has one exit. The first:
        // the opponent already has a reply it prefers to anything this node can now
        // promise, so the rest of the move list will never be reached. The second:
        // out of budget with moves still unexamined, which means this node now
        // reports less than it is worth and the iteration it belongs to is thrown
        // away.
        val outOfBudget = context.nodes >= context.nodeLimit && i + 1 < count
        if (outOfBudget) context.aborted = true
        if (lower >= beta || outOfBudget) break
    }

    return best
}

/** The move a root search chose, and what it thought of it. */
private data class RootResult(
    val move: Int,
    val score: Int,
)

/**
 * One full-width iteration from the root. [preferred] is the best move of the
 * previous, shallower iteration; searching it first makes the window tight
 * straight away, which is most of what alpha-beta pruning lives on.
 */
private fun searchRoot(
    context: SearchContext,
    cells: ByteArray,
    toMove: Byte,
    depth: Int,
    preferred: Int,
): RootResult {
    val moves = context.moveLists[0]
    val count = orderMoves(context, cells, toMove, moves, context.width)
    if (count == 0) return RootResult(move = -1, score = 0)

    for (i in 1 until count) {
        if (moves[i] == preferred) {
            moves[i] = moves[0]
            moves[0] = preferred
            break
        }
    }

    val foe = opponent(toMove)
    var bestMove = moves[0]
    var bestScore = -INFINITE

    for (i in 0 until count) {
        val move = moves[i]
        cells[move] = toMove
        val value =
            if (connects(context, cells, toMove, move)) {
                WIN_SCORE
            } else {
                -negamax(context, cells, foe, depth - 1, 1, -INFINITE, -bestScore)
            }
        cells[move] = EMPTY

        if (value > bestScore) {
            bestScore = value
            bestMove = move
        }
        // One exit, as in negamax: a win needs no more looking, and running out of
        // budget with moves still unexamined spoils this iteration.
        val outOfBudget = context.nodes >= context.nodeLimit && i + 1 < count
        if (outOfBudget) context.aborted = true
        if (bestScore >= WIN_SCORE || outOfBudget) break
    }

    return RootResult(move = bestMove, score = bestScore)
}

/**
 * The cells where one stone would finish [player]'s crossing outright, counted;
 * the first of them is left in [SearchContext.threatCell].
 *
 * A stone on cell c joins the near edge when the shortest route from that edge to
 * c is one stone long - c itself - and joins the far edge on the same terms, so
 * the two plain distance fields answer the question for the whole board at once.
 * No stones are placed and nothing is flood filled.
 */
private fun countImmediateWins(
    context: SearchContext,
    cells: ByteArray,
    player: Byte,
): Int {
    val topology = context.topology
    val start = context.fields[0]
    val end = context.fields[1]
    distanceField(topology, cells, player, 0, 1, context, start)
    distanceField(topology, cells, player, 1, 1, context, end)

    var count = 0
    context.threatCell = -1
    for (cell in 0 until topology.cellCount) {
        if (cells[cell] == EMPTY && start[cell] == 1 && end[cell] == 1) {
            if (count == 0) context.threatCell = cell
            count += 1
        }
    }
    return count
}

/**
 * A stone dropped anywhere at all. The easiest level plays this once it has been
 * offered the winning move it never misses, which leaves it a legal opponent a
 * beginner can beat.
 */
private fun randomMove(
    context: SearchContext,
    cells: ByteArray,
    random: Random,
): Int {
    val empties = context.empties
    var count = 0
    for (cell in 0 until context.topology.cellCount) {
        if (cells[cell] == EMPTY) {
            empties[count] = cell
            count += 1
        }
    }
    if (count == 0) return -1
    return empties[random.nextInt(count)]
}

/**
 * The cell the computer plays, or -1 when there is nothing to play. The game is
 * never touched: the search runs on a copy of the board.
 */
fun chooseMove(
    game: Game,
    level: Level,
    random: Random = Random.Default,
): Int {
    if (game.winner != EMPTY) return -1

    val context = contextFor(game.topology)
    val cells = context.board
    game.cells.copyInto(cells)

    val forced = forcedMove(context, cells, game, level, random)
    if (forced != null) return forced

    context.width = level.width
    context.innerWidth = level.inner
    context.nodes = 0
    context.nodeLimit = level.nodeBudget(game.topology.cellCount)
    context.aborted = false

    var best = -1
    // Odd depths only. The evaluation says nothing about whose turn it is, so a
    // position judged after an even number of plies is judged with the wrong side
    // on move and reads as far better than it is; deepening two plies at a time
    // keeps every iteration comparable with the one before it.
    var depth = 1
    while (depth <= level.depth) {
        val result = searchRoot(context, cells, game.turn, depth, best)
        // A cut-short iteration has only looked at part of the move list, so its
        // answer replaces the previous one only when there is no previous one.
        if (result.move >= 0 && (!context.aborted || best < 0)) best = result.move
        // Nothing to search, out of budget, or a win already found: all three mean
        // there is nothing a deeper iteration could add.
        if (result.move < 0 || context.aborted || result.score >= WIN_SCORE - depth) break
        depth += 2
    }

    return if (best >= 0) best else randomMove(context, cells, random)
}

/**
 * The move no level may get wrong, or null when the position needs thinking about.
 *
 * A crossing that can be finished now is finished now, at every level. Otherwise,
 * if the opponent can finish next move and exactly one cell does it, that cell is
 * the only move that is not an immediate loss - two such cells and the game is
 * already gone, so the search may as well play on. The easiest level stops here
 * and drops a stone anywhere, which leaves it a legal opponent a beginner can beat.
 */
private fun forcedMove(
    context: SearchContext,
    cells: ByteArray,
    game: Game,
    level: Level,
    random: Random,
): Int? {
    if (countImmediateWins(context, cells, game.turn) > 0) return context.threatCell
    if (level.depth == 0) return randomMove(context, cells, random)
    if (countImmediateWins(context, cells, opponent(game.turn)) == 1) return context.threatCell
    return null
}

/**
 * Whether the computer takes the opening stone when the pie rule offers it.
 *
 * Two things make an opening not worth taking, and Hex theory agrees on both:
 *
 *  * A stone on the opener's own edge. That edge is already theirs - it is one of
 *    the two sides they are trying to join - so a stone standing on it has bought
 *    almost nothing, and taking it over buys the same nothing. This is what rules
 *    out the acute corners too, since those lie on both edges.
 *  * A stone out towards a corner. What an opening is worth is how much of the
 *    middle it commands, and centerBias is four times the hex distance from the
 *    middle, so half a board's distance is the line drawn here.
 *
 * Judging only by distance from the middle - which is what this did first - took
 * the opening on three cells in four, corners and edge stones included, and made
 * the pie rule feel like it fired at random.
 */
fun shouldSwap(game: Game): Boolean {
    if (!game.canSwap() || game.lastMove < 0) return false
    val cell = game.lastMove
    val topology = game.topology
    val opener = game.cells[cell]
    val ownEdges = borderMask(opener, 0) or borderMask(opener, 1)
    if (topology.edges[cell].toInt() and ownEdges != 0) return false
    return topology.centerBias[cell] <= 2 * (game.size - 1)
}
