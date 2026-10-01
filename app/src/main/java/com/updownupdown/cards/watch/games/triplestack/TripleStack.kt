package com.updownupdown.cards.watch.games.triplestack

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.BottomButton
import kotlin.random.Random
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
)

object TripleStack : Game<TripleStackState> {
    const val HAND = 8

    override val id = "triple-stack"
    override val thumbnail = R.drawable.thumb_triple_stack
    override val banner = R.drawable.banner_triple_stack
    override val accent = Color(0xFF49261D)
    override val title = "Triple Stack"
    override val stateSerializer: KSerializer<TripleStackState> = TripleStackState.serializer()

    override val rules = listOf(
        "## Objective",
        "**Win** by using up every ingredient in the deck to serve sandwiches.",
        "**Lose** when no sandwich can be made while ingredients remain.",
        "## Serving",
        "- Tap three cards with **equally spaced ranks**, then **Serve**. Aces, Jacks, Queens and Kings are 1, 11, 12 and 13.",
        "- Ranks wrap from King to Ace: 9, Queen and 2 make a sandwich.",
        "## Refills",
        "After serving, new cards come in:",
        "- Four for a sandwich of one suit",
        "- Three for one colour",
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
        if (state.hand[index] == null) return null
        return when {
            index in state.selected -> state.copy(selected = state.selected - index)
            state.selected.size >= 3 -> null
            else -> state.copy(selected = state.selected + index)
        }
    }

    private fun selectedCards(state: TripleStackState) = state.selected.mapNotNull { state.hand[it] }

    fun serve(state: TripleStackState): TripleStackState? {
        val cards = selectedCards(state)
        if (!isSandwich(cards)) return null
        val hand = state.hand.mapIndexed { i, c -> if (i in state.selected) null else c }.toMutableList()
        // The hand never holds more than its eight places.
        val count = minOf(refills(cards), state.deck.size, hand.count { it == null })
        var deck = state.deck
        repeat(count) {
            val empty = hand.indexOfFirst { it == null }
            hand[empty] = deck.first()
            deck = deck.drop(1)
        }
        return TripleStackState(hand, deck)
    }

    /** Picking cards isn't worth an undo step; serving is. */
    override fun isUndoStep(previous: TripleStackState, next: TripleStackState) = previous.hand != next.hand

    override fun outcome(state: TripleStackState) = when {
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

    @Composable
    override fun Board(state: TripleStackState, play: (TripleStackState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val picked = selectedCards(state)
        val selection = when {
            picked.size < 3 -> Selection.Neutral
            isSandwich(picked) -> Selection.Valid
            else -> Selection.Invalid
        }
        CardTable(
            modifier,
            overlay = { BoardText("Ingredients: ${state.deck.size}", Modifier.align(Alignment.TopCenter)) },
        ) {
            // The hand, centred below the ingredients count.
            val label = 16.dp
            val layout = CardLayout(4, 2, width, height - label * 2)
            state.hand.forEachIndexed { i, card ->
                val x = layout.xOf(i)
                val y = label + layout.yOf(i)
                if (card != null) {
                    card(card, card, x, y, layout.cardWidth, selection = if (i in state.selected) selection else null)
                } else {
                    slot(x, y, layout.cardWidth)
                }
                tap(x, y, layout.cardWidth, layout.cardHeight) { feedback.tryMove(toggle(state, i), play) }
            }
        }
    }
}
