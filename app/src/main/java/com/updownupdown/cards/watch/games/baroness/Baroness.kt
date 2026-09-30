package com.updownupdown.cards.watch.games.baroness

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardGrid
import com.updownupdown.cards.watch.cards.CardPile
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Five piles, each bottom to top, and the stock. */
@Serializable
data class BaronessState(val piles: List<List<Card>>, val stock: List<Card>)

object Baroness : Game<BaronessState> {
    const val PILES = 5

    override val id = "baroness"
    override val title = "Baroness"
    override val stateSerializer: KSerializer<BaronessState> = BaronessState.serializer()

    override val rules = listOf(
        "## Objective",
        "- **Win** by discarding all 52 cards.",
        "- **Lose** when the stock is empty and no move is left.",
        "## Discarding",
        "- Tap a **King** to discard it.",
        "- Tap two top cards that add up to **13** to discard them. Aces are worth 1, Jacks 11 and Queens 12.",
        "- Only the top card of each pile can be played.",
        "- To fill an empty pile, tap a top card, then the empty pile.",
        "- When you're stuck, tap **Deal** for one more card on each pile.",
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
        GameAction<BaronessState>("Deal", enabled = state.stock.isNotEmpty()) { deal(it) },
    )

    @Composable
    override fun Board(state: BaronessState, play: (BaronessState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        // Three piles above two, with the stock count in the last cell.
        CardGrid(
            columns = 3,
            rows = 2,
            modifier = modifier,
            gap = 0.12f,
            onTap = { index ->
                if (index >= PILES || outcome(state) != null) {
                    feedback.reject()
                    return@CardGrid
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
            },
        ) { index ->
            if (index < PILES) {
                CardPile(state.piles[index], selected = selected == index)
            } else {
                Box(contentAlignment = Alignment.Center) { BoardText("Stock\n${state.stock.size}") }
            }
        }
    }
}
