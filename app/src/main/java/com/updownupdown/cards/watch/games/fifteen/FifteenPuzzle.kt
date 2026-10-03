package com.updownupdown.cards.watch.games.fifteen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** The cells row by row from the top left; null is the gap. */
@Serializable
data class FifteenState(val cells: List<Card?>, val moves: Int = 0)

object FifteenPuzzle : Game<FifteenState> {
    const val SIZE = 4
    private const val SHUFFLE_MOVES = 160

    private val SUIT_ROWS = listOf(Suit.Hearts, Suit.Clubs, Suit.Diamonds, Suit.Spades)
    private val RANKS = listOf(Rank.Ace, Rank.Two, Rank.Three, Rank.Four)

    val SOLVED: List<Card?> =
        SUIT_ROWS.flatMap { suit -> RANKS.map { Card(it, suit) } }.dropLast(1) + null

    override val id = "fifteen-puzzle"
    override val thumbnail = R.drawable.thumb_fifteen_puzzle
    override val banner = R.drawable.banner_fifteen_puzzle
    override val accent = R.color.game_fifteen_puzzle
    override val accentPale = R.color.game_fifteen_puzzle_pale
    override val title = "Fifteen Puzzle"
    override val stateSerializer: KSerializer<FifteenState> = FifteenState.serializer()

    override val rules = listOf(
        "## Objective",
        "Arrange each row from Ace to 4, by suit:",
        "- **Hearts**",
        "- **Clubs**",
        "- **Diamonds**",
        "- **Spades** (there is no 4 of Spades)",
        "## Gameplay",
        "Tap a card above, below, left or right of the gap to slide it into the gap.",
        "Cards turn green when they are in their place.",
        "Every puzzle is shuffled with legal moves, so it can always be solved.",
    )

    fun neighbours(index: Int): List<Int> {
        val row = index / SIZE
        val col = index % SIZE
        return buildList {
            if (row > 0) add(index - SIZE)
            if (row < SIZE - 1) add(index + SIZE)
            if (col > 0) add(index - 1)
            if (col < SIZE - 1) add(index + 1)
        }
    }

    /** Slides the card at [index] into the gap, or null if it isn't beside the gap. */
    fun slide(state: FifteenState, index: Int): FifteenState? {
        val gap = state.cells.indexOf(null)
        if (index !in neighbours(gap)) return null
        val cells = state.cells.toMutableList()
        cells[gap] = cells[index]
        cells[index] = null
        return FifteenState(cells, state.moves + 1)
    }

    override fun newGame(random: Random): FifteenState {
        while (true) {
            // Random legal moves, never straight back, so it stays solvable.
            var cells = FifteenState(SOLVED)
            var previousGap = -1
            repeat(SHUFFLE_MOVES) {
                val gap = cells.cells.indexOf(null)
                val choices = neighbours(gap).filter { it != previousGap }
                previousGap = gap
                cells = slide(cells, choices.random(random))!!
            }
            if (cells.cells != SOLVED) return FifteenState(cells.cells)
        }
    }

    fun isSolved(state: FifteenState) = state.cells == SOLVED

    override fun outcome(state: FifteenState) =
        if (isSolved(state)) Outcome(won = true, message = "Solved in ${state.moves} moves") else null

    override val scoreName = "Moves"
    override val lowScoreWins = true
    override fun score(state: FifteenState) = state.moves

    // No count under the board: the board takes that room, for larger cards.
    // Its corners just reach the round screen's edge.
    override val boardFraction = 0.76f
    override val boardLift = -0.03f

    @Composable
    override fun Board(state: FifteenState, play: (FifteenState) -> Unit, feedback: Feedback, modifier: Modifier) {
        CardTable(modifier) {
            val layout = CardLayout(SIZE, SIZE, width, height)
            state.cells.forEachIndexed { i, card ->
                val x = layout.xOf(i)
                val y = layout.yOf(i)
                // Each place shows under its card, so it looks the same while cards come and go.
                slot(x, y, layout.cardWidth)
                if (card != null) card(card, card, x, y, layout.cardWidth, overlay = if (card == SOLVED[i]) CardOverlay.Good else null)
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    feedback.tryMove(if (isSolved(state)) null else slide(state, i), play)
                }
            }
        }
    }
}
