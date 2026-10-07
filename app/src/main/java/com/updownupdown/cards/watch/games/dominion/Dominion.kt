package com.updownupdown.cards.watch.games.dominion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Removal
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.text
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * @param board 5×3 spaces, row by row; a conquest stacks the conqueror on
 *   the conquered card, so each space is a pile, bottom to top.
 */
@Serializable
data class DominionState(val board: List<List<Card>>)

/** The phone's 5×3 board, without its reserve, dealt from three suits. */
object Dominion : Game<DominionState> {
    const val COLUMNS = 5
    const val ROWS = 3
    const val CELLS = COLUMNS * ROWS
    /** How many times a deal with a card nothing matches is dealt again. */
    private const val MAX_DEALS = 100

    override val id = "dominion"
    override val thumbnail = R.drawable.thumb_dominion
    override val banner = R.drawable.banner_dominion
    override val art = R.drawable.art_dominion
    override val credit = ArtCredit("Procession of a Daimyo", "Hishikawa Moronobu", "ca. 1681–84", "Art Institute of Chicago", "https://www.artic.edu/artworks/30113")
    override val accent = R.color.game_dominion
    override val accentPale = R.color.game_dominion_pale
    override val title = R.string.dominion_title
    override val rating = Rating(difficulty = 65, complexity = 35, luck = 35)
    override val stateSerializer: KSerializer<DominionState> = DominionState.serializer()
    override val saveVersion = 2

    // Five across is wider than the square board, and there's nothing under
    // it: a larger board, in the middle, whose corners just reach the screen's edge.
    override val boardFraction = 0.76f
    override val boardLift = 0f

    override val rules = R.array.dominion_rules

    /**
     * Fifteen cards from three suits (one, at random, is left out). A deal
     * with a card that nothing matches can't be won, so it's dealt again.
     */
    override fun newGame(random: Random): DominionState {
        var deal: DominionState
        var deals = 0
        do {
            val left = Suit.entries.random(random)
            deal = DominionState(fullDeck().filter { it.suit != left }.shuffled(random).take(CELLS).map { listOf(it) })
            deals++
        } while (isolated(deal.board) && deals < MAX_DEALS)
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
        if (from == to) return false
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

    /** Whether some card in play (of more than one) can never be matched. */
    fun isolated(board: List<List<Card>>): Boolean {
        val cards = board.mapNotNull { it.lastOrNull() }
        return cards.size > 1 && cards.any { card -> cards.none { it !== card && matches(it, card) } }
    }

    private fun hasConquest(state: DominionState) =
        (0 until CELLS).any { from -> (0 until CELLS).any { to -> canConquer(state, from, to) } }

    override fun outcome(state: DominionState): Outcome? = when {
        state.board.count { it.isNotEmpty() } == 1 -> Outcome(true, text(R.string.dominion_won))
        isolated(state.board) -> Outcome(false, text(R.string.dominion_isolated))
        !hasConquest(state) -> Outcome(false, text(R.string.dominion_stuck))
        else -> null
    }

    @Composable
    override fun Board(state: DominionState, play: (DominionState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        CardTable(modifier) {
            val layout = CardLayout(COLUMNS, ROWS, width, height, gap = 0.12f)
            val from = selected
            state.board.forEachIndexed { cell, pile ->
                val x = layout.xOf(cell)
                val y = layout.yOf(cell)
                slot(x, y, layout.cardWidth)
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
                        // No hint of where the picked card can go: that's for the player to see.
                        selection = if (top && from == cell) Selection.Neutral else null,
                        removal = Removal.Vanish,
                    )
                }
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    val next = if (outcome(state) == null && from != null && from != cell) conquer(state, from, cell) else null
                    when {
                        next != null -> {
                            feedback.move()
                            play(next)
                        }
                        from == cell -> selected = null
                        pile.isNotEmpty() && outcome(state) == null -> {
                            feedback.move()
                            selected = cell
                        }
                        else -> feedback.reject()
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun DominionPreview() = GamePreview(Dominion)

