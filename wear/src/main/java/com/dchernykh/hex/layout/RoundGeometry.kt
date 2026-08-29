package com.dchernykh.hex.layout

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Keeping content inside a round screen.
//
// A round watch cuts the corners off every row, so a line placed near the top or
// the bottom is sliced by the bezel unless its width is held to the chord of the
// circle at that height. All of it is pure arithmetic in whole screen pixels, so a
// test can ask where something lands without a watch in the room.

/** A pixel box, in screen coordinates. */
data class Box(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    operator fun contains(point: Pair<Int, Int>): Boolean {
        val (px, py) = point
        return px >= x && px < x + w && py >= y && py < y + h
    }
}

/**
 * Half the on-screen width of a circle of this radius, [dy] pixels from its
 * horizontal centre line. Zero past the edge of the circle.
 */
fun safeHalfWidth(
    radius: Float,
    dy: Float,
): Float {
    val distance = abs(dy)
    if (distance >= radius) return 0f
    return sqrt(radius * radius - distance * distance)
}

/**
 * The widest a horizontally centred line may be with its centre at [y], once
 * [padding] is kept clear of the bezel on each side. The binding chord is at
 * whichever end of the line's height is further from the centre line - the top of
 * a line in the upper half, the bottom of one in the lower.
 */
fun safeLineWidth(
    screenSize: Int,
    y: Float,
    lineHeight: Float,
    padding: Int,
): Float {
    val radius = screenSize / 2f
    val dyTop = abs((y - lineHeight / 2f) - radius)
    val dyBottom = abs((y + lineHeight / 2f) - radius)
    val half = safeHalfWidth(radius, maxOf(dyTop, dyBottom))
    val width = 2f * half - 2f * padding
    return if (width > 0f) width else 0f
}

/**
 * A horizontally centred box of the given height whose top edge is at [top],
 * never wider than [maxWidth] and never poking past the bezel.
 */
fun centeredBox(
    screenSize: Int,
    top: Int,
    height: Int,
    maxWidth: Float,
    padding: Int,
): Box {
    val safe = safeLineWidth(screenSize, top + height / 2f, height.toFloat(), padding)
    val width = floor(minOf(maxWidth, safe)).toInt()
    return Box(x = ((screenSize - width) / 2f).roundToInt(), y = top, w = width, h = height)
}
