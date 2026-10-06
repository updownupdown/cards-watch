package com.updownupdown.cards.watch.games.triplestack

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.LocalGameAccent
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.BottomButton
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * @param hand eight places; null where a card was served and not replaced.
 * @param deck the ingredients still to come.
 * @param selected the places picked for the next sandwich. Saved with the
 *   game because the Serve button, outside the board, acts on it.
 */
@Serializable
data class TripleStackState(
    val hand: List<Card?>,
    val deck: List<Card>,
    val selected: List<Int> = emptyList(),
    val refill: Int = 0,
)

object TripleStack : Game<TripleStackState> {
    const val HAND = 8

    override val id = "triple-stack"
    override val thumbnail = R.drawable.thumb_triple_stack
    override val banner = R.drawable.banner_triple_stack
    override val credit = ArtCredit("A Country Grocer", "Hans Smidth", "1908 - 1909", "Artvee", "https://artvee.com/dl/a-country-grocer/")
    override val accent = R.color.game_triple_stack
    override val accentPale = R.color.game_triple_stack_pale
    override val title = "Triple Stack"
    override val rating = Rating(difficulty = 70, complexity = 20, luck = 40)
    override val stateSerializer: KSerializer<TripleStackState> = TripleStackState.serializer()

    override val rules = listOf(
        "## Objective",
        "**Win** by using up every ingredient to serve sandwiches.",
        "**Lose** when no sandwich can be made while ingredients remain.",
        "## Serving",
        "Tap three cards with **equally spaced ranks**, then **Serve**. Aces, Jacks, Queens and Kings are 1, 11, 12 and 13.",
        "Ranks wrap from King to Ace, so for example: 9 / Q / 2 makes a valid sandwich.",
        "## Refills",
        "After serving, the number of ingredients that refill your hand depends on the quality of the sandwich served:",
        "- Four for a sandwich make from one suit",
        "- Three for a sandwich made from one color",
        "- Two for any other",
    )

    override fun newGame(random: Random): TripleStackState {
        val deck = shuffledDeck(random)
        return TripleStackState(hand = deck.take(HAND), deck = deck.drop(HAND))
    }

    private fun mod13(v: Int) = ((v % 13) + 13) % 13

    fun isSandwich(cards: List<Card>): Boolean {
        if (cards.size != 3) return false
        val v = cards.map { it.rank.value }
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        return orders.any { (a, b, c) -> mod13(v[b] - v[a]) == mod13(v[c] - v[b]) }
    }

    fun refills(cards: List<Card>): Int = when {
        cards.map { it.suit }.distinct().size == 1 -> 4
        cards.map { it.isRed }.distinct().size == 1 -> 3
        else -> 2
    }

    private fun hasSandwich(hand: List<Card?>): Boolean {
        val cards = hand.filterNotNull()
        for (i in cards.indices) for (j in i + 1 until cards.size) for (k in j + 1 until cards.size) {
            if (isSandwich(listOf(cards[i], cards[j], cards[k]))) return true
        }
        return false
    }

    fun toggle(state: TripleStackState, index: Int): TripleStackState? {
        if (state.hand[index] == null || state.refill > 0) return null
        return when {
            index in state.selected -> state.copy(selected = state.selected - index)
            state.selected.size >= 3 -> null
            else -> state.copy(selected = state.selected + index)
        }
    }

    private fun selectedCards(state: TripleStackState) = state.selected.mapNotNull { state.hand[it] }

    /** Takes the served cards off the table; the refill follows a moment later ([refill]). */
    fun serve(state: TripleStackState): TripleStackState? {
        val cards = selectedCards(state)
        if (!isSandwich(cards) || state.refill > 0) return null
        val hand = state.hand.mapIndexed { i, c -> if (i in state.selected) null else c }
        // The hand never holds more than its eight places.
        val count = minOf(refills(cards), state.deck.size, hand.count { it == null })
        return TripleStackState(hand, state.deck, refill = count)
    }

    /** Fills the hand's empty places from the deck, as many as the served sandwich earned. */
    fun refill(state: TripleStackState): TripleStackState {
        val hand = state.hand.toMutableList()
        var deck = state.deck
        repeat(state.refill) {
            val empty = hand.indexOfFirst { it == null }
            hand[empty] = deck.first()
            deck = deck.drop(1)
        }
        return TripleStackState(hand, deck)
    }

    /** Picking cards isn't worth an undo step, nor is the refill after a serve (which undoes with it). */
    override fun isUndoStep(previous: TripleStackState, next: TripleStackState) =
        previous.refill == 0 && previous.hand != next.hand

    override fun outcome(state: TripleStackState) = when {
        state.refill > 0 -> null
        state.deck.isEmpty() -> Outcome(true, "Every ingredient served!")
        !hasSandwich(state.hand) -> Outcome(false, "No sandwich left")
        else -> null
    }

    @Composable
    override fun Bottom(state: TripleStackState, play: (TripleStackState) -> Unit, feedback: Feedback, size: DpSize) {
        val picked = selectedCards(state)
        val earned = if (isSandwich(picked)) refills(picked) else 0
        BottomButton("Serve ($earned)", { feedback.tryMove(serve(state), play) }, earned > 0)
    }

    /** The cards in the deck at the start: all but the first hand. */
    private const val DECK = 52 - HAND
    /** How long after the served cards leave the refill waits. */
    private const val REFILL_MS = 300L
    /** The bar's width, in cards. */
    private const val BAR_CARDS = 3
    private val BAR_HEIGHT = 4.dp
    /** The gap between the count and the bar, and between the bar and the cards. */
    private val GAP = 5.dp
    /** The room left under the cards. */
    private val FOOT = 6.dp
    /** How far the count and bar sit up from there, towards the time, to stand clear of the cards. */
    private val HEAD_LIFT = 7.dp

    /** The count, the bar and the hand, one under the other with [GAP] between, for a count [label] high. */
    private class Rows(board: Dp, label: Dp) {
        private val head = label + GAP + BAR_HEIGHT + GAP
        val layout = CardLayout(4, 2, board, board - head - FOOT)
        /** The three are centred together in the room over the foot, then the count and bar lifted. */
        val labelY = layout.y(0) - HEAD_LIFT
        val barY = labelY + label + GAP
        val handY = layout.y(0) + head
        val barWidth = layout.cardWidth * BAR_CARDS
    }

    @Composable
    override fun Board(state: TripleStackState, play: (TripleStackState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val picked = selectedCards(state)
        val selection = when {
            picked.size < 3 -> Selection.Neutral
            isSandwich(picked) -> Selection.Valid
            else -> Selection.Invalid
        }
        // Served cards go first; the refill follows after a wait.
        val latest by rememberUpdatedState(state)
        LaunchedEffect(state.refill) {
            if (state.refill > 0) {
                delay(REFILL_MS)
                play(refill(latest))
            }
        }

        val ambient = LocalAmbient.current
        val board = LocalBoardSize.current
        val accent = LocalGameAccent.current
        val label = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
        CardTable(
            modifier,
            overlay = {
                val rows = Rows(board, label)
                BoardText("Ingredients: ${state.deck.size}", Modifier.align(Alignment.TopCenter).offset(y = rows.labelY))
                // How much of the deck is left, under the count: full at the start, emptying as it's served.
                if (!ambient) {
                    val left by animateFloatAsState(state.deck.size / DECK.toFloat(), tween(CARD_MOVE_MS), label = "left")
                    Canvas(Modifier.align(Alignment.TopCenter).offset(y = rows.barY).size(width = rows.barWidth, height = BAR_HEIGHT)) {
                        val round = CornerRadius(size.height / 2)
                        drawRoundRect(AppColors.Track, cornerRadius = round)
                        if (left > 0f) drawRoundRect(accent, size = Size(size.width * left, size.height), cornerRadius = round)
                    }
                }
            },
        ) {
            // The hand, below the ingredients count and its bar.
            val rows = Rows(width, label)
            val layout = rows.layout
            state.hand.forEachIndexed { i, card ->
                val x = layout.xOf(i)
                val y = rows.handY - layout.y(0) + layout.yOf(i)
                // Each place shows under its card, so it looks the same while cards come and go.
                slot(x, y, layout.cardWidth)
                if (card != null) card(card, card, x, y, layout.cardWidth, selection = if (i in state.selected) selection else null)
                tap(x, y, layout.cardWidth, layout.cardHeight) { feedback.tryMove(toggle(state, i), play) }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun TripleStackPreview() = GamePreview(TripleStack)

