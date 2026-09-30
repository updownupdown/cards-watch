package com.updownupdown.cards.watch.games.fifteen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardGrid
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.PlayingCard
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

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
        "- Tap a card above, below, left or right of the gap to slide it into the gap.",
        "- Cards turn green when they are in their place.",
        "- Every puzzle is shuffled with legal moves, so it can always be solved.",
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

    override fun status(state: FifteenState) =
        if (state.moves == 1) "1 move" else "${state.moves} moves"

    @Composable
    override fun Board(
        state: FifteenState,
        play: (FifteenState) -> Unit,
        feedback: Feedback,
        modifier: Modifier,
    ) {
        CardGrid(
            columns = SIZE,
            rows = SIZE,
            modifier = modifier,
            onTap = { index ->
                val next = if (isSolved(state)) null else slide(state, index)
                if (next != null) {
                    feedback.move()
                    play(next)
                } else {
                    feedback.reject()
                }
            },
        ) { index ->
            val card = state.cells[index]
            PlayingCard(
                card = card,
                overlay = if (card != null && card == SOLVED[index]) CardOverlay.Good else null,
            )
        }
    }
}
