package com.updownupdown.cards.watch.games.ozanam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * @param grid the square, row by row; null where a place is empty.
 * @param given the places whose card was dealt there, which can't be moved.
 * @param moves how many cards have been moved.
 */
@Serializable
data class OzanamState(val grid: List<Card?>, val given: List<Boolean>, val moves: Int = 0) {
    /** The cards still to place, a pile for each suit, Ace to Jack. */
    val tray: List<List<Card>>
        get() = OzanamsSquare.SUITS.map { suit ->
            OzanamsSquare.RANKS.map { Card(it, suit) }.filter { it !in grid }
        }
}

/** Where a card is picked from: a place on the square, or a suit's pile in the tray. */
sealed interface Source {
    data class Place(val cell: Int) : Source
    data class Tray(val card: Card) : Source
}

/**
 * Ozanam's card puzzle (1725): the sixteen Aces and court cards in a 4×4
 * square, no row, column or long diagonal holding two of a rank or a suit.
 */
object OzanamsSquare : Game<OzanamState> {
    const val SIZE = 4
    const val CELLS = SIZE * SIZE
    val RANKS = listOf(Rank.Ace, Rank.King, Rank.Queen, Rank.Jack)
    val SUITS = listOf(Suit.Spades, Suit.Hearts, Suit.Diamonds, Suit.Clubs)

    /** The phone's Easy deal: this many cards start in place (and the solution is still the only one). */
    private const val GIVENS = 7

    override val id = "ozanams-square"
    override val thumbnail = R.drawable.thumb_ozanams_square
    override val banner = R.drawable.banner_ozanams_square
    override val art = R.drawable.art_ozanams_square
    override val credit = ArtCredit("Melencolia I", "Albrecht Dürer", "1514", "The Metropolitan Museum of Art", "https://commons.wikimedia.org/wiki/File:Melencolia_I_MET_DP820348.jpg")
    override val accent = R.color.game_ozanams_square
    override val accentPale = R.color.game_ozanams_square_pale
    override val title = "Ozanam's Square"
    override val rating = Rating(difficulty = 35, complexity = 15, luck = 5)
    override val stateSerializer: KSerializer<OzanamState> = OzanamState.serializer()

    // Nothing under the board: it takes the room, as Memory's does.
    override val boardFraction = 0.7f
    override val boardLift = -0.02f

    override val scoreName = "Moves"
    override val lowScoreWins = true
    override fun score(state: OzanamState) = state.moves

    override val rules = listOf(
        "## Objective",
        "Fill the square so that each **row, column and long diagonal** holds **one card of each rank and one of each suit**.",
        "## Gameplay",
        "- The cards to place lie in a pile for each suit, at the right. Tap a pile to pick its top card; tap it again for the next card of that suit.",
        "- Then tap an empty space to place it. Cards dealt into place (shown in black) can't be moved.",
        "- Cards you place can be moved to another space, swapped, or sent back by tapping their pile.",
        "- A card that repeats a rank or suit in one of its lines is marked in red.",
        "- Every deal has **exactly one** solution.",
    )

    /** Rows, columns and the two long diagonals. */
    private val LINES: List<List<Int>> =
        List(SIZE) { r -> List(SIZE) { c -> r * SIZE + c } } +
                List(SIZE) { c -> List(SIZE) { r -> r * SIZE + c } } +
                listOf(List(SIZE) { it * SIZE + it }, List(SIZE) { it * SIZE + SIZE - 1 - it })
    private val LINES_OF = List(CELLS) { cell -> LINES.filter { cell in it } }

    private fun clashes(a: Card, b: Card) = a.rank == b.rank || a.suit == b.suit

    /** Whether [card] in [cell] clashes with none of the cards in its lines. */
    fun fits(grid: List<Card?>, cell: Int, card: Card) =
        LINES_OF[cell].all { line ->
            line.all { other ->
                other == cell || grid[other]?.let {
                    !clashes(
                        it,
                        card
                    )
                } ?: true
            }
        }

    /** Up to [limit] ways to fill the empty places, trying cards in [order]. */
    fun solve(grid: List<Card?>, limit: Int, order: List<Card> = ALL): List<List<Card?>> {
        val work = grid.toMutableList()
        val used = grid.filterNotNull().toMutableSet()
        val found = mutableListOf<List<Card?>>()
        fun fill(cell: Int) {
            if (found.size >= limit) return
            if (cell == CELLS) {
                found += work.toList()
                return
            }
            if (work[cell] != null) return fill(cell + 1)
            for (card in order) {
                if (card in used || !fits(work, cell, card)) continue
                work[cell] = card
                used += card
                fill(cell + 1)
                used -= card
                work[cell] = null
                if (found.size >= limit) return
            }
        }
        fill(0)
        return found
    }

    private val ALL = SUITS.flatMap { suit -> RANKS.map { Card(it, suit) } }

    override fun newGame(random: Random): OzanamState {
        val solution = solve(List(CELLS) { null }, 1, ALL.shuffled(random)).single()
        val puzzle = solution.toMutableList<Card?>()
        var givens = CELLS
        for (cell in (0 until CELLS).shuffled(random)) {
            if (givens <= GIVENS) break
            val kept = puzzle[cell]
            puzzle[cell] = null
            if (solve(puzzle, 2).size == 1) givens-- else puzzle[cell] = kept
        }
        return OzanamState(puzzle, puzzle.map { it != null })
    }

    /** The places whose card clashes with another in one of its lines. */
    fun conflicts(grid: List<Card?>): Set<Int> =
        grid.indices.filter { cell -> grid[cell]?.let { !fits(grid, cell, it) } ?: false }.toSet()

    fun solved(grid: List<Card?>) = grid.all { it != null } && conflicts(grid).isEmpty()

    /**
     * Moves the card at [source] to [cell]: a card already there goes back to
     * the tray, or swaps places with one moved from another place. Null when
     * it can't go.
     */
    fun place(state: OzanamState, source: Source, cell: Int): OzanamState? {
        if (solved(state.grid) || state.given.getOrNull(cell) != false) return null
        val grid = state.grid.toMutableList()
        when (source) {
            is Source.Place -> {
                if (source.cell == cell || state.given[source.cell]) return null
                val card = grid[source.cell] ?: return null
                grid[source.cell] = grid[cell]
                grid[cell] = card
            }

            is Source.Tray -> {
                if (source.card in grid) return null
                grid[cell] = source.card
            }
        }
        return state.copy(grid = grid, moves = state.moves + 1)
    }

    /** Sends the card at [cell] back to the tray. */
    fun unplace(state: OzanamState, cell: Int): OzanamState? {
        if (solved(state.grid) || state.given.getOrNull(cell) != false || state.grid[cell] == null) return null
        return state.copy(
            grid = state.grid.mapIndexed { i, card -> if (i == cell) null else card },
            moves = state.moves + 1
        )
    }

    override fun outcome(state: OzanamState) =
        if (solved(state.grid)) Outcome(true, "The square is complete!") else null

    /** The gap between the square and the tray, × a card's width, on top of the usual gap. */
    private const val TRAY_GAP = 0.25f
    private const val GAP = 0.1f

    @Composable
    override fun Board(
        state: OzanamState,
        play: (OzanamState) -> Unit,
        feedback: Feedback,
        modifier: Modifier
    ) {
        var picked by remember(state) { mutableStateOf<Source?>(null) }
        // Which card of each suit's pile is on top, counting from its Ace.
        var shown by remember { mutableStateOf(List(SIZE) { 0 }) }
        val playing = outcome(state) == null
        val conflicts = conflicts(state.grid)
        CardTable(modifier) {
            // Four columns of the square and the tray's, each card with its pile's edge, and the gaps.
            val extra = CardStyle.PILE_OFFSET
            val cardWidth = min(
                width / (SIZE * 1 + (1 + extra) + (SIZE - 1) * GAP + GAP + TRAY_GAP),
                height / (SIZE * (CARD_ASPECT + extra) + (SIZE - 1) * GAP),
            )
            val cardHeight = cardWidth * CARD_ASPECT
            val gap = cardWidth * GAP
            val rowStep = cardHeight + pileEdge(cardWidth) + gap
            val totalWidth =
                cardWidth * SIZE + gap * (SIZE - 1) + cardWidth * (TRAY_GAP + GAP) + cardWidth * (1 + extra)
            val left = (width - totalWidth) / 2
            val top = (height - rowStep * SIZE + gap) / 2
            val edge = pileEdge(cardWidth)
            fun cellX(c: Int) = left + (cardWidth + gap) * c
            fun cellY(r: Int) = top + edge + rowStep * r
            val trayX = cellX(SIZE) + cardWidth * TRAY_GAP

            state.grid.forEachIndexed { cell, card ->
                val x = cellX(cell % SIZE)
                val y = cellY(cell / SIZE)
                slot(x, y, cardWidth)
                if (card != null) {
                    card(
                        key = card,
                        card = card,
                        x = x,
                        y = y,
                        width = cardWidth,
                        selection = if (picked == Source.Place(cell)) Selection.Neutral else null,
                        overlay = if (!state.given[cell] && cell in conflicts) CardOverlay.Bad else null,
                        // Cards dealt into place, which never move, lie on black.
                        face = if (state.given[cell]) Color.Black else null,
                    )
                }
                tap(x, y, cardWidth, cardHeight) {
                    val from = picked
                    val next = if (playing && from != null) place(state, from, cell) else null
                    when {
                        next != null -> feedback.tryMove(next, play)
                        !playing || state.given[cell] -> feedback.reject()
                        card == null -> if (from != null) feedback.reject()
                        else -> picked =
                            if (from == Source.Place(cell)) null else Source.Place(cell)
                    }
                }
            }

            // The tray: a pile for each suit, one over the other, the card picked from it on top.
            state.tray.forEachIndexed { suit, pile ->
                val y = cellY(suit) - edge
                // Its place, which shows once the suit's cards are all on the square.
                slot(trayX, y + edge, cardWidth)
                val top = pile.getOrNull(shown[suit] % pile.size.coerceAtLeast(1))
                val ordered = if (top == null) pile else pile - top + top
                ordered.forEachIndexed { i, card ->
                    val isTop = i == ordered.lastIndex
                    val lift = if (isTop && ordered.size > 1) edge else 0.dp
                    card(
                        key = card,
                        card = card,
                        x = trayX + lift,
                        y = y + edge - lift,
                        width = cardWidth,
                        z = if (isTop) 1f else i * 0.01f,
                        under = !isTop,
                        selection = if (isTop && picked == Source.Tray(card)) Selection.Neutral else null,
                    )
                }
                tap(trayX, y, cardWidth + edge, cardHeight + edge) {
                    val from = picked
                    when {
                        !playing -> feedback.reject()
                        // A card picked from the square goes back to its pile.
                        from is Source.Place -> feedback.tryMove(unplace(state, from.cell), play)
                        top == null -> feedback.reject()
                        // Tapped again: the next card of the suit comes up, still picked.
                        from == Source.Tray(top) -> {
                            if (ordered.size > 1) {
                                val next = (shown[suit] + 1) % pile.size
                                shown = shown.toMutableList().also { it[suit] = next }
                                picked = Source.Tray(pile[next])
                            }
                        }

                        else -> picked = Source.Tray(top)
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun OzanamsSquarePreview() = GamePreview(OzanamsSquare)
