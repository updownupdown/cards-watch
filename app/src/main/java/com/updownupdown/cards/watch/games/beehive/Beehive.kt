package com.updownupdown.cards.watch.games.beehive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.text
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Piles list bottom to top. Only the beehive's top card is face up. The
 * stock deals to the waste three at a time; sets of four leave the game.
 */
@Serializable
data class BeehiveState(
    val beehive: List<Card>,
    val tableau: List<List<Card>>,
    val stock: List<Card>,
    val waste: List<Card> = emptyList(),
    val cleared: Int = 0,
)

object Beehive : Game<BeehiveState> {
    private const val TABLEAU = 6
    private const val BEEHIVE_SIZE = 10
    private const val SET = 4

    // Board cells on a 3×3 grid.
    override val id = "beehive"
    override val thumbnail = R.drawable.thumb_beehive
    override val banner = R.drawable.banner_beehive
    override val art = R.drawable.art_beehive
    override val credit = ArtCredit("Gathering Honey, Tomb of Rekhmire", "Nina de Garis Davies", "ca. 1479–1425 BCE", "The Metropolitan Museum of Art", "https://www.metmuseum.org/art/collection/search/544626")
    override val accent = R.color.game_beehive
    override val accentPale = R.color.game_beehive_pale
    override val title = R.string.beehive_title
    override val rating = Rating(difficulty = 25, complexity = 20, luck = 50)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<BeehiveState> = BeehiveState.serializer()

    override val rules = R.array.beehive_rules

    override fun newGame(random: Random): BeehiveState {
        val deck = shuffledDeck(random)
        return BeehiveState(
            beehive = deck.take(BEEHIVE_SIZE),
            tableau = deck.drop(BEEHIVE_SIZE).take(TABLEAU).map { listOf(it) },
            stock = deck.drop(BEEHIVE_SIZE + TABLEAU),
        )
    }

    /** Clears any pile holding a full set. */
    private fun settle(state: BeehiveState): BeehiveState {
        val full = state.tableau.count { it.size == SET }
        if (full == 0) return state
        return state.copy(
            tableau = state.tableau.map { if (it.size == SET) emptyList() else it },
            cleared = state.cleared + full * SET,
        )
    }

    private fun destinationFor(state: BeehiveState, card: Card): Int? {
        val matching =
            state.tableau.indexOfFirst { it.isNotEmpty() && it.size < SET && it.last().rank == card.rank }
        if (matching >= 0) return matching
        return state.tableau.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 }
    }

    fun playBeehive(state: BeehiveState): BeehiveState? {
        val card = state.beehive.lastOrNull() ?: return null
        val to = destinationFor(state, card) ?: return null
        return settle(
            state.copy(
                beehive = state.beehive.dropLast(1),
                tableau = state.tableau.mapIndexed { i, p -> if (i == to) p + card else p },
            ),
        )
    }

    fun playWaste(state: BeehiveState): BeehiveState? {
        val card = state.waste.lastOrNull() ?: return null
        val to = destinationFor(state, card) ?: return null
        return settle(
            state.copy(
                waste = state.waste.dropLast(1),
                tableau = state.tableau.mapIndexed { i, p -> if (i == to) p + card else p },
            ),
        )
    }

    fun joinPile(state: BeehiveState, from: Int): BeehiveState? {
        val source = state.tableau[from]
        if (source.isEmpty()) return null
        val to = state.tableau.indices.firstOrNull { i ->
            val pile = state.tableau[i]
            i != from && pile.isNotEmpty() && pile.size + source.size <= SET && pile.last().rank == source.last().rank
        } ?: return null
        return settle(
            state.copy(
                tableau = state.tableau.mapIndexed { i, p ->
                    when (i) {
                        from -> emptyList()
                        to -> p + source
                        else -> p
                    }
                },
            ),
        )
    }

    fun draw(state: BeehiveState): BeehiveState = if (state.stock.isEmpty()) {
        state.copy(stock = state.waste.reversed(), waste = emptyList())
    } else {
        // The stock's top is its last card; each goes face up onto the waste.
        val turned = state.stock.takeLast(3).reversed()
        state.copy(stock = state.stock.dropLast(turned.size), waste = state.waste + turned)
    }

    private fun hasMove(state: BeehiveState) =
        playBeehive(state) != null || playWaste(state) != null ||
                state.tableau.indices.any { joinPile(state, it) != null }

    /** Whether drawing through the stock (recycling once) turns up a playable card. */
    private fun drawingHelps(state: BeehiveState): Boolean {
        var probe = state
        var recycled = false
        while (probe.stock.isNotEmpty() || probe.waste.isNotEmpty()) {
            if (probe.stock.isEmpty()) {
                if (recycled) return false
                recycled = true
            }
            probe = draw(probe)
            if (probe.waste.isNotEmpty() && playWaste(probe) != null) return true
        }
        return false
    }

    override fun outcome(state: BeehiveState) = when {
        state.cleared == 52 -> Outcome(true, text(R.string.beehive_won))
        !hasMove(state) && !drawingHelps(state) -> Outcome(false, text(R.string.outcome_no_moves_left))
        else -> null
    }

    override fun actions(state: BeehiveState) = listOf(
        GameAction<BeehiveState>(
            label = if (state.stock.isEmpty()) text(R.string.beehive_recycle) else text(R.string.action_draw, state.stock.size),
            enabled = state.stock.isNotEmpty() || state.waste.isNotEmpty(),
        ) { draw(it) },
    )

    private const val COLUMNS = 2
    private const val ROWS = 3
    private const val GAP = 0.12f

    /** How many of the waste's cards show. */
    private const val WASTE_SHOWN = 3

    /** How much of each waste card the next one covers, × a card's height. */
    private const val WASTE_OVERLAP = 0.35f

    /** How far the beehive and the waste sit in from the screen's edge, towards the grid, × a card's width. */
    private const val SIDE_PULL = 0.4f

    /** The cards' height, × the board's: short of its foot, to stand clear of the Draw button. */
    private const val BOARD_HEIGHT = 0.9f

    /** How long a recycle waits before it draws. */
    private const val RECYCLE_DRAW_MS = 300L

    /**
     * Where everything sits on a board [board] wide and [high] high: the
     * grid of piles in the middle, as large as the height allows, with the
     * beehive to its left and the waste in a column to its right, each a little
     * in from as far out as the round screen allows, and the same gap from it.
     */
    private class Places(board: Dp, high: Dp) {
        private val grid = CardLayout(
            COLUMNS,
            ROWS,
            board,
            high * BOARD_HEIGHT,
            gap = GAP,
            extra = CardStyle.PILE_OFFSET
        )
        val width = grid.cardWidth
        val height = grid.cardHeight
        private val gridWidth = grid.cellWidth * COLUMNS + grid.gap * (COLUMNS - 1)
        val gridHeight = grid.cellHeight * ROWS + grid.gap * (ROWS - 1)
        val gridTop = grid.y(0)
        private val gridLeft = (board - gridWidth) / 2

        /**
         * The waste's three places, overlapping, centred on the grid. They're
         * fixed, so the first card stays put however many there are.
         */
        private val wasteStep = height * (1 - WASTE_OVERLAP)
        private val wasteTop = gridTop + (gridHeight - height - wasteStep * (WASTE_SHOWN - 1)) / 2
        val wasteY = List(WASTE_SHOWN) { wasteTop + wasteStep * it }

        val beehiveY = gridTop + (gridHeight - grid.cellHeight) / 2
        private val screen = RoundScreen(board)
        private val side = min(
            gridLeft - grid.cellWidth - screen.left(beehiveY, beehiveY + grid.cellHeight),
            screen.right(wasteTop, wasteY.last() + height) - width - (gridLeft + gridWidth),
        ) - width * SIDE_PULL
        val beehiveX = gridLeft - side - grid.cellWidth
        val wasteX = gridLeft + gridWidth + side

        fun pileX(index: Int) = gridLeft + (grid.cellWidth + grid.gap) * (index % COLUMNS)
        fun pileY(index: Int) = grid.yOf(index)
    }

    @Composable
    override fun Board(
        state: BeehiveState,
        play: (BeehiveState) -> Unit,
        feedback: Feedback,
        modifier: Modifier
    ) {
        val over = outcome(state) != null

        // A recycle then draws, after a short wait.
        var before by remember { mutableStateOf(state) }
        LaunchedEffect(state) {
            val recycled =
                before.stock.isEmpty() && before.waste.isNotEmpty() && state.waste.isEmpty() && state.stock.isNotEmpty()
            before = state
            if (recycled && !over) {
                delay(RECYCLE_DRAW_MS)
                play(draw(state))
            }
        }

        CardTable(modifier) {
            val places = Places(width, height)
            val cardWidth = places.width
            state.tableau.forEachIndexed { i, pile ->
                pile(pile, places.pileX(i), places.pileY(i), cardWidth) {
                    feedback.tryMove(if (over) null else joinPile(state, i), play)
                }
            }
            pile(
                state.beehive,
                places.beehiveX,
                places.beehiveY,
                cardWidth,
                faceDown = { it != state.beehive.lastIndex }) {
                feedback.tryMove(if (over) null else playBeehive(state), play)
            }
            // The waste's older cards stay under its first place, so cards glide in and out.
            val hidden = (state.waste.size - WASTE_SHOWN).coerceAtLeast(0)
            state.waste.forEachIndexed { i, card ->
                val place = (i - hidden).coerceAtLeast(0)
                card(
                    key = card,
                    card = card,
                    x = places.wasteX,
                    y = places.wasteY[place],
                    width = cardWidth,
                    z = i * 0.01f,
                    under = i != state.waste.lastIndex,
                    // The stock is off to the right.
                    fromRight = true,
                )
            }
            slot(places.wasteX, places.wasteY[0], cardWidth)
            tap(places.wasteX, places.gridTop, cardWidth, places.gridHeight) {
                feedback.tryMove(if (over) null else playWaste(state), play)
            }
        }
    }
}

@WearGamePreview
@Composable
private fun BeehivePreview() = GamePreview(Beehive)

