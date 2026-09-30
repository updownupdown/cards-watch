package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Lays out [columns] x [rows] cells over the whole of its bounds (the game
 * board). Cells are numbered row by row from the top left.
 *
 * @param gap the space between cells, as a fraction of a cell's width.
 * @param onTap called with a cell's index when it's tapped; null makes the
 *   grid display-only.
 * @param cell draws a cell, stretched to fill it: usually a [PlayingCard].
 */
@Composable
fun CardGrid(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    gap: Float = 0.08f,
    onTap: ((index: Int) -> Unit)? = null,
    cell: @Composable (index: Int) -> Unit,
) {
    BoxWithConstraints(modifier) {
        // Solve width = columns * cell + (columns - 1) * gap * cell.
        val cellWidth: Dp = maxWidth / (columns + (columns - 1) * gap)
        val gapSize: Dp = cellWidth * gap
        val cellHeight: Dp = (maxHeight - gapSize * (rows - 1)) / rows

        for (index in 0 until columns * rows) {
            val col = index % columns
            val row = index / columns
            Box(
                Modifier
                    .offset(x = (cellWidth + gapSize) * col, y = (cellHeight + gapSize) * row)
                    .size(cellWidth, cellHeight)
                    .then(
                        if (onTap != null) {
                            Modifier.clickable(interactionSource = null, indication = null) {
                                onTap(index)
                            }
                        } else {
                            Modifier
                        },
                    ),
                propagateMinConstraints = true,
            ) {
                cell(index)
            }
        }
    }
}

/** A cell's position and size on a [CardGrid]-style board, for laying out by hand. */
data class CellBounds(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

fun cellBounds(columns: Int, rows: Int, width: Dp, height: Dp, gap: Float, index: Int): CellBounds {
    val cellWidth = width / (columns + (columns - 1) * gap)
    val gapSize = cellWidth * gap
    val cellHeight = (height - gapSize * (rows - 1)) / rows
    return CellBounds(
        x = (cellWidth + gapSize) * (index % columns),
        y = (cellHeight + gapSize) * (index / columns),
        width = cellWidth,
        height = cellHeight,
    )
}
