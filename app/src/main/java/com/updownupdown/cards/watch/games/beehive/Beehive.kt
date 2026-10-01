package com.updownupdown.cards.watch.games.beehive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import kotlin.random.Random
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
    private const val BEEHIVE_CELL = 6
    private const val WASTE_CELL = 8

    override val id = "beehive"
    override val thumbnail = R.drawable.thumb_beehive
    override val banner = R.drawable.banner_beehive
    override val accent = Color(0xFF5A2A1A)
    override val title = "Beehive"
    override val stateSerializer: KSerializer<BeehiveState> = BeehiveState.serializer()

    override val rules = listOf(
        "## Objective",
        "Clear all 52 cards by gathering sets of four cards of the same rank.",
        "## Board",
        "- The six piles on the left hold one rank each. A set of four is cleared at once.",
        "- Top right is the **beehive**; under it is the **waste**, its last three cards spread out with the top one lowest.",
        "## Playing",
        "- Tap the beehive or the waste to move its top card onto a pile of the same rank, or else into an empty pile.",
        "- Tap a pile to join it to another pile of the same rank.",
        "- Tap **Draw** to turn up three cards from the stock onto the waste. When the stock runs out, **Recycle** turns the waste back over.",
    )

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
        val matching = state.tableau.indexOfFirst { it.isNotEmpty() && it.size < SET && it.last().rank == card.rank }
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
        state.cleared == 52 -> Outcome(true, "The hive is empty!")
        !hasMove(state) && !drawingHelps(state) -> Outcome(false, "No moves left")
        else -> null
    }

    override fun actions(state: BeehiveState) = listOf(
        GameAction<BeehiveState>(
            label = if (state.stock.isEmpty()) "Recycle" else "Draw (${state.stock.size})",
            enabled = state.stock.isNotEmpty() || state.waste.isNotEmpty(),
        ) { draw(it) },
    )

    private const val GAP = 0.12f
    /** The gap between the piles and the column to their right, × a card's width. */
    private const val COLUMN_GAP = 0.6f
    /** How many of the waste's cards show. */
    private const val WASTE_SHOWN = 3
    /** The cards' height, × the board's: short of its foot, to stand clear of the Draw button. */
    private const val BOARD_HEIGHT = 0.9f

    @Composable
    override fun Board(state: BeehiveState, play: (BeehiveState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val over = outcome(state) != null
        CardTable(modifier) {
            val height = height * BOARD_HEIGHT
            // Six piles, two across and three down, on the left. To their right, well apart,
            // the beehive at the top and the waste spread downwards from the middle row.
            val edge = CardStyle.PILE_OFFSET
            val cardWidth = min(
                width / (3 * (1 + edge) + GAP + COLUMN_GAP),
                height / (3 * (CARD_ASPECT + edge) + 2 * GAP),
            )
            val gap = cardWidth * GAP
            val cellWidth = cardWidth * (1 + edge)
            val cellHeight = cardWidth * (CARD_ASPECT + edge)
            val cardHeight = cardWidth * CARD_ASPECT
            val left = (width - cellWidth * 3 - gap - cardWidth * COLUMN_GAP) / 2
            val top = (height - cellHeight * 3 - gap * 2) / 2
            fun rowY(row: Int) = top + (cellHeight + gap) * row
            state.tableau.forEachIndexed { i, pile ->
                pile(pile, left + (cellWidth + gap) * (i % 2), rowY(i / 2), cardWidth) {
                    feedback.tryMove(if (over) null else joinPile(state, i), play)
                }
            }
            val rightX = left + cellWidth * 2 + gap + cardWidth * COLUMN_GAP
            pile(state.beehive, rightX, rowY(0), cardWidth, faceDown = { it != state.beehive.lastIndex }) {
                feedback.tryMove(if (over) null else playBeehive(state), play)
            }
            // The waste's older cards stay under its first place, so cards glide in and out.
            // Its first card is level with the middle row, and its third with the bottom one.
            val wasteY = rowY(1) + pileEdge(cardWidth)
            val step = (cellHeight + gap) / (WASTE_SHOWN - 1)
            val hidden = (state.waste.size - WASTE_SHOWN).coerceAtLeast(0)
            state.waste.forEachIndexed { i, card ->
                val covered = i != state.waste.lastIndex
                card(
                    key = card,
                    card = card,
                    x = rightX,
                    y = wasteY + step * (i - hidden).coerceAtLeast(0),
                    width = cardWidth,
                    z = i * 0.01f,
                    under = covered,
                    // The stock is off to the right.
                    fromRight = true,
                )
            }
            if (state.waste.isEmpty()) slot(rightX, wasteY, cardWidth)
            tap(rightX, wasteY, cardWidth, cardHeight + step * (WASTE_SHOWN - 1)) {
                feedback.tryMove(if (over) null else playWaste(state), play)
            }
        }
    }
}
