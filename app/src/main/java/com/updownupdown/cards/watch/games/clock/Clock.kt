package com.updownupdown.cards.watch.games.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.LocalBoardSize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

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
    override val thumbnail = R.drawable.thumb_clock
    override val banner = R.drawable.banner_clock
    override val accent = Color(0xFF4F3F17)
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

    // The hours need more room than a square board has: this one is larger
    // and lower, reaching the screen's foot (there's no button while playing).
    override val boardFraction = 0.8f
    override val boardLift = -0.03f
    /** A card's width, × the board's: the most that keeps neighbouring hours apart. */
    private const val CARD_WIDTH = 0.126f

    @Composable
    override fun Board(state: ClockState, play: (ClockState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val over = outcome(state) != null
        val board = LocalBoardSize.current
        CardTable(
            modifier,
            overlay = {
                // Over and under the Kings' pile in the middle.
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText("Kings")
                    Spacer(Modifier.height(board * CARD_WIDTH * (CARD_ASPECT + CardStyle.PILE_OFFSET) + 6.dp))
                    BoardText("${state.kings} of 4")
                }
            },
        ) {
            // The hours round a circle, clockwise from 12 at the top; Kings in the middle.
            val cardWidth = width * CARD_WIDTH
            val edge = pileEdge(cardWidth)
            val pileWidth = cardWidth + edge
            val pileHeight = cardWidth * CARD_ASPECT + edge
            val radius = (min(width, height) - pileHeight) / 2
            for (pile in 0 until 13) {
                val (cx, cy) = if (pile == 0) {
                    width / 2 to height / 2
                } else {
                    val angle = Math.toRadians(pile * 30.0)
                    (width / 2 + radius * sin(angle).toFloat()) to (height / 2 - radius * cos(angle).toFloat())
                }
                val cards = state.piles[pile]
                pile(
                    cards = cards.map { it.card },
                    x = cx - pileWidth / 2,
                    y = cy - pileHeight / 2,
                    cardWidth = cardWidth,
                    faceDown = { !cards[it].faceUp },
                    overlay = if (!over && pile == state.next) CardOverlay.Hint else null,
                    z = if (pile == 0) 1f else 0f,
                ) {
                    feedback.tryMove(if (pile == state.next) reveal(state) else null, play)
                }
            }
        }
    }
}
