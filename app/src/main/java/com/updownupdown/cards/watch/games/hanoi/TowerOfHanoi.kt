package com.updownupdown.cards.watch.games.hanoi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Three piles, each listed from the bottom card up. */
@Serializable
data class HanoiState(val piles: List<List<Card>>, val moves: Int = 0)

object TowerOfHanoi : Game<HanoiState> {
    const val PILES = 3
    /** A card's width, × the board's. */
    private const val CARD_WIDTH = 0.18f
    /** The gap between piles, × the board's width. */
    private const val PILE_GAP = 0.073f
    /** How far down each card sits from the one above, × a card's height: enough to show each card's rank, even past the board's foot. */
    private const val SPREAD = 0.56f

    /** One suit for each few ranks, always the same, to tell the cards apart. */
    fun suitOf(rank: Rank) = when (rank.value) {
        in 1..3 -> Suit.Spades
        4, 5 -> Suit.Clubs
        6, 7 -> Suit.Diamonds
        else -> Suit.Hearts
    }
    private val RANKS = Rank.entries.take(9)

    override val id = "tower-of-hanoi"
    override val thumbnail = R.drawable.thumb_tower_of_hanoi
    override val banner = R.drawable.banner_tower_of_hanoi
    override val accent = R.color.game_tower_of_hanoi
    override val accentPale = R.color.game_tower_of_hanoi_pale
    override val title = "Tower of Hanoi"
    override val rating = Rating(difficulty = 40, complexity = 15, luck = 0)
    override val stateSerializer: KSerializer<HanoiState> = HanoiState.serializer()
    override val saveVersion = 3

    override val rules = listOf(
        "## Objective",
        "Build one pile ordered from **9** to **Ace**.",
        "## Gameplay",
        "Tap a pile to pick up its top card, then tap another pile to move it there.",
        "A card can be moved to an empty pile, or on a card of **higher rank**.",
        "There is no losing: keep moving until the tower is built.",
    )

    override fun newGame(random: Random): HanoiState {
        val cards = RANKS.map { Card(it, suitOf(it)) }.shuffled(random)
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

    override val scoreName = "Moves"
    override val lowScoreWins = true
    override fun score(state: HanoiState) = state.moves

    /** The move count's room, from the screen's left edge, and how far in from that edge it sits. */
    private val COUNT_HEIGHT = 40.dp
    private val COUNT_IN = 10.dp

    @Composable
    override fun Board(state: HanoiState, play: (HanoiState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var selected by remember(state) { mutableStateOf<Int?>(null) }
        // The move count alone, outside the board at the screen's left, level with its middle.
        val board = LocalBoardSize.current
        val screen = RoundScreen(board, boardLift)
        val outside = (screen.size - board) / 2
        Box(
            Modifier.offset(x = -outside, y = screen.middleY - COUNT_HEIGHT / 2).size(outside, COUNT_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "${state.moves}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = COUNT_IN),
            )
        }
        CardTable(modifier) {
            // Narrow cards, so a tall pile still shows each card's rank.
            val cardWidth = width * CARD_WIDTH
            val gap = width * PILE_GAP
            val left = (width - cardWidth * PILES - gap * (PILES - 1)) / 2
            state.piles.forEachIndexed { index, pile ->
                val x = left + (cardWidth + gap) * index
                fan(pile, x, 0.dp, cardWidth, Dp.Infinity, selectedFrom = if (selected == index) pile.lastIndex else null, spread = SPREAD)
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

@WearGamePreview
@Composable
private fun TowerOfHanoiPreview() = GamePreview(TowerOfHanoi)

