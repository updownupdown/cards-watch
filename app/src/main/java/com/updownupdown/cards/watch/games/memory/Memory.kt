package com.updownupdown.cards.watch.games.memory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardTable
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
 * @param open the face-up cards (at most two). An open pair stays up, green
 *   if it matches and red if not, until the next tap.
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
    override val saveVersion = 2

    override val rules = listOf(
        "## Objective",
        "Clear the board by finding all matching pairs.",
        "## Matching",
        "- Tap a card to turn it over, then tap a second card.",
        "- A pair must match in both rank and suit.",
        "- A matching pair turns green and leaves on your next tap; a pair that doesn't match turns red and turns back over.",
    )

    override fun newGame(random: Random): MemoryState {
        val pairs = fullDeck().shuffled(random).take(COLUMNS * ROWS / 2)
        val cells = pairs.flatMap { listOf(MemoryCard(it, 0), MemoryCard(it, 1)) }.shuffled(random)
        return MemoryState(cells)
    }

    private fun isPair(state: MemoryState) =
        state.open.size == 2 && state.cells[state.open[0]]?.card == state.cells[state.open[1]]?.card

    /** Clears the open pair: a match leaves the board, a miss turns back over. */
    private fun settle(state: MemoryState): MemoryState = when {
        state.open.size < 2 -> state
        isPair(state) -> state.copy(cells = state.cells.mapIndexed { i, c -> if (i in state.open) null else c }, open = emptyList())
        else -> state.copy(open = emptyList())
    }

    /** Turns over the card at [index], or null if that does nothing. */
    fun tap(state: MemoryState, index: Int): MemoryState? {
        val settled = settle(state)
        if (settled.cells[index] == null || index in settled.open) {
            return if (settled != state) settled else null
        }
        val open = settled.open + index
        return settled.copy(open = open, moves = settled.moves + if (open.size == 2) 1 else 0)
    }

    override fun outcome(state: MemoryState): Outcome? {
        val left = state.cells.count { it != null }
        val done = left == 0 || left == 2 && isPair(state)
        return if (done) Outcome(true, "Cleared in ${state.moves} moves") else null
    }

    override fun status(state: MemoryState) = if (state.moves == 1) "1 move" else "${state.moves} moves"

    @Composable
    override fun Board(state: MemoryState, play: (MemoryState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val pair = isPair(state)
        CardTable(modifier) {
            val layout = CardLayout(COLUMNS, ROWS, width, height)
            state.cells.forEachIndexed { i, cell ->
                val x = layout.xOf(i)
                val y = layout.yOf(i)
                if (cell != null) {
                    val open = i in state.open
                    card(
                        key = cell,
                        card = cell.card,
                        x = x,
                        y = y,
                        width = layout.cardWidth,
                        faceDown = !open,
                        overlay = when {
                            !open || state.open.size < 2 -> null
                            pair -> CardOverlay.Good
                            else -> CardOverlay.Bad
                        },
                    )
                }
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    val next = if (outcome(state) == null) tap(state, i) else null
                    when {
                        next == null -> feedback.reject()
                        next.open.size == 2 && !isPair(next) -> feedback.reject().also { play(next) }
                        else -> feedback.move().also { play(next) }
                    }
                }
            }
        }
    }
}
