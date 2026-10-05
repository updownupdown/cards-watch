package com.updownupdown.cards.watch.games.irisgarden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.ScrollingCards
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalBoardScroll
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** The garden, row by row, five to a row; null where a card has gone. */
@Serializable
data class IrisGardenState(val grid: List<Card?>)

/**
 * Shisen-Sho with cards, narrowed for the watch: pairs of a rank go out when
 * a straight line through empty places joins them.
 */
object IrisGarden : Game<IrisGardenState> {
    const val COLUMNS = 5
    const val ROWS = 8
    const val CELLS = COLUMNS * ROWS

    /** Aces to 10s: four of each rank, two pairs. */
    private val RANKS = Rank.entries.take(CELLS / 4)

    /** Neighbouring pairs make a deal too easy, so the deal favours others (the phone's weight). */
    private const val NEIGHBOUR_WEIGHT = 0.15

    override val id = "iris-garden"
    override val thumbnail = R.drawable.thumb_iris_garden
    override val banner = R.drawable.banner_iris_garden
    override val accent = R.color.game_iris_garden
    override val accentPale = R.color.game_iris_garden_pale
    override val title = "Iris Garden"
    override val rating = Rating(difficulty = 25, complexity = 15, luck = 30)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<IrisGardenState> = IrisGardenState.serializer()

    // Nothing under the board, which scrolls, as Accordion's does.
    override val boardFraction = 0.72f
    override val boardLift = 0f

    override val rules = listOf(
        "## Objective",
        "Clear all **40 cards** (Ace to 10) from the garden in pairs.",
        "## Gameplay",
        "Two cards of the **same rank** make a pair, whatever their suits.",
        "A pair can be discarded when the two cards are **side by side**, or in the same **row or column** with only empty spaces between them.",
        "Each rank has four cards: which two you pair decides what opens up next.",
        "Every deal can be cleared.",
    )

    private fun cell(x: Int, y: Int) = y * COLUMNS + x

    /** Whether [a] and [b] lie in one row or column with only empty places between them. */
    fun joined(grid: List<Any?>, a: Int, b: Int): Boolean {
        if (a == b) return false
        val (ax, ay) = a % COLUMNS to a / COLUMNS
        val (bx, by) = b % COLUMNS to b / COLUMNS
        return when {
            ax == bx -> (1 until abs(by - ay)).all { grid[cell(ax, ay + it * (by - ay).sign)] == null }
            ay == by -> (1 until abs(bx - ax)).all { grid[cell(ax + it * (bx - ax).sign, ay)] == null }
            else -> false
        }
    }

    fun canPair(grid: List<Card?>, a: Int, b: Int): Boolean {
        val first = grid.getOrNull(a) ?: return false
        val second = grid.getOrNull(b) ?: return false
        return first.rank == second.rank && joined(grid, a, b)
    }

    fun pairs(grid: List<Card?>): List<Pair<Int, Int>> = grid.indices.flatMap { a ->
        (a + 1 until CELLS).filter { b -> canPair(grid, a, b) }.map { a to it }
    }

    fun pair(state: IrisGardenState, a: Int, b: Int): IrisGardenState? {
        if (!canPair(state.grid, a, b)) return null
        return IrisGardenState(state.grid.mapIndexed { i, card -> if (i == a || i == b) null else card })
    }

    /** Places paired off in an order that clears the garden, or null when one ran into a dead end. */
    private fun clearingOrder(random: Random): List<Pair<Int, Int>>? {
        val filled = MutableList<Boolean?>(CELLS) { true }
        val order = mutableListOf<Pair<Int, Int>>()
        while (order.size < CELLS / 2) {
            val cells = filled.indices.filter { filled[it] != null }
            val options = cells.flatMap { a ->
                cells.filter { it > a && joined(filled, a, it) }.map { b ->
                    val distance = abs(a % COLUMNS - b % COLUMNS) + abs(a / COLUMNS - b / COLUMNS)
                    (a to b) to if (distance <= 1) NEIGHBOUR_WEIGHT else 1.0
                }
            }
            if (options.isEmpty()) return null
            var roll = random.nextDouble() * options.sumOf { it.second }
            val pick = options.firstOrNull { roll -= it.second; roll <= 0 }?.first ?: options.last().first
            filled[pick.first] = null
            filled[pick.second] = null
            order += pick
        }
        return order
    }

    /** A garden that can be cleared: each pair of a clearing order shares a rank. */
    override fun newGame(random: Random): IrisGardenState {
        var order = clearingOrder(random)
        while (order == null) order = clearingOrder(random)
        val pairs = RANKS.flatMap { rank ->
            val cards = fullDeck().filter { it.rank == rank }.shuffled(random)
            listOf(cards.take(2), cards.drop(2))
        }.shuffled(random)
        val grid = MutableList<Card?>(CELLS) { null }
        order.forEachIndexed { i, (a, b) ->
            grid[a] = pairs[i][0]
            grid[b] = pairs[i][1]
        }
        return IrisGardenState(grid)
    }

    override fun outcome(state: IrisGardenState) = when {
        state.grid.all { it == null } -> Outcome(true, "The garden is cleared!")
        pairs(state.grid).isEmpty() -> Outcome(false, "No pair left to take")
        else -> null
    }

    @Composable
    override fun Board(state: IrisGardenState, play: (IrisGardenState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var picked by remember(state) { mutableStateOf<Int?>(null) }
        val playing = outcome(state) == null
        val scroll = LocalBoardScroll.current ?: rememberScrollState()
        // A new deal starts from the top.
        LaunchedEffect(state.takeIf { it.grid.none { card -> card == null } }) {
            if (state.grid.none { it == null }) scroll.scrollTo(0)
        }
        val board = LocalBoardSize.current
        // The window: as tall as the round screen has room for at the board's width.
        val diameter = board / boardFraction
        val window = min(board, sqrt(diameter.value * diameter.value - board.value * board.value).dp - 4.dp)
        Box(modifier, contentAlignment = Alignment.Center) {
            ScrollingCards(COLUMNS, ROWS, board, window, scroll = scroll) { places ->
                val from = picked
                state.grid.forEachIndexed { index, card ->
                    if (card == null) return@forEachIndexed
                    val x = places.xOf(index)
                    val y = places.yOf(index)
                    card(card, card, x, y, places.cardWidth, selection = if (from == index) Selection.Neutral else null)
                    tap(x, y, places.cardWidth, places.cardHeight) {
                        val next = if (playing && from != null) pair(state, from, index) else null
                        when {
                            next != null -> feedback.tryMove(next, play)
                            !playing -> feedback.reject()
                            from == index -> picked = null
                            else -> picked = index
                        }
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun IrisGardenPreview() = GamePreview(IrisGarden)
