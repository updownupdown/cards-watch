package com.updownupdown.cards.watch.games.hanoi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Three piles, each listed from the bottom card up. */
@Serializable
data class HanoiState(val piles: List<List<Card>>, val moves: Int = 0)

object TowerOfHanoi : Game<HanoiState> {
    const val PILES = 3
    private val RANKS = Rank.entries.take(9)

    override val id = "tower-of-hanoi"
    override val title = "Tower of Hanoi"
    override val stateSerializer: KSerializer<HanoiState> = HanoiState.serializer()
    override val saveVersion = 2

    override val rules = listOf(
        "## Objective",
        "Build one pile ordered from **9** at the bottom up to **Ace** at the top.",
        "## Gameplay",
        "- Tap a pile to pick up its top card, then tap the pile to put it on.",
        "- A card goes on an empty pile or on a card of **higher rank**.",
        "- There is no losing: keep moving until the tower is built.",
    )

    override fun newGame(random: Random): HanoiState {
        val cards = RANKS.map { Card(it, Suit.Spades) }.shuffled(random)
        return HanoiState(cards.chunked(3))
    }

    fun move(state: HanoiState, from: Int, to: Int): HanoiState? {
        if (from == to) return null
        val card = state.piles[from].lastOrNull() ?: return null
        val target = state.piles[to].lastOrNull()
        if (target != null && card.rank.value >= target.rank.value) return null
        val piles = state.piles.mapIndexed { i, pile ->
            when (i) {
                from -> pile.dropLast(1)
                to -> pile + card
                else -> pile
            }
        }
        return HanoiState(piles, state.moves + 1)
    }

    private fun isBuilt(state: HanoiState) =
        state.piles.any { pile -> pile.map { it.rank } == RANKS.reversed() }

    override fun outcome(state: HanoiState) =
        if (isBuilt(state)) Outcome(true, "Built in ${state.moves} moves") else null

    override fun status(state: HanoiState) = if (state.moves == 1) "1 move" else "${state.moves} moves"

    @Composable
    override fun Board(state: HanoiState, play: (HanoiState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        CardTable(modifier) {
            val gap = 6.dp
            val cardWidth = (width - gap * (PILES - 1)) / PILES
            state.piles.forEachIndexed { index, pile ->
                val x = (cardWidth + gap) * index
                fan(pile, x, 0.dp, cardWidth, height, selectedFrom = if (selected == index) pile.lastIndex else null)
                tap(x, 0.dp, cardWidth, height) {
                    val from = selected
                    when {
                        outcome(state) != null -> feedback.reject()
                        from == null && pile.isNotEmpty() -> {
                            feedback.move()
                            selected = index
                        }
                        from == null -> feedback.reject()
                        from == index -> selected = null
                        else -> feedback.tryMove(move(state, from, index), play)
                    }
                }
            }
        }
    }
}
