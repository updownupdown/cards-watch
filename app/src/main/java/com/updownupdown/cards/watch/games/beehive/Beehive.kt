package com.updownupdown.cards.watch.games.beehive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

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
    override val title = "Beehive"
    override val stateSerializer: KSerializer<BeehiveState> = BeehiveState.serializer()

    override val rules = listOf(
        "## Objective",
        "Clear all 52 cards by gathering sets of four cards of the same rank.",
        "## Board",
        "- The six piles at the top hold one rank each. A set of four is cleared at once.",
        "- Bottom left is the **beehive**; bottom right is the **waste**.",
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

    @Composable
    override fun Board(state: BeehiveState, play: (BeehiveState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val over = outcome(state) != null
        CardTable(modifier) {
            // Six piles above the beehive, the stock count and the waste.
            val layout = CardLayout(3, 3, width, height, gap = 0.12f, extra = 0.1f)
            fun x(cell: Int) = layout.x(cell % 3)
            fun y(cell: Int) = layout.y(cell / 3)
            state.tableau.forEachIndexed { i, pile ->
                pile(pile, x(i), y(i), layout.cardWidth) { feedback.tryMove(if (over) null else joinPile(state, i), play) }
            }
            pile(state.beehive, x(BEEHIVE_CELL), y(BEEHIVE_CELL), layout.cardWidth, faceDown = { it != state.beehive.lastIndex }) {
                feedback.tryMove(if (over) null else playBeehive(state), play)
            }
            pile(state.waste, x(WASTE_CELL), y(WASTE_CELL), layout.cardWidth) {
                feedback.tryMove(if (over) null else playWaste(state), play)
            }
        }
    }
}
