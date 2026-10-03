package com.updownupdown.cards.watch.games.ebbandflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardColors
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * @param stock the cards still to draw.
 * @param hand kept in rank order.
 * @param piles two rising piles, then two falling ones, each bottom to top.
 * @param played the cards played since the hand was last filled.
 */
@Serializable
data class EbbAndFlowState(
    val stock: List<Card>,
    val hand: List<Card>,
    val piles: List<List<Card>>,
    val played: Int = 0,
)

/**
 * The Game (Steffen Benndorf) in playing cards: two piles climb, two fall, and
 * at least two cards go down each turn. Plays the phone's Normal hand of seven.
 */
object EbbAndFlow : Game<EbbAndFlowState> {
    const val PILES = 4
    const val HAND = 7
    private const val MIN_PLAYS = 2

    override val id = "ebb-and-flow"
    override val thumbnail = R.drawable.thumb_ebb_and_flow
    override val banner = R.drawable.banner_ebb_and_flow
    override val accent = R.color.game_ebb_and_flow
    override val accentPale = R.color.game_ebb_and_flow_pale
    override val title = "Ebb & Flow"
    override val stateSerializer: KSerializer<EbbAndFlowState> = EbbAndFlowState.serializer()

    override val rules = listOf(
        "## Objective",
        "Play all **52 cards** onto the four piles.",
        "## Gameplay",
        "Play cards onto the rising (first two) or falling (last two) piles, using cards of the same rank or higher/lower.",
        "Any card can start an empty pile. Aces are worth 1.",
        "A card **one rank back and in the same suit** can also be played.",
        "If your hand has 5 cards or less, you can **draw** to fill your hand back up to seven.",
    )

    /** Whether pile [index] climbs. */
    fun rising(index: Int) = index < PILES / 2

    private fun sorted(hand: List<Card>) = hand.sortedWith(compareBy({ it.rank.value }, { it.suit }))

    override fun newGame(random: Random): EbbAndFlowState {
        val deck = shuffledDeck(random)
        return EbbAndFlowState(stock = deck.drop(HAND), hand = sorted(deck.take(HAND)), piles = List(PILES) { emptyList() })
    }

    fun canPlay(card: Card, pile: List<Card>, rising: Boolean): Boolean {
        val top = pile.lastOrNull() ?: return true
        val rise = card.rank.value - top.rank.value
        val step = if (rising) rise else -rise
        // On, or a step back in the same suit.
        return step >= 0 || (step == -1 && card.suit == top.suit)
    }

    fun canPlayOn(state: EbbAndFlowState, card: Card, pile: Int) =
        card in state.hand && canPlay(card, state.piles[pile], rising(pile))

    private fun hasPlay(state: EbbAndFlowState) =
        state.hand.any { card -> (0 until PILES).any { canPlayOn(state, card, it) } }

    /** Once enough cards are down; with the stock empty there's nothing to draw. */
    fun canDraw(state: EbbAndFlowState) = state.stock.isNotEmpty() && state.played >= MIN_PLAYS

    fun play(state: EbbAndFlowState, card: Card, pile: Int): EbbAndFlowState? {
        if (!canPlayOn(state, card, pile)) return null
        return state.copy(
            hand = state.hand - card,
            piles = state.piles.mapIndexed { i, p -> if (i == pile) p + card else p },
            played = state.played + 1,
        )
    }

    /** Fills the hand from the stock. */
    fun draw(state: EbbAndFlowState): EbbAndFlowState? {
        if (!canDraw(state)) return null
        val count = minOf(HAND - state.hand.size, state.stock.size)
        return state.copy(stock = state.stock.drop(count), hand = sorted(state.hand + state.stock.take(count)), played = 0)
    }

    override fun outcome(state: EbbAndFlowState) = when {
        state.stock.isEmpty() && state.hand.isEmpty() -> Outcome(true, "Every card played!")
        state.hand.isNotEmpty() && !hasPlay(state) && !canDraw(state) ->
            Outcome(false, "Stuck with ${state.stock.size + state.hand.size} cards left")
        else -> null
    }

    override fun actions(state: EbbAndFlowState) = listOf(
        GameAction<EbbAndFlowState>("Draw (${state.stock.size})", enabled = canDraw(state)) { draw(it) ?: it },
    )

    /** The gap between the piles and the hand, and between cards in each row, × the card's width. */
    private val ROW_GAP = 5.dp
    private const val PILE_GAP = 0.15f
    private const val HAND_GAP = 0.06f
    /** How far the piles sit up from their place over the hand, × the card's height. */
    private const val PILE_LIFT = 0.16f
    /** The arrow in an empty pile: its height, its head's reach and its line, × the card's width. */
    private const val ARROW_HEIGHT = 0.5f
    private const val ARROW_HEAD = 0.2f
    private const val ARROW_LINE = 0.07f

    /**
     * The piles in a row over the hand, one card size: the largest at which a
     * full hand, side by side, reaches the round screen's edges past the
     * board's and the piles still fit the board's width.
     */
    private class Layout(board: Dp) {
        val cardWidth: Dp
        val cardHeight: Dp get() = cardWidth * CARD_ASPECT
        val pileY: Dp
        val handY: Dp
        private val pileLeft: Dp
        private val middle = board / 2

        init {
            val screen = RoundScreen(board)
            val pilesFit = board / (PILES * (1 + CardStyle.PILE_OFFSET) + (PILES - 1) * PILE_GAP)
            fun top(width: Dp) = (board - width * CARD_ASPECT * 2 - pileEdge(width) - ROW_GAP) / 2
            fun handY(width: Dp) = top(width) + pileEdge(width) + width * CARD_ASPECT + ROW_GAP
            // The hand's room depends on its height, and so on the card's size: a few rounds settle it.
            var width = pilesFit
            repeat(4) {
                val y = handY(width)
                val room = screen.right(y, y + width * CARD_ASPECT) - screen.left(y, y + width * CARD_ASPECT)
                width = min(pilesFit, room / (HAND + (HAND - 1) * HAND_GAP))
            }
            cardWidth = width
            pileY = top(width) - width * CARD_ASPECT * PILE_LIFT
            handY = handY(width)
            pileLeft = middle - (pileEdge(width) * PILES + width * (PILES + (PILES - 1) * PILE_GAP)) / 2
        }

        fun pileX(index: Int) = pileLeft + (cardWidth + pileEdge(cardWidth) + cardWidth * PILE_GAP) * index

        /** Card [index] of [count], the hand centred. */
        fun handX(index: Int, count: Int): Dp {
            val step = cardWidth * (1 + HAND_GAP)
            return middle - (step * (count - 1) + cardWidth) / 2 + step * index
        }
    }

    @Composable
    override fun Board(state: EbbAndFlowState, play: (EbbAndFlowState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Card?>(null) }
        val playing = outcome(state) == null
        val layout = Layout(LocalBoardSize.current)
        val arrowColour = if (LocalAmbient.current) CardColors.Ambient.copy(alpha = 0.3f) else AppColors.SlotMark

        CardTable(
            modifier,
            overlay = {
                // An empty pile shows which way it goes.
                val cardWidth = layout.cardWidth
                state.piles.forEachIndexed { index, pile ->
                    if (pile.isNotEmpty()) return@forEachIndexed
                    Canvas(Modifier.offset(layout.pileX(index), layout.pileY + pileEdge(cardWidth)).size(cardWidth, layout.cardHeight)) {
                        val w = size.width
                        val half = w * ARROW_HEIGHT / 2
                        val head = w * ARROW_HEAD
                        val line = w * ARROW_LINE
                        // Drawn pointing up; turned over for a falling pile.
                        val flip = if (rising(index)) 1f else -1f
                        val tip = Offset(center.x, center.y - half * flip)
                        drawLine(arrowColour, Offset(center.x, center.y + half * flip), tip, line, StrokeCap.Round)
                        drawLine(arrowColour, tip, Offset(tip.x - head, tip.y + head * flip), line, StrokeCap.Round)
                        drawLine(arrowColour, tip, Offset(tip.x + head, tip.y + head * flip), line, StrokeCap.Round)
                    }
                }
            },
        ) {
            val cardWidth = layout.cardWidth
            val picked = selected
            state.piles.forEachIndexed { index, pile ->
                pile(
                    cards = pile,
                    x = layout.pileX(index),
                    y = layout.pileY,
                    cardWidth = cardWidth,
                ) {
                    feedback.tryMove(if (playing && picked != null) play(state, picked, index) else null, play)
                }
            }
            state.hand.forEachIndexed { index, card ->
                val x = layout.handX(index, state.hand.size)
                card(card, card, x, layout.handY, cardWidth, selection = if (card == picked) Selection.Neutral else null)
                tap(x, layout.handY, cardWidth, layout.cardHeight) {
                    if (playing) selected = if (card == picked) null else card else feedback.reject()
                }
            }
        }
    }
}
