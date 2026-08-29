package com.dchernykh.hex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.dchernykh.hex.game.BLUE
import com.dchernykh.hex.game.EDGE_BLUE_END
import com.dchernykh.hex.game.EDGE_BLUE_START
import com.dchernykh.hex.game.EDGE_RED_END
import com.dchernykh.hex.game.EDGE_RED_START
import com.dchernykh.hex.game.EMPTY
import com.dchernykh.hex.game.RED
import com.dchernykh.hex.game.Topology
import com.dchernykh.hex.layout.HexLayout
import com.dchernykh.hex.layout.hexCorners
import com.dchernykh.hex.layout.isCellDrawable

/**
 * The rhombus of hexagons.
 *
 * Cells past the edge of the screen are skipped rather than drawn and clipped: a
 * nine-cell board is eighty-one hexagons, most of them off screen at any one pan,
 * and a watch has no cycles to spend painting what nobody can see.
 */
@Composable
fun HexBoard(
    layout: HexLayout,
    topology: Topology,
    cells: List<Byte>,
    lastMove: Int,
    screenSize: Int,
    panX: Int,
    panY: Int,
    modifier: Modifier = Modifier,
) {
    val originX = screenSize / 2 + panX
    val originY = screenSize / 2 + panY

    Canvas(modifier = modifier) {
        for (cell in 0 until layout.cellCount) {
            if (!isCellDrawable(layout, cell, panX, panY, screenSize)) continue
            val corners = hexCorners(layout, cell, originX, originY)
            val path =
                Path().apply {
                    moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
                    for (i in 1 until corners.size) lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
                    close()
                }
            drawPath(path, color = colorFor(topology, cells, cell))

            // A dot on the stone played last. On a board of eighty-one cells it is
            // the only way to see where the answer landed.
            if (cell == lastMove && cells[cell] != EMPTY) {
                drawCircle(
                    color = ColorMark,
                    radius = layout.scale * 0.22f,
                    center =
                        Offset(
                            (originX + layout.offsetsX[cell]).toFloat(),
                            (originY + layout.offsetsY[cell]).toFloat(),
                        ),
                )
            }
        }
    }
}

/**
 * What colour a cell is drawn in: its stone if it has one, and otherwise the tint
 * of whichever edges it sits on.
 */
private fun colorFor(
    topology: Topology,
    cells: List<Byte>,
    cell: Int,
): Color {
    when (cells[cell]) {
        RED -> return ColorRed
        BLUE -> return ColorBlue
    }
    val edges = topology.edges[cell].toInt()
    val red = edges and (EDGE_RED_START or EDGE_RED_END) != 0
    val blue = edges and (EDGE_BLUE_START or EDGE_BLUE_END) != 0
    return when {
        red && blue -> ColorCellBothEdges
        red -> ColorCellRedEdge
        blue -> ColorCellBlueEdge
        else -> ColorCell
    }
}
