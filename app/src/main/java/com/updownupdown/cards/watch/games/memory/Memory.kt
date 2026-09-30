package com.updownupdown.cards.watch.games.memory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardGrid
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.PlayingCard
import com.updownupdown.cards.watch.cards.SlideSlot
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One of the two copies of a card on the board. */
@Serializable
data class MemoryCard(val card: Card, val copy: Int)

/**
 * @param cells the board row by row; null once a pair is taken.
 * @param open the face-up cards (at most two); a mismatched pair stays open
 *   until the next tap.
 */
@Serializable
data class MemoryState(
    val cells: List<MemoryCard?>,
    val open: List<Int> = emptyList(),
    val moves: Int = 0,
)

object Memory : Game<MemoryState> {
    const val COLUMNS = 5
    const val ROWS = 4

    override val id = "memory"
    override val title = "Memory"
    override val stateSerializer: KSerializer<MemoryState> = MemoryState.serializer()

    override val rules = listOf(
        "## Objective",
        "Clear the board by finding all matching pairs.",
        "## Matching",
        "- Tap a card to turn it over, then tap a second card.",
        "- A pair must match in both rank and suit. Pairs are taken off the board.",
        "- Cards that don't match stay face up until your next tap.",
    )

    override fun newGame(random: Random): MemoryState {
        val pairs = fullDeck().shuffled(random).take(COLUMNS * ROWS / 2)
        val cells = pairs.flatMap { listOf(MemoryCard(it, 0), MemoryCard(it, 1)) }.shuffled(random)
        return MemoryState(cells)
    }

    /** Turns over the card at [index], or null if that does nothing. */
    fun tap(state: MemoryState, index: Int): MemoryState? {
        val cell = state.cells[index] ?: return null
        // A mismatched pair turns back over on the next tap.
        val open = if (state.open.size == 2) emptyList() else state.open
        if (index in open) return null
        if (open.isEmpty()) return state.copy(open = listOf(index))

        val first = open.single()
        val moves = state.moves + 1
        return if (state.cells[first]!!.card == cell.card) {
            val cells = state.cells.toMutableList().apply {
                this[first] = null
                this[index] = null
            }
            MemoryState(cells, emptyList(), moves)
        } else {
            state.copy(open = listOf(first, index), moves = moves)
        }
    }

    override fun outcome(state: MemoryState) =
        if (state.cells.all { it == null }) Outcome(true, "Cleared in ${state.moves} moves") else null

    override fun status(state: MemoryState) = if (state.moves == 1) "1 move" else "${state.moves} moves"

    @Composable
    override fun Board(state: MemoryState, play: (MemoryState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val mismatched = state.open.size == 2
        CardGrid(
            columns = COLUMNS,
            rows = ROWS,
            modifier = modifier,
            onTap = { index ->
                val next = tap(state, index)
                if (next == null) {
                    feedback.reject()
                } else {
                    if (next.open.size == 2) feedback.reject() else feedback.move()
                    play(next)
                }
            },
        ) { index ->
            // A matched pair leaves face up, after a moment to see it.
            SlideSlot(state.cells[index], key = { it }, exitDelayMs = 450) { cell ->
                val gone = cell != null && state.cells[index] != cell
                PlayingCard(
                    card = cell?.card,
                    faceDown = cell != null && !gone && index !in state.open,
                    overlay = when {
                        gone -> CardOverlay.Good
                        mismatched && index in state.open -> CardOverlay.Bad
                        else -> null
                    },
                )
            }
        }
    }
}
