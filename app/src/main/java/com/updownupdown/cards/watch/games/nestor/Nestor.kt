package com.updownupdown.cards.watch.games.nestor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.math.hypot
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * @param columns each listed from the top of the screen down; only the last card is free.
 * @param reserve two free cards, each keeping its place (null once paired off).
 */
@Serializable
data class NestorState(val columns: List<List<Card>>, val reserve: List<Card?>)

/**
 * Nestor, cut down to fit: Aces to 8s in six columns of five, with a reserve
 * of two. Pair off free cards of the same rank. Every deal can be won.
 */
object Nestor : Game<NestorState> {
    const val COLUMNS = 6
    private const val ROWS = 5
    private const val RESERVE = 2

    /** Where a free card can be: a column (0 until [COLUMNS]), then the reserve's two places. */
    const val PLACES = COLUMNS + RESERVE

    private val RANKS = Rank.entries.take(8)

    override val id = "nestor"
    override val thumbnail = R.drawable.thumb_nestor
    override val banner = R.drawable.banner_nestor
    override val art = R.drawable.art_nestor
    override val credit = ArtCredit("The Triumph of Time over Fame", "South Netherlandish", "ca. 1500–1530", "The Metropolitan Museum of Art", "https://www.metmuseum.org/art/collection/search/467828")
    override val accent = R.color.game_nestor
    override val accentPale = R.color.game_nestor_pale
    override val title = "Nestor"
    override val rating = Rating(difficulty = 70, complexity = 15, luck = 55)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<NestorState> = NestorState.serializer()

    // Nothing under the board: it reaches the screen's foot, for larger cards.
    override val boardFraction = 0.8f
    override val boardLift = -0.09f

    override val rules = listOf(
        "## Objective",
        "**Win** by pairing off all 32 cards.",
        "## Gameplay",
        "Tap two free cards of the **same rank** to discard them.",
        "Only the top card of a column, or the two reserve cards, can be used.",
        "Only **Aces to 8s** are used.",
    )

    override fun newGame(random: Random): NestorState {
        val deck = RANKS.flatMap { rank -> Suit.entries.map { Card(rank, it) } }
        while (true) {
            val cards = deck.shuffled(random)
            val state = NestorState(cards.take(COLUMNS * ROWS).chunked(ROWS), cards.drop(COLUMNS * ROWS))
            // No rank twice in a column, where one would bury the other, and a deal that can be won.
            if (state.columns.all { column -> column.distinctBy { it.rank }.size == ROWS } && winnable(state)) return state
        }
    }

    /** The free card at [place], if there is one. */
    fun free(state: NestorState, place: Int): Card? =
        if (place < COLUMNS) state.columns[place].lastOrNull() else state.reserve[place - COLUMNS]

    /** Discards the free cards at [a] and [b], when they are of one rank. */
    fun pair(state: NestorState, a: Int, b: Int): NestorState? {
        if (a == b) return null
        val first = free(state, a) ?: return null
        val second = free(state, b) ?: return null
        if (first.rank != second.rank) return null
        return NestorState(
            columns = state.columns.mapIndexed { i, column -> if (i == a || i == b) column.dropLast(1) else column },
            reserve = state.reserve.mapIndexed { i, card -> if (i + COLUMNS == a || i + COLUMNS == b) null else card },
        )
    }

    private fun moves(state: NestorState): List<NestorState> =
        (0 until PLACES).flatMap { a -> (a + 1 until PLACES).mapNotNull { b -> pair(state, a, b) } }

    private fun cleared(state: NestorState) = state.columns.all { it.isEmpty() } && state.reserve.all { it == null }

    /** Whether every card can still be paired off. [dead] holds the positions already found wanting. */
    fun winnable(state: NestorState, dead: MutableSet<Int> = HashSet()): Boolean {
        if (cleared(state)) return true
        // Within a deal, what's left of each column and of the reserve says which cards remain.
        val columns = state.columns.fold(0) { key, column -> key * (ROWS + 1) + column.size }
        val key = state.reserve.fold(columns) { key, card -> key * 2 + if (card != null) 1 else 0 }
        if (!dead.add(key)) return false
        return moves(state).any { winnable(it, dead) }
    }

    override fun outcome(state: NestorState) = when {
        cleared(state) -> Outcome(true, "All paired off!")
        moves(state).isEmpty() -> Outcome(false, "No pairs left")
        else -> null
    }

    /** The gap between columns, and between them and the reserve, × the card's width. */
    private const val GAP = 0.1f
    private const val RESERVE_GAP = 0.2f
    /** How far down each card sits from the one above, × a card's height: enough to show its rank. */
    private const val SPREAD = 0.56f
    /** The least gap between a card and the screen's edge. */
    private val MARGIN = 2.dp

    @Composable
    override fun Board(state: NestorState, play: (NestorState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        val playing = outcome(state) == null

        fun tapPlace(place: Int) {
            val picked = selected
            when {
                !playing || free(state, place) == null -> feedback.reject()
                picked == place -> selected = null
                picked != null -> pair(state, picked, place)?.let { feedback.tryMove(it, play) } ?: run { selected = place }
                else -> selected = place
            }
        }

        CardTable(modifier) {
            // The columns across the screen's middle with the reserve under them, as large as the
            // round screen holds: sizes here are × the card's width until that is known.
            val wide = COLUMNS + (COLUMNS - 1) * GAP
            val column = CARD_ASPECT * (1 + (ROWS - 1) * SPREAD)
            val reserveWide = RESERVE + (RESERVE - 1) * GAP
            val high = column + RESERVE_GAP + CARD_ASPECT
            // How far the columns' top is above the screen's middle, for their top corners and the
            // reserve's bottom ones to be equally far from it.
            val above = (high * high + reserveWide * reserveWide / 4 - wide * wide / 4) / (2 * high)
            val diameter = width / boardFraction
            val cardWidth = (diameter / 2 - MARGIN) / hypot(wide / 2, above)
            val cardHeight = cardWidth * CARD_ASPECT
            val step = cardWidth * (1 + GAP)
            val fall = cardHeight * SPREAD
            val middle = height / 2 + diameter * boardLift
            val top = middle - cardWidth * above

            val picked = selected
            // The picked card; the cards that pair with it are left to be found.
            fun selection(place: Int) = if (place == picked) Selection.Neutral else null

            state.columns.forEachIndexed { index, cards ->
                val x = width / 2 - cardWidth * (wide / 2) + step * index
                cards.forEachIndexed { row, card ->
                    val isFree = row == cards.lastIndex
                    card(
                        key = card,
                        card = card,
                        x = x,
                        y = top + fall * row,
                        width = cardWidth,
                        z = row.toFloat(),
                        selection = if (isFree) selection(index) else null,
                    )
                }
                if (cards.isNotEmpty()) tap(x, top, cardWidth, fall * cards.lastIndex + cardHeight) { tapPlace(index) }
            }
            val reserveY = top + cardWidth * (column + RESERVE_GAP)
            state.reserve.forEachIndexed { index, card ->
                if (card == null) return@forEachIndexed
                val place = COLUMNS + index
                val x = width / 2 - cardWidth * (reserveWide / 2) + step * index
                card(card, card, x, reserveY, cardWidth, selection = selection(place))
                tap(x, reserveY, cardWidth, cardHeight) { tapPlace(place) }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun NestorPreview() = GamePreview(Nestor)

