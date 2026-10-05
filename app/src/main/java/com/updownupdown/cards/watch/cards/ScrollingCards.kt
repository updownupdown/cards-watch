package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.ui.LocalBoardScroll

/** Room round the cards for a picked card's border. */
private val PAD = 2.dp
/** How much of the window's top and foot fades out while there are cards beyond it. */
private val FADE = 10.dp

/**
 * Where the cards of a [ScrollingCards] grid go: [columns] across the
 * window's width, the first row at the top (or, with few rows, in the middle).
 */
class GridPlaces(val columns: Int, width: Dp, gap: Float, private val top: Dp) {
    val cardWidth: Dp = width / (columns + (columns - 1) * gap)
    val cardHeight: Dp get() = cardWidth * CARD_ASPECT
    val gap: Dp = cardWidth * gap

    fun x(column: Int): Dp = (cardWidth + gap) * column
    fun y(row: Int): Dp = top + (cardHeight + gap) * row
    fun xOf(index: Int): Dp = x(index % columns)
    fun yOf(index: Int): Dp = y(index / columns)

    /** The height of [rows] rows. */
    fun height(rows: Int): Dp = if (rows <= 0) 0.dp else cardHeight * rows + gap * (rows - 1)
}

/**
 * A grid of cards larger than its window ([width] × [window]), scrolling
 * inside it as Accordion's does: by the board's scroll ([LocalBoardScroll]),
 * so the crown turns the cards before the screen, with the window's top and
 * foot fading to black while there are cards beyond them. With [centre], a
 * grid shorter than the window sits in its middle.
 *
 * @param rows how many rows the grid has now.
 */
@Composable
fun ScrollingCards(
    columns: Int,
    rows: Int,
    width: Dp,
    window: Dp,
    modifier: Modifier = Modifier,
    gap: Float = 0.12f,
    centre: Boolean = false,
    scroll: ScrollState = LocalBoardScroll.current ?: rememberScrollState(),
    build: TableBuilder.(GridPlaces) -> Unit,
) {
    val probe = GridPlaces(columns, width, gap, PAD)
    val needed = probe.height(rows) + PAD * 2
    val content = max(window, needed)
    val top = if (centre && needed < window) (window - probe.height(rows)) / 2 else PAD
    val places = GridPlaces(columns, width, gap, top)
    val background = MaterialTheme.colorScheme.background
    val fadeTop = remember(background) { listOf(background, Color.Transparent) }
    Box(
        modifier
            .size(width, window)
            // Cards beyond the window fade out at its edge, which says there are more.
            .drawWithContent {
                drawContent()
                val fade = FADE.toPx()
                if (scroll.value > 0) {
                    drawRect(Brush.verticalGradient(fadeTop, endY = fade), size = Size(size.width, fade))
                }
                if (scroll.value < scroll.maxValue) {
                    val foot = size.height - fade
                    drawRect(
                        Brush.verticalGradient(fadeTop.reversed(), startY = foot, endY = size.height),
                        topLeft = Offset(0f, foot),
                        size = Size(size.width, fade),
                    )
                }
            }
            .verticalScroll(scroll),
        contentAlignment = Alignment.TopStart,
    ) {
        CardTable(Modifier.size(width, content)) { build(places) }
    }
}
