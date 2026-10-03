package com.updownupdown.cards.watch.games.clock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.LocalUndoing
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin
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
    override val thumbnail = R.drawable.thumb_clock
    override val banner = R.drawable.banner_clock
    override val accent = R.color.game_clock
    override val accentPale = R.color.game_clock_pale
    override val title = "Clock Solitaire"
    override val stateSerializer: KSerializer<ClockState> = ClockState.serializer()

    override val rules = listOf(
        "## Objective",
        "Turn every card face up, sorting all 52 to their hours, before all four Kings come up.",
        "## Gameplay",
        "Tap the middle pile to turn up its top card.",
        "Then tap the pile that card belongs to: Aces at 1 o'clock, numbers at their hour, Jacks at 11, Queens at 12 and Kings in the middle.",
    )

    fun pileFor(rank: Rank) = if (rank == Rank.King) 0 else rank.value

    override fun newGame(random: Random): ClockState {
        val deck = shuffledDeck(random)
        val piles =
            List(13) { pile -> deck.filterIndexed { i, _ -> i % 13 == pile }.map { ClockCard(it) } }
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

    /** A card's width, × the board's. */
    private const val CARD_WIDTH = 0.139f

    /** How much of each King that has come up shows beside the middle pile, × a card's width. */
    private const val KING_SHOWN = 0.64f

    /** The height of the pile a card is sent to: over a card on the move, which is over the rest. */
    private const val TARGET_Z = 2001f

    /** How long a new game waits before turning up its first card. */
    private const val START_DELAY_MS = 500L

    /** Where the piles sit on a board of side [board]: the hours round a circle, Kings in the middle. */
    private class Places(board: Dp) {
        val cardWidth = board * CARD_WIDTH
        val cardHeight = cardWidth * CARD_ASPECT
        private val middle = board / 2
        private val radius = (board - cardHeight) / 2

        /** The middle of [pile]. */
        fun centre(pile: Int): DpOffset {
            if (pile == 0) return DpOffset(middle, middle)
            val angle = Math.toRadians(pile * 30.0)
            return DpOffset(
                middle + radius * sin(angle).toFloat(),
                middle - radius * cos(angle).toFloat()
            )
        }

        /** Each hour's cards point away from the middle. */
        fun rotation(pile: Int) = pile * 30f

        /** The middle of the progress ring: inside the hours, and under the Kings where they reach it. */
        val ringRadius = (radius - cardHeight / 2 - RING_GAP - RING_WIDTH / 2) * RING_SCALE
    }

    /** The progress ring's thickness, and the gap between it and the hours. */
    private val RING_WIDTH = 3.dp
    private val RING_GAP = 4.dp

    /** The ring's radius, × what would just clear the hours. */
    private const val RING_SCALE = 0.9f

    private fun kingColour(kings: Int) = when (kings) {
        0 -> AppColors.Good
        1 -> AppColors.Neutral
        2 -> AppColors.PaleYellow
        3 -> AppColors.ButtonOrange
        else -> AppColors.Bad
    }

    /** How many cards are face up, as a ring under the cards, its colour showing the Kings that have come up. */
    @Composable
    private fun Ring(state: ClockState) {
        val progress by animateFloatAsState(
            state.revealed / 52f,
            tween(CARD_MOVE_MS),
            label = "progress"
        )
        val ringColour = kingColour(state.kings)
        Canvas(Modifier.fillMaxSize()) {
            val radius = Places(size.minDimension.toDp()).ringRadius.toPx()
            val stroke = Stroke(RING_WIDTH.toPx(), cap = StrokeCap.Round)
            val corner = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawArc(AppColors.Track, 0f, 360f, false, corner, box, style = stroke)
            if (progress > 0f) drawArc(
                ringColour,
                -90f,
                360f * progress,
                false,
                corner,
                box,
                style = stroke
            )
        }
    }

    @Composable
    override fun Board(
        state: ClockState,
        play: (ClockState) -> Unit,
        feedback: Feedback,
        modifier: Modifier
    ) {
        val over = outcome(state) != null

        // After a tap the turned-up card travels under its pile, and only then is
        // that pile's top card turned up: the same wait even when it has nowhere to go.
        val first = remember { state }
        val undoing = LocalUndoing.current
        var landed by remember(state) { mutableStateOf(state === first || state.last == null || undoing) }
        LaunchedEffect(state) {
            if (!landed) {
                delay(CARD_MOVE_MS.toLong())
                landed = true
            }
        }

        // A new game turns up its first card after a short wait.
        LaunchedEffect(state) {
            if (state === first && state.revealed == 0 && !over) {
                delay(START_DELAY_MS)
                reveal(state)?.let(play)
            }
        }

        CardTable(modifier, underlay = { if (!LocalAmbient.current) Ring(state) }) {
            val places = Places(width)
            val cardWidth = places.cardWidth
            for (pile in 0 until 13) {
                val cards = state.piles[pile]
                val centre = places.centre(pile)
                // In the middle, the Kings that have come up lie under the pile, each showing at its left.
                val kings =
                    if (pile == 0) cards.takeWhile { it.faceUp && it.card.rank == Rank.King }.size else 0
                val step = cardWidth * KING_SHOWN
                val x = centre.x - cardWidth / 2 + step * kings / 2
                val y = centre.y - places.cardHeight / 2
                cards.forEachIndexed { i, c ->
                    val top = i == cards.lastIndex
                    val current = top && pile == state.last && c.faceUp
                    card(
                        key = c.card,
                        card = c.card,
                        x = if (i < kings) x - step * (kings - i) else x,
                        y = y,
                        width = cardWidth,
                        // The card just turned up waits, face down, for the last one to land.
                        faceDown = !c.faceUp || current && !landed,
                        // A card sent to its hour passes over every pile but that one, whose cards
                        // lie higher still: it goes under them, to the bottom.
                        z = (if (pile == state.last && i > 0) TARGET_Z else 1f) + i * 0.01f,
                        selection = if (current && landed && !over) Selection.Neutral else null,
                        rotation = places.rotation(pile),
                    )
                }
                // The card turned up goes to the pile tapped, if it's the right one. The first tap, anywhere, starts.
                val reach = (cardWidth + places.cardHeight) / 2
                tap(centre.x - reach / 2, centre.y - reach / 2, reach, reach) {
                    feedback.tryMove(
                        if (landed && (state.last == null || pile == state.next)) reveal(
                            state
                        ) else null, play
                    )
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun ClockPreview() = GamePreview(Clock)

