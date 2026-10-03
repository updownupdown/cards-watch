package com.updownupdown.cards.watch.games.accordion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Removal
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalBoardScroll
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * @param piles the top card of each pile, in order. The cards under a top
 *   card never come back into play, so they aren't kept.
 */
@Serializable
data class AccordionState(val piles: List<Card>)

/** All 52 cards in one line, five to a row: squeeze it to a single pile. */
object Accordion : Game<AccordionState> {
    const val COLUMNS = 5
    private const val CARDS = 52

    /** How far back a pile may move: onto the one before it, or over two. */
    val REACH = listOf(1, 3)

    override val id = "accordion"
    override val thumbnail = R.drawable.thumb_accordion
    override val banner = R.drawable.banner_accordion
    override val accent = R.color.game_accordion
    override val accentPale = R.color.game_accordion_pale
    override val title = "Accordion"
    override val stateSerializer: KSerializer<AccordionState> = AccordionState.serializer()

    // Nothing under the board, which scrolls: it sits in the middle, as wide as leaves
    // the round screen room for three rows and half of the next, to show there are more.
    override val boardFraction = 0.72f
    override val boardLift = 0f

    override val rules = listOf(
        "## Objective",
        "**Win** by consolidate all 52 cards into a single pile.",
        "**Lose** when no card can move.",
        "## Gameplay",
        "A card can be consolidated onto the card **one or three spaces before it** when their they share a **rank or suit**.",
        "The cards wrap left to right, top to bottom.",
    )

    override fun newGame(random: Random) = AccordionState(shuffledDeck(random))

    fun canMove(state: AccordionState, from: Int, to: Int): Boolean {
        if (from !in state.piles.indices || to < 0 || from - to !in REACH) return false
        val card = state.piles[from]
        val onto = state.piles[to]
        return card.rank == onto.rank || card.suit == onto.suit
    }

    /** Moves the pile at [from] onto the one at [to]; those after it move up a place. */
    fun move(state: AccordionState, from: Int, to: Int): AccordionState? {
        if (!canMove(state, from, to)) return null
        val piles = state.piles.toMutableList()
        piles[to] = piles.removeAt(from)
        return AccordionState(piles)
    }

    private fun hasMove(state: AccordionState) =
        state.piles.indices.any { from -> REACH.any { canMove(state, from, from - it) } }

    override fun outcome(state: AccordionState) = when {
        state.piles.size == 1 -> Outcome(true, "Squeezed into one pile!")
        !hasMove(state) -> Outcome(false, "Stuck with ${state.piles.size} piles")
        else -> null
    }

    /** The gap between cards, × a card's width. */
    private const val GAP = 0.12f
    /** Room round the cards for a picked card's border, and the least gap to the screen's edge. */
    private val PAD = 2.dp
    /** How much of the board's top and foot fades out while there are cards beyond it. */
    private val FADE = 10.dp

    @Composable
    override fun Board(state: AccordionState, play: (AccordionState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        val playing = outcome(state) == null
        val scroll = LocalBoardScroll.current ?: rememberScrollState()
        // A new deal starts from the top.
        LaunchedEffect(state.takeIf { it.piles.size == CARDS }) {
            if (state.piles.size == CARDS) scroll.scrollTo(0)
        }

        val board = LocalBoardSize.current
        val cardWidth = board / (COLUMNS + (COLUMNS - 1) * GAP)
        val cardHeight = cardWidth * CARD_ASPECT
        val gap = cardWidth * GAP
        // The window on the cards: as tall as the round screen has room for at the board's width.
        val diameter = board / boardFraction
        val chord = (sqrt(diameter.value * diameter.value - board.value * board.value)).dp
        val window = min(board, chord - PAD * 2)
        val rows = (state.piles.size + COLUMNS - 1) / COLUMNS
        val content = max(window, cardHeight * rows + gap * (rows - 1) + PAD * 2)

        val background = MaterialTheme.colorScheme.background
        val fadeTop = remember(background) { listOf(background, Color.Transparent) }
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(board, window)
                    // Cards beyond the window fade out at its edge, which says there are more.
                    .drawWithContent {
                        drawContent()
                        val fade = FADE.toPx()
                        if (scroll.value > 0) {
                            drawRect(Brush.verticalGradient(fadeTop, endY = fade), size = Size(size.width, fade))
                        }
                        if (scroll.value < scroll.maxValue) {
                            val top = size.height - fade
                            drawRect(
                                Brush.verticalGradient(fadeTop.reversed(), startY = top, endY = size.height),
                                topLeft = Offset(0f, top),
                                size = Size(size.width, fade),
                            )
                        }
                    }
                    .verticalScroll(scroll),
            ) {
                CardTable(Modifier.size(board, content)) {
                    val from = selected
                    // The piles in reach of the picked one, and whether it can go onto each.
                    val reach = if (from == null) emptyMap() else REACH.map { from - it }.filter { it >= 0 }.associateWith { canMove(state, from, it) }
                    state.piles.forEachIndexed { index, card ->
                        val x = (cardWidth + gap) * (index % COLUMNS)
                        val y = PAD + (cardHeight + gap) * (index / COLUMNS)
                        card(
                            key = card,
                            card = card,
                            x = x,
                            y = y,
                            width = cardWidth,
                            selection = if (from == index) (if (true in reach.values) Selection.Valid else Selection.Neutral) else null,
                            // Where the picked pile can go; a pile in reach it can't go onto is dimmed.
                            overlay = if (reach[index] == true) CardOverlay.Good else null,
                            alpha = if (reach[index] == false) 0.5f else 1f,
                            // A pile moved onto stays put under the one that lands on it.
                            removal = Removal.Vanish,
                        )
                        tap(x, y, cardWidth, cardHeight) {
                            val next = if (playing && from != null) move(state, from, index) else null
                            when {
                                next != null -> feedback.tryMove(next, play)
                                from == index -> selected = null
                                playing -> selected = index
                                else -> feedback.reject()
                            }
                        }
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun AccordionPreview() = GamePreview(Accordion)

