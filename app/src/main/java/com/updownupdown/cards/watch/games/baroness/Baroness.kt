package com.updownupdown.cards.watch.games.baroness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Five piles, each bottom to top, and the stock. */
@Serializable
data class BaronessState(val piles: List<List<Card>>, val stock: List<Card>)

object Baroness : Game<BaronessState> {
    const val PILES = 5

    override val id = "baroness"
    override val thumbnail = R.drawable.thumb_baroness
    override val banner = R.drawable.banner_baroness
    override val art = R.drawable.art_baroness
    override val credit = ArtCredit("Portrait of the Baroness of Bernus", "Philipp Veit", "1838", "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:Portrait_of_the_Baroness_of_Bernus_(SM_1798).png")
    override val accent = R.color.game_baroness
    override val accentPale = R.color.game_baroness_pale
    override val title = "Baroness"
    override val rating = Rating(difficulty = 50, complexity = 15, luck = 60)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<BaronessState> = BaronessState.serializer()

    override val rules = listOf(
        "## Objective",
        "**Win** by discarding all 52 cards.",
        "**Lose** when the stock is empty and no move is left.",
        "## Gameplay",
        "Tap a **King**, or two top cards that add up to **13**, to discard them. Aces are worth 1, Jacks 11, and Queens 12.",
        "To fill an empty pile, tap a card, then the empty pile.",
        "When stuck, tap **Deal** to deal one per pile.",
    )

    override fun newGame(random: Random) = deal(BaronessState(List(PILES) { emptyList() }, shuffledDeck(random)))

    fun deal(state: BaronessState): BaronessState {
        val dealt = state.stock.take(PILES)
        return BaronessState(
            piles = state.piles.mapIndexed { i, pile -> dealt.getOrNull(i)?.let { pile + it } ?: pile },
            stock = state.stock.drop(dealt.size),
        )
    }

    private fun removeTops(state: BaronessState, vararg piles: Int) = state.copy(
        piles = state.piles.mapIndexed { i, pile -> if (i in piles) pile.dropLast(1) else pile },
    )

    /**
     * The result of tapping [pile] with [selected] already chosen: a move, or
     * null with the pile to select instead (or nothing to select).
     */
    fun tap(state: BaronessState, selected: Int?, pile: Int): Pair<BaronessState?, Int?> {
        val card = state.piles[pile].lastOrNull()
        if (card == null) {
            // Fill an empty pile with the selected card.
            if (selected == null) return null to null
            val moving = state.piles[selected].last()
            val piles = state.piles.mapIndexed { i, p ->
                when (i) {
                    selected -> p.dropLast(1)
                    pile -> listOf(moving)
                    else -> p
                }
            }
            return state.copy(piles = piles) to null
        }
        if (card.rank == Rank.King) return removeTops(state, pile) to null
        if (selected == null || selected == pile) return null to (if (selected == pile) null else pile)
        val first = state.piles[selected].last()
        return if (first.rank.value + card.rank.value == 13) {
            removeTops(state, selected, pile) to null
        } else {
            null to pile
        }
    }

    private fun hasMove(state: BaronessState): Boolean {
        val tops = state.piles.mapNotNull { it.lastOrNull() }
        if (tops.any { it.rank == Rank.King }) return true
        if (tops.indices.any { i -> (i + 1 until tops.size).any { j -> tops[i].rank.value + tops[j].rank.value == 13 } }) return true
        return state.piles.any { it.isEmpty() } && state.piles.any { it.size > 1 }
    }

    override fun outcome(state: BaronessState) = when {
        state.stock.isEmpty() && state.piles.all { it.isEmpty() } -> Outcome(true, "All 52 discarded!")
        state.stock.isEmpty() && !hasMove(state) -> Outcome(false, "No moves left")
        else -> null
    }

    override fun actions(state: BaronessState) = listOf(
        GameAction<BaronessState>("Deal (${state.stock.size})", enabled = state.stock.isNotEmpty()) { deal(it) },
    )

    @Composable
    override fun Board(state: BaronessState, play: (BaronessState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        CardTable(modifier) {
            // Two piles centred in a row, over three under them.
            val layout = CardLayout(3, 2, width, height, gap = 0.12f, extra = CardStyle.PILE_OFFSET)
            val step = layout.cellWidth + layout.gap
            state.piles.forEachIndexed { index, pile ->
                pile(
                    cards = pile,
                    x = if (index < 2) layout.x(index) + step / 2 else layout.x(index - 2),
                    y = layout.y(if (index < 2) 0 else 1),
                    cardWidth = layout.cardWidth,
                    selection = if (selected == index) Selection.Neutral else null,
                ) {
                    if (outcome(state) != null) {
                        feedback.reject()
                        return@pile
                    }
                    val (next, select) = tap(state, selected, index)
                    when {
                        next != null -> {
                            feedback.move()
                            play(next)
                        }
                        select != null -> {
                            feedback.move()
                            selected = select
                        }
                        else -> {
                            if (selected == null) feedback.reject()
                            selected = null
                        }
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun BaronessPreview() = GamePreview(Baroness)

