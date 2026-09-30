package com.updownupdown.cards.watch.games.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardPile
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.cellBounds
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ClockCard(val card: Card, val faceUp: Boolean = false)

/**
 * @param piles pile 0 holds the Kings (the middle); piles 1 to 12 are the
 *   hours, Aces at 1, Jacks at 11 and Queens at 12. Each lists bottom to top.
 * @param next the pile to turn up next.
 * @param last the pile holding the card turned up last, still on top of it.
 */
@Serializable
data class ClockState(
    val piles: List<List<ClockCard>>,
    val next: Int = 0,
    val last: Int? = null,
    val kings: Int = 0,
    val revealed: Int = 0,
)

object Clock : Game<ClockState> {
    override val id = "clock"
    override val title = "Clock Solitaire"
    override val stateSerializer: KSerializer<ClockState> = ClockState.serializer()

    override val rules = listOf(
        "## Objective",
        "Turn every card face up, sorting all 52 to their hours, before all four Kings come up.",
        "## Playing",
        "- Tap the highlighted pile to turn up its top card. Start with the Kings in the middle.",
        "- The card goes to its hour: Aces at 1, numbers at their hour, Jacks at 11, Queens at 12 and Kings in the middle. That pile is next.",
        "## Winning and losing",
        "You win when every card is face up. You lose the moment the **fourth King** comes up.",
    )

    fun pileFor(rank: Rank) = if (rank == Rank.King) 0 else rank.value

    override fun newGame(random: Random): ClockState {
        val deck = shuffledDeck(random)
        val piles = List(13) { pile -> deck.filterIndexed { i, _ -> i % 13 == pile }.map { ClockCard(it) } }
        return ClockState(piles)
    }

    /** Turns up the top card of the next pile, moving the last card to its hour first. */
    fun reveal(state: ClockState): ClockState? {
        if (outcome(state) != null) return null
        val piles = state.piles.map { it.toMutableList() }
        state.last?.let { last ->
            // The card turned up last goes under the pile it points to.
            piles[state.next].add(0, piles[last].removeAt(piles[last].lastIndex))
        }
        val pile = piles[state.next]
        val top = pile.last()
        pile[pile.lastIndex] = top.copy(faceUp = true)
        val king = top.card.rank == Rank.King
        return ClockState(
            piles = piles,
            next = pileFor(top.card.rank),
            last = state.next,
            kings = state.kings + if (king) 1 else 0,
            revealed = state.revealed + 1,
        )
    }

    override fun outcome(state: ClockState) = when {
        state.revealed == 52 -> Outcome(true, "Every card is home")
        state.kings == 4 -> Outcome(false, "The fourth King came up")
        else -> null
    }

    override fun status(state: ClockState) = "Kings ${state.kings} of 4"

    /** Where each pile sits on a 4×4 grid: the hours clockwise round the edge, Kings in the middle. */
    private val HOUR_CELLS = listOf(1, 2, 3, 7, 11, 15, 14, 13, 12, 8, 4, 0) // 12, 1, 2, … 11 o'clock

    private fun cellOf(pile: Int) = if (pile == 0) -1 else HOUR_CELLS[pile % 12]

    @Composable
    override fun Board(state: ClockState, play: (ClockState) -> Unit, feedback: Feedback, modifier: Modifier) {
        BoxWithConstraints(modifier) {
            val over = outcome(state) != null
            for (pile in 0 until 13) {
                val bounds = if (pile == 0) {
                    // The Kings take the middle, the size of one card.
                    val a = cellBounds(4, 4, maxWidth, maxHeight, GAP, 5)
                    val b = cellBounds(4, 4, maxWidth, maxHeight, GAP, 10)
                    a.copy(
                        x = (a.x + b.x) / 2,
                        y = (a.y + b.y) / 2,
                    )
                } else {
                    cellBounds(4, 4, maxWidth, maxHeight, GAP, cellOf(pile))
                }
                val cards = state.piles[pile]
                Box(
                    Modifier
                        .offset(bounds.x, bounds.y)
                        .size(bounds.width, bounds.height)
                        .clickable(interactionSource = null, indication = null) {
                            val next = if (pile == state.next) reveal(state) else null
                            if (next == null) feedback.reject() else {
                                feedback.move()
                                play(next)
                            }
                        },
                ) {
                    CardPile(
                        cards = cards.map { it.card },
                        faceUp = { cards[it].faceUp },
                        overlay = if (!over && pile == state.next) CardOverlay.Hint else null,
                        modifier = Modifier.size(bounds.width, bounds.height),
                    )
                }
            }
        }
    }

    private const val GAP = 0.1f
}
