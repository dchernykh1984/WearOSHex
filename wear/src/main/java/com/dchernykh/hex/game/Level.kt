package com.dchernykh.hex.game

import androidx.annotation.StringRes
import com.dchernykh.hex.R

/**
 * How hard the watch plays.
 *
 * - [depth] plies of search; zero means "do not search at all".
 * - [width] candidate moves considered at the root.
 * - [inner] candidate moves considered below it. Breadth at the root is what
 *   decides how well a level plays - the rating that picks the candidates is good
 *   but not good enough to trust its top eight - so the tree is kept affordable by
 *   narrowing with depth instead.
 * - [effort] leaf evaluations allowed for a whole move, before the board size is
 *   taken into account (see [nodeBudget]).
 *
 * The name is the storage key, so a level must never be renamed.
 */
enum class Level(
    val depth: Int,
    val width: Int,
    val inner: Int,
    val effort: Int,
    @param:StringRes val labelRes: Int,
) {
    EASY(depth = 0, width = 0, inner = 0, effort = 0, labelRes = R.string.level_easy),
    NORMAL(depth = 1, width = 12, inner = 12, effort = 1_600, labelRes = R.string.level_normal),
    HARD(depth = 3, width = 12, inner = 8, effort = 90_000, labelRes = R.string.level_hard),
    ;

    /** The next level in the cycle, so one button walks through all of them. */
    val next: Level get() = entries[(ordinal + 1) % entries.size]

    /**
     * How many leaves this level may look at on a board of [cellCount] cells.
     *
     * A leaf evaluation costs a handful of passes over the board, so the budget is
     * divided by the board area: a big board then costs the watch about the same
     * wall clock as a small one.
     */
    fun nodeBudget(cellCount: Int): Int {
        if (depth == 0) return 0
        return maxOf(width, Math.round(effort.toFloat() / maxOf(1, cellCount)))
    }

    companion object {
        val DEFAULT = NORMAL

        val maxDepth: Int = entries.maxOf { it.depth }

        val maxWidth: Int = maxOf(1, entries.maxOf { maxOf(it.width, it.inner) })

        /**
         * The level a stored name refers to, or the default. Anything unrecognised
         * reads as the default rather than as the first entry, which would quietly
         * move everyone to the easiest game.
         */
        fun fromStoredName(name: String?): Level = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
