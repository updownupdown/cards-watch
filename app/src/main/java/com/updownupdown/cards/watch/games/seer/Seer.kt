package com.updownupdown.cards.watch.games.seer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.Outfit
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** A card on a prediction space: face up for a success, face down for a stone. */
@Serializable
data class SeerCard(val card: Card, val faceUp: Boolean)

/**
 * @param stock drawn from the end; returned stones go under it (the front).
 * @param table cards turned up and out of play; the last is shown.
 * @param board the eight prediction spaces, each a pile bottom to top.
 * @param freed spaces whose top stone was just freed, waiting for the player
 *   to reveal them or return them to the stock.
 */
@Serializable
data class SeerState(
    val stock: List<Card>,
    val table: List<Card> = emptyList(),
    val board: List<List<SeerCard>> = List(SPACES) { emptyList() },
    val freed: List<Int> = emptyList(),
)

private const val COLUMNS = 4
private const val SPACES = 8

/** The phone game's Normal rules: a success frees stones beside it, not diagonally. */
object Seer : Game<SeerState> {
    override val id = "seer"
    override val thumbnail = R.drawable.thumb_seer
    override val banner = R.drawable.banner_seer
    override val accent = Color(0xFF2A4038)
    override val title = "Seer"
    override val stateSerializer: KSerializer<SeerState> = SeerState.serializer()

    override val rules = listOf(
        "## Objective",
        "Complete all eight predictions before the 24-card stock runs out or every space is blocked.",
        "## Predictions",
        "- Tap a space to turn up the next card and see if it matches: **Red**, **Black**, **Even**, **Odd**, ♦♥ or ♠♣.",
        "- Two spaces mix colour and number: red **1 3 5** over black **2 4 6** means a red odd card or a black even card; the other is the reverse.",
        "- On a match, the card goes on the space (green: complete).",
        "- On a miss, the card is discarded and the next card goes on the space face down as a **stone**. Three stones block a space.",
        "## Freeing stones",
        "- A match frees the top stone of each space directly above, below, left and right of it.",
        "- The freed stones turn yellow. Then choose: **Reveal** discards the freed stones face up; **Return** puts them face down under the stock.",
        "- Stones under a completed card are trapped.",
        "## Replaying",
        "- A completed space can be played again to free more stones.",
        "- A miss there adds a stone, and the space isn't complete until that stone is freed or it succeeds again.",
    )

    val DECK: List<Card> = fullDeck().filter { it.rank.value <= 6 }

    override fun newGame(random: Random) = SeerState(stock = DECK.shuffled(random))

    fun matches(card: Card, space: Int): Boolean {
        val red = card.isRed
        val even = card.rank.value % 2 == 0
        return when (space) {
            0 -> red
            1 -> red && !even || !red && even
            2 -> even
            3 -> card.suit == Suit.Diamonds || card.suit == Suit.Hearts
            4 -> card.suit == Suit.Spades || card.suit == Suit.Clubs
            5 -> !even
            6 -> !red && !even || red && even
            else -> !red
        }
    }

    private fun stones(pile: List<SeerCard>) = pile.count { !it.faceUp }
    fun isComplete(pile: List<SeerCard>) = pile.lastOrNull()?.faceUp == true
    fun isBlocked(pile: List<SeerCard>) = stones(pile) >= 3

    private fun neighbours(space: Int): List<Int> {
        val row = space / COLUMNS
        val col = space % COLUMNS
        return (0 until SPACES).filter { other ->
            kotlin.math.abs(other / COLUMNS - row) + kotlin.math.abs(other % COLUMNS - col) == 1
        }
    }

    fun predict(state: SeerState, space: Int): SeerState? {
        if (state.freed.isNotEmpty() || outcome(state) != null || isBlocked(state.board[space])) return null
        val card = state.stock.lastOrNull() ?: return null
        var stock = state.stock.dropLast(1)
        val board = state.board.toMutableList()
        if (matches(card, space)) {
            board[space] = board[space] + SeerCard(card, faceUp = true)
            val freed = neighbours(space).filter { board[it].lastOrNull()?.faceUp == false }
            return state.copy(stock = stock, board = board, freed = freed)
        }
        val stone = stock.lastOrNull()
        if (stone != null) {
            stock = stock.dropLast(1)
            board[space] = board[space] + SeerCard(stone, faceUp = false)
        }
        return state.copy(stock = stock, table = state.table + card, board = board)
    }

    /** Takes the freed stones off: onto the table face up, or under the stock. */
    fun dispose(state: SeerState, toStock: Boolean): SeerState {
        val stones = state.freed.map { state.board[it].last().card }
        val board = state.board.mapIndexed { i, pile -> if (i in state.freed) pile.dropLast(1) else pile }
        return if (toStock) {
            state.copy(stock = stones + state.stock, board = board, freed = emptyList())
        } else {
            state.copy(table = state.table + stones, board = board, freed = emptyList())
        }
    }

    override fun outcome(state: SeerState) = when {
        state.freed.isNotEmpty() -> null
        state.board.all { isComplete(it) } -> Outcome(true, "All eight foreseen!")
        state.stock.isEmpty() -> Outcome(false, "The stock ran out")
        state.board.all { isBlocked(it) } -> Outcome(false, "Every space is blocked")
        else -> null
    }

    override fun actions(state: SeerState): List<GameAction<SeerState>> =
        if (state.freed.isEmpty()) {
            emptyList()
        } else {
            listOf(
                GameAction("Reveal") { dispose(it, toStock = false) },
                GameAction("Return") { dispose(it, toStock = true) },
            )
        }

    /** The spaces' cards: four across, under a label strip, the same size as the turned-up card. */
    private fun layout(board: Dp) = CardLayout(COLUMNS, 2, board, board - LABEL * 2, gap = 0.12f, extra = CardStyle.PILE_OFFSET)

    private val LABEL = 18.dp

    @Composable
    override fun Bottom(state: SeerState, play: (SeerState) -> Unit, feedback: Feedback, size: DpSize) {
        // The last card turned up sits in the middle (drawn with the board);
        // the choice for freed stones goes either side of it, or else the stock count.
        val cardWidth = layout(LocalBoardSize.current).cardWidth
        val side = (size.width - cardWidth) / 2 - 4.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.freed.isNotEmpty()) {
                GameButton("Reveal", { feedback.tryMove(dispose(state, toStock = false), play) }, side, size.height)
                Spacer(Modifier.width(cardWidth + 8.dp))
                GameButton("Return", { feedback.tryMove(dispose(state, toStock = true), play) }, side, size.height)
            } else {
                Spacer(Modifier.width(side + cardWidth + 8.dp))
                BasicText(
                    text = "Stock:\n${state.stock.size}",
                    style = TextStyle(color = AppColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp, lineHeight = 12.sp),
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.width(side),
                )
            }
        }
    }

    private val redText = AppColors.LabelRed
    private val blackText = AppColors.LabelBlack
    private val plainText = AppColors.LabelPlain

    private fun label(space: Int): AnnotatedString = buildAnnotatedString {
        fun part(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text) }
        when (space) {
            0 -> part("Red", redText)
            1 -> { part("135", redText); append("\n"); part("246", blackText) }
            2 -> part("Even", plainText)
            3 -> part("♦♥", redText)
            4 -> part("♠♣", blackText)
            5 -> part("Odd", plainText)
            6 -> { part("135", blackText); append("\n"); part("246", redText) }
            else -> part("Black", blackText)
        }
    }

    @Composable
    override fun Board(state: SeerState, play: (SeerState) -> Unit, feedback: Feedback, modifier: Modifier) {
        BoxWithConstraints(modifier) {
            val layout = layout(maxWidth)
            val rowHeight = LABEL + layout.cellHeight
            CardTable(
                Modifier.fillMaxSize(),
                overlay = {
                    for (space in 0 until SPACES) {
                        BasicText(
                            text = label(space),
                            style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 9.sp),
                            maxLines = 2,
                            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 11.sp),
                            modifier = Modifier
                                .offset(layout.xOf(space), rowHeight * (space / COLUMNS) + layout.gap * (space / COLUMNS))
                                .size(layout.cellWidth, LABEL),
                        )
                    }
                },
            ) {
                for (space in 0 until SPACES) {
                    val pile = state.board[space]
                    val x = layout.xOf(space)
                    val y = LABEL + (rowHeight + layout.gap) * (space / COLUMNS)
                    pile(
                        cards = pile.map { it.card },
                        x = x,
                        y = y,
                        cardWidth = layout.cardWidth,
                        faceDown = { !pile[it].faceUp },
                        overlay = when {
                            space in state.freed -> CardOverlay.Hint
                            isComplete(pile) -> CardOverlay.Good
                            isBlocked(pile) -> CardOverlay.Bad
                            else -> null
                        },
                    ) {
                        val next = predict(state, space)
                        when {
                            next == null -> feedback.reject()
                            next.table.size > state.table.size -> feedback.reject().also { play(next) }
                            else -> feedback.move().also { play(next) }
                        }
                    }
                }
                // The last card turned up, in the middle of the crescent below.
                state.table.lastOrNull()?.let {
                    card(it, it, (width - layout.cardWidth) / 2, height + 2.dp, layout.cardWidth, z = 50f)
                }
            }
        }
    }
}
