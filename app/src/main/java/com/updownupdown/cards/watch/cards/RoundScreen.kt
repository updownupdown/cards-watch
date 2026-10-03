package com.updownupdown.cards.watch.cards

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.ui.BOARD_FRACTION
import com.updownupdown.cards.watch.ui.BOARD_LIFT
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The round screen behind a default-sized board of side [board], for cards
 * that sit out past the board's sides: where a card's rows between two heights
 * ([top] and [bottom], from the board's top) just clear the screen's edge.
 */
class RoundScreen(private val board: Dp, private val lift: Float = BOARD_LIFT) {
    private val diameter = board.value / BOARD_FRACTION
    private val radius = diameter / 2
    private val outside = (diameter - board.value) / 2

    /** The screen's diameter, and the middle's height from the board's top. */
    val size: Dp = diameter.dp
    val middleY: Dp = (board.value / 2 + diameter * lift).dp

    /** How far in from the screen's edge a card at these heights has to start. */
    private fun inset(top: Dp, bottom: Dp): Float {
        // The board is lifted, so the screen's middle is a little below the board's.
        val middle = board.value / 2 + diameter * lift
        val reach = maxOf(abs(top.value - middle), abs(bottom.value - middle)).coerceAtMost(radius)
        return radius - sqrt(radius * radius - reach * reach) + MARGIN
    }

    /** The left edge of the leftmost card spanning [top] to [bottom], in the board's own coordinates. */
    fun left(top: Dp, bottom: Dp): Dp = (inset(top, bottom) - outside).dp

    /** The right edge of the rightmost card spanning [top] to [bottom], in the board's own coordinates. */
    fun right(top: Dp, bottom: Dp): Dp = (board.value + outside - inset(top, bottom)).dp

    private companion object {
        /** The least gap, in dp, between a card and the screen's edge. */
        const val MARGIN = 2f
    }
}
