package com.updownupdown.cards.watch.games.littlewindmill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.min
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Piles list bottom to top.
 *
 * @param tableau the cross: its top, left, middle, right and bottom piles.
 * @param foundations the corners; the first starts with an Ace taken from the deck.
 * @param stock drawn from the end, a card at a time, onto the [waste].
 */
@Serializable
data class LittleWindmillState(
    val tableau: List<List<Card>>,
    val foundations: List<List<Card>>,
    val stock: List<Card>,
    val waste: List<Card> = emptyList(),
)

/** A cross of five piles built down, and a foundation in each corner built up by suit from Ace to King, the first Ace laid out at the start. */
object LittleWindmill : Game<LittleWindmillState> {
    const val TABLEAU = 5
    private const val FOUNDATIONS = 4
    private const val RANKS = 13

    /** The waste as a move's source; the tableau's piles are 0 until [TABLEAU]. */
    const val WASTE = TABLEAU

    override val id = "little-windmill"
    override val thumbnail = R.drawable.thumb_little_windmill
    override val banner = R.drawable.banner_little_windmill
    override val credit = ArtCredit("A Windmill on a Polder Waterway, Known as ‘In the Month of July’", "Paul Joseph Constantin Gabriël", "about 1889", "Rijksmuseum", "https://commons.wikimedia.org/wiki/File:Een_molen_aan_een_poldervaart,_bekend_als_%E2%80%98In_de_maand_juli%E2%80%99_Rijksmuseum_SK-A-1505.jpeg")
    override val accent = R.color.game_little_windmill
    override val accentPale = R.color.game_little_windmill_pale
    override val title = "Little Windmill"
    override val rating = Rating(difficulty = 65, complexity = 25, luck = 55)
    override val stateSerializer: KSerializer<LittleWindmillState> = LittleWindmillState.serializer()

    override val rules = listOf(
        "## Objective",
        "Move all 52 cards to the four corner foundations.",
        "## Foundations",
        "An **Ace** is taken from the deck to start the first corner. The other three Aces start the other corners as they come up.",
        "Build each foundation **up by suit**, from Ace to King.",
        "## The Cross",
        "Five cards are dealt to the cross. Build its piles **down, regardless of suit**. Nothing goes on an Ace.",
        "Any available card may fill an empty space in the cross.",
        "## Draw and Waste",
        "Tap **Draw** to turn the stock one card at a time onto the **waste**, at the left. There is no redeal.",
    )

    override fun newGame(random: Random): LittleWindmillState {
        val shuffled = shuffledDeck(random)
        // The first Ace in the deck starts the first foundation.
        val ace = shuffled.first { it.rank == Rank.Ace }
        val deck = shuffled - ace
        return LittleWindmillState(
            tableau = deck.take(TABLEAU).map { listOf(it) },
            foundations = List(FOUNDATIONS) { if (it == 0) listOf(ace) else emptyList() },
            stock = deck.drop(TABLEAU),
        )
    }

    private fun fitsFoundation(card: Card, pile: List<Card>): Boolean {
        val top = pile.lastOrNull() ?: return card.rank == Rank.Ace
        return card.suit == top.suit && card.rank.value == top.rank.value + 1
    }

    private fun fitsTableau(card: Card, pile: List<Card>): Boolean {
        val top = pile.lastOrNull() ?: return true
        return card.rank.value == top.rank.value - 1
    }

    /** The card [source] offers: the waste's top card, or a pile's. */
    fun top(state: LittleWindmillState, source: Int): Card? =
        if (source == WASTE) state.waste.lastOrNull() else state.tableau[source].lastOrNull()

    /** The foundation that takes [source]'s card, if one does. */
    fun foundationFor(state: LittleWindmillState, source: Int): Int? {
        val card = top(state, source) ?: return null
        return state.foundations.indexOfFirst { fitsFoundation(card, it) }.takeIf { it >= 0 }
    }

    fun canBuild(state: LittleWindmillState, source: Int, pile: Int): Boolean {
        val card = top(state, source) ?: return false
        if (source == pile) return false
        // Moving a lone card to another gap changes nothing.
        if (source != WASTE && state.tableau[source].size == 1 && state.tableau[pile].isEmpty()) return false
        return fitsTableau(card, state.tableau[pile])
    }

    private fun take(state: LittleWindmillState, source: Int) = if (source == WASTE) {
        state.copy(waste = state.waste.dropLast(1))
    } else {
        state.copy(tableau = state.tableau.mapIndexed { i, p -> if (i == source) p.dropLast(1) else p })
    }

    fun toFoundation(state: LittleWindmillState, source: Int): LittleWindmillState? {
        val card = top(state, source) ?: return null
        val to = foundationFor(state, source) ?: return null
        return take(state, source).copy(foundations = state.foundations.mapIndexed { i, p -> if (i == to) p + card else p })
    }

    fun toTableau(state: LittleWindmillState, source: Int, pile: Int): LittleWindmillState? {
        if (!canBuild(state, source, pile)) return null
        val card = top(state, source) ?: return null
        val taken = take(state, source)
        return taken.copy(tableau = taken.tableau.mapIndexed { i, p -> if (i == pile) p + card else p })
    }

    fun draw(state: LittleWindmillState): LittleWindmillState? {
        val card = state.stock.lastOrNull() ?: return null
        return state.copy(stock = state.stock.dropLast(1), waste = state.waste + card)
    }

    private fun hasMove(state: LittleWindmillState) = state.stock.isNotEmpty() ||
        (0..WASTE).any { source ->
            foundationFor(state, source) != null || (0 until TABLEAU).any { canBuild(state, source, it) }
        }

    override fun outcome(state: LittleWindmillState) = when {
        state.foundations.all { it.size == RANKS } -> Outcome(true, "Every suit is home!")
        !hasMove(state) -> Outcome(false, "No moves remain")
        else -> null
    }

    override fun actions(state: LittleWindmillState) = listOf(
        GameAction<LittleWindmillState>("Draw (${state.stock.size})", enabled = state.stock.isNotEmpty()) { draw(it) ?: it },
    )

    private const val SIDE = 3
    private const val GAP = 0.12f
    /** The cards' height, × the board's: short of its foot, to stand clear of the Draw button. */
    private const val BOARD_HEIGHT = 0.9f
    /** How far the waste sits in from the screen's edge, × a card's width. */
    private const val WASTE_IN = 0.3f

    // Board cells on a 3×3 grid: the cross, and a foundation in each corner.
    private val TABLEAU_CELLS = listOf(1, 3, 4, 5, 7)
    private val FOUNDATION_CELLS = listOf(0, 2, 6, 8)

    @Composable
    override fun Board(state: LittleWindmillState, play: (LittleWindmillState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        val playing = outcome(state) == null

        fun tapSource(source: Int) {
            val picked = selected
            when {
                !playing -> feedback.reject()
                // The picked card again: home, if it can go.
                picked == source -> toFoundation(state, source)?.let { feedback.tryMove(it, play) } ?: run { selected = null }
                picked != null && source != WASTE && canBuild(state, picked, source) -> feedback.tryMove(toTableau(state, picked, source), play)
                top(state, source) != null -> selected = source
                else -> feedback.reject()
            }
        }

        CardTable(modifier) {
            val grid = CardLayout(SIDE, SIDE, width, height * BOARD_HEIGHT, gap = GAP, extra = CardStyle.PILE_OFFSET)
            val cardWidth = grid.cardWidth
            val picked = selected
            val home = picked?.let { foundationFor(state, it) }
            state.foundations.forEachIndexed { i, pile ->
                pile(
                    cards = pile,
                    x = grid.xOf(FOUNDATION_CELLS[i]),
                    y = grid.yOf(FOUNDATION_CELLS[i]),
                    cardWidth = cardWidth,
                    overlay = if (i == home) CardOverlay.Target else null,
                ) {
                    // Any corner sends the picked card to the one it fits.
                    feedback.tryMove(if (playing && picked != null) toFoundation(state, picked) else null, play)
                }
            }
            state.tableau.forEachIndexed { i, pile ->
                pile(
                    cards = pile,
                    x = grid.xOf(TABLEAU_CELLS[i]),
                    y = grid.yOf(TABLEAU_CELLS[i]),
                    cardWidth = cardWidth,
                    // Where the picked card can go.
                    overlay = if (picked != null && canBuild(state, picked, i)) CardOverlay.Target else null,
                    selection = if (picked == i) Selection.Neutral else null,
                ) { tapSource(i) }
            }
            // The waste, level with the cross's middle row: from as far left as the round screen allows, a little in.
            val wasteY = grid.y(1)
            val wasteX = min(RoundScreen(width).left(wasteY, wasteY + grid.cellHeight), grid.x(0) - grid.gap - grid.cellWidth) + cardWidth * WASTE_IN
            pile(
                cards = state.waste,
                x = wasteX,
                y = wasteY,
                cardWidth = cardWidth,
                selection = if (picked == WASTE) Selection.Neutral else null,
            ) { tapSource(WASTE) }
        }
    }
}

@WearGamePreview
@Composable
private fun LittleWindmillPreview() = GamePreview(LittleWindmill)

