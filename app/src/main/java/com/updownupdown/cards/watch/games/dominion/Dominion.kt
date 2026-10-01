package com.updownupdown.cards.watch.games.dominion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Removal
import com.updownupdown.cards.watch.cards.Selection
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
 * @param board 4×3 spaces, row by row; a conquest stacks the conqueror on
 *   the conquered card, so each space is a pile, bottom to top.
 * @param reserve the face-down reserve, drawn from the end.
 * @param drawn the reserve card turned up, waiting for an empty space.
 */
@Serializable
data class DominionState(
    val board: List<List<Card>>,
    val reserve: List<Card>,
    val drawn: Card? = null,
)

/** The phone's 5×3 board with a reserve of 3, shrunk to 4×3 for the watch. */
object Dominion : Game<DominionState> {
    const val COLUMNS = 4
    const val ROWS = 3
    private const val CELLS = COLUMNS * ROWS
    private const val RESERVE = 3

    /** The grid's height, × the board's. */
    private const val GRID_HEIGHT = 0.9f
    /** How far up the drawn card sits from the Draw button, × its height. */
    private const val DRAWN_LIFT = 0.15f

    override val id = "dominion"
    override val thumbnail = R.drawable.thumb_dominion
    override val banner = R.drawable.banner_dominion
    override val accent = Color(0xFF3F3A2A)
    override val title = "Dominion"
    override val stateSerializer: KSerializer<DominionState> = DominionState.serializer()

    override val rules = listOf(
        "## Objective",
        "**Win** by reducing all fifteen cards to a single card on the board.",
        "**Lose** when no conquest is possible and no reserve card can be placed, or when a card has no possible match left.",
        "## Conquering",
        "- Tap a card, then tap a card of the **same rank or suit** next to it (diagonals count) to conquer it.",
        "- A card may also conquer a card further along the same row or column when every space between them is empty.",
        "- The conqueror moves onto the conquered card's space, leaving its own space empty.",
        "## Reserve",
        "- Once a space is empty, tap **Draw** to turn over the next reserve card.",
        "- Tap an empty space to place it before conquering again.",
        "- All three reserve cards must be played to win.",
    )

    override fun newGame(random: Random): DominionState {
        var deal: DominionState
        var attempts = 0
        do {
            val deck = shuffledDeck(random)
            deal = DominionState(deck.take(CELLS).map { listOf(it) }, deck.drop(CELLS).take(RESERVE))
            attempts++
        } while (isolated(deal.board, deal.reserve) && attempts < 50)
        return deal
    }

    private fun matches(a: Card, b: Card) = a.rank == b.rank || a.suit == b.suit

    private fun clearPath(board: List<List<Card>>, from: Int, to: Int): Boolean {
        val (fr, fc) = from / COLUMNS to from % COLUMNS
        val (tr, tc) = to / COLUMNS to to % COLUMNS
        if (kotlin.math.abs(fr - tr) <= 1 && kotlin.math.abs(fc - tc) <= 1) return true
        if (fr == tr) {
            val range = if (fc < tc) fc + 1 until tc else tc + 1 until fc
            return range.all { board[fr * COLUMNS + it].isEmpty() }
        }
        if (fc == tc) {
            val range = if (fr < tr) fr + 1 until tr else tr + 1 until fr
            return range.all { board[it * COLUMNS + fc].isEmpty() }
        }
        return false
    }

    fun canConquer(state: DominionState, from: Int, to: Int): Boolean {
        if (from == to || state.drawn != null) return false
        val a = state.board[from].lastOrNull() ?: return false
        val b = state.board[to].lastOrNull() ?: return false
        return matches(a, b) && clearPath(state.board, from, to)
    }

    fun conquer(state: DominionState, from: Int, to: Int): DominionState? {
        if (!canConquer(state, from, to)) return null
        return state.copy(
            board = state.board.mapIndexed { i, pile ->
                when (i) {
                    from -> emptyList()
                    to -> pile + state.board[from]
                    else -> pile
                }
            },
        )
    }

    fun drawReserve(state: DominionState): DominionState? {
        if (state.drawn != null || state.reserve.isEmpty() || state.board.none { it.isEmpty() }) return null
        return state.copy(reserve = state.reserve.dropLast(1), drawn = state.reserve.last())
    }

    fun place(state: DominionState, cell: Int): DominionState? {
        val card = state.drawn ?: return null
        if (state.board[cell].isNotEmpty()) return null
        return state.copy(board = state.board.mapIndexed { i, p -> if (i == cell) listOf(card) else p }, drawn = null)
    }

    /** Whether some card in play can never be matched. */
    private fun isolated(board: List<List<Card>>, reserve: List<Card>): Boolean {
        val cards = board.mapNotNull { it.lastOrNull() } + reserve
        return cards.any { card -> cards.none { it !== card && matches(it, card) } }
    }

    private fun hasConquest(state: DominionState) =
        (0 until CELLS).any { from -> (0 until CELLS).any { to -> canConquer(state, from, to) } }

    override fun outcome(state: DominionState): Outcome? {
        if (state.drawn != null) return null
        val inPlay = state.board.count { it.isNotEmpty() }
        return when {
            inPlay == 1 && state.reserve.isEmpty() -> Outcome(true, "One card rules them all!")
            state.reserve.isEmpty() && isolated(state.board, emptyList()) -> Outcome(false, "A card can't be conquered")
            !hasConquest(state) && drawReserve(state) == null -> Outcome(false, "No conquests left")
            else -> null
        }
    }

    @Composable
    override fun Bottom(state: DominionState, play: (DominionState) -> Unit, feedback: Feedback, size: DpSize) {
        // A drawn card is shown on the board's layer, over this button.
        BottomButton("Draw (${state.reserve.size})", { feedback.tryMove(drawReserve(state), play) }, drawReserve(state) != null)
    }

    @Composable
    override fun Board(state: DominionState, play: (DominionState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        CardTable(modifier) {
            // Short of the board's foot, to stand clear of the Draw button.
            val layout = CardLayout(COLUMNS, ROWS, width, height * GRID_HEIGHT, gap = 0.12f)
            val from = selected
            state.board.forEachIndexed { cell, pile ->
                val x = layout.xOf(cell)
                val y = layout.yOf(cell)
                if (pile.isEmpty()) slot(x, y, layout.cardWidth)
                // Conquered cards stay hidden under the conqueror, so a conquest
                // is one card gliding onto another.
                pile.forEachIndexed { i, card ->
                    val top = i == pile.lastIndex
                    card(
                        key = card,
                        card = card,
                        x = x,
                        y = y,
                        width = layout.cardWidth,
                        z = i.toFloat(),
                        selection = if (top && from == cell) Selection.Neutral else null,
                        overlay = if (top && from != null && canConquer(state, from, cell)) CardOverlay.Target else null,
                        removal = Removal.Vanish,
                    )
                }
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    val next = when {
                        outcome(state) != null -> null
                        state.drawn != null -> place(state, cell)
                        from != null && from != cell -> conquer(state, from, cell)
                        else -> null
                    }
                    when {
                        next != null -> {
                            feedback.move()
                            play(next)
                        }
                        state.drawn == null && from == cell -> selected = null
                        state.drawn == null && pile.isNotEmpty() -> {
                            feedback.move()
                            selected = cell
                        }
                        else -> feedback.reject()
                    }
                }
            }
            // The drawn reserve card waits below the board, over the Draw button.
            state.drawn?.let { card(it, it, (width - layout.cardWidth) / 2, height + 2.dp - layout.cardHeight * DRAWN_LIFT, layout.cardWidth, z = 50f, selection = Selection.Neutral) }
        }
    }
}
