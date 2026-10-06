package com.updownupdown.cards.watch.games.acesup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
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

/** Four piles, each bottom to top, and the undealt deck. */
@Serializable
data class AcesUpState(val piles: List<List<Card>>, val deck: List<Card>)

object AcesUp : Game<AcesUpState> {
    const val PILES = 4

    override val id = "aces-up"
    override val thumbnail = R.drawable.thumb_aces_up
    override val banner = R.drawable.banner_aces_up
    override val art = R.drawable.art_aces_up
    override val credit = ArtCredit("Playing Card: Ace of Spades", "Unknown artist", null, "Art Institute of Chicago", "https://www.artic.edu/artworks/82930")
    override val accent = R.color.game_aces_up
    override val accentPale = R.color.game_aces_up_pale
    override val title = "Aces Up"
    override val rating = Rating(difficulty = 75, complexity = 15, luck = 70)
    override val stateSerializer: KSerializer<AcesUpState> = AcesUpState.serializer()

    override val rules = listOf(
        "## Objective",
        "Discard the whole deck except the four Aces (one per pile).",
        "## Gameplay",
        "Tap a card to discard it when another pile shows a **higher card of the same suit**.",
        "**Aces are high** and can never be discarded.",
        "When a pile is empty, tap any other top card to move it there.",
        "When you're stuck, tap **Deal** for one more card on each pile.",
    )

    private fun value(card: Card) = if (card.rank == Rank.Ace) 14 else card.rank.value

    override fun newGame(random: Random) = deal(AcesUpState(List(PILES) { emptyList() }, shuffledDeck(random)))

    fun deal(state: AcesUpState): AcesUpState {
        val dealt = state.deck.take(PILES)
        return AcesUpState(
            piles = state.piles.mapIndexed { i, pile -> dealt.getOrNull(i)?.let { pile + it } ?: pile },
            deck = state.deck.drop(dealt.size),
        )
    }

    fun canDiscard(state: AcesUpState, pile: Int): Boolean {
        val card = state.piles[pile].lastOrNull() ?: return false
        return state.piles.any { other ->
            val top = other.lastOrNull()
            top != null && top.suit == card.suit && value(top) > value(card)
        }
    }

    /** Discards the top card of [pile], or moves it to an empty pile. */
    fun tap(state: AcesUpState, pile: Int): AcesUpState? {
        val piles = state.piles.map { it.toMutableList() }
        when {
            canDiscard(state, pile) -> piles[pile].removeAt(piles[pile].lastIndex)
            state.piles[pile].size > 1 && state.piles.any { it.isEmpty() } -> {
                val empty = state.piles.indexOfFirst { it.isEmpty() }
                piles[empty].add(piles[pile].removeAt(piles[pile].lastIndex))
            }
            else -> return null
        }
        return state.copy(piles = piles)
    }

    /** Moving a card to an empty pile only helps if it uncovers another. */
    private fun canMove(state: AcesUpState) =
        state.piles.any { it.isEmpty() } && state.piles.any { it.size > 1 }

    override fun outcome(state: AcesUpState): Outcome? {
        if (state.deck.isNotEmpty()) return null
        if ((0 until PILES).any { canDiscard(state, it) } || canMove(state)) return null
        val left = state.piles.flatten()
        return if (left.size == 4 && left.all { it.rank == Rank.Ace }) {
            Outcome(true, "Four Aces up!")
        } else {
            Outcome(false, "No moves left")
        }
    }

    override fun actions(state: AcesUpState) = listOf(
        GameAction<AcesUpState>("Deal (${state.deck.size})", enabled = state.deck.isNotEmpty()) { deal(it) },
    )

    @Composable
    override fun Board(state: AcesUpState, play: (AcesUpState) -> Unit, feedback: Feedback, modifier: Modifier) {
        CardTable(modifier) {
            // Two piles one over the other in the middle, and one at each side, level with the middle of
            // those two. The cards are as large as two rows allow, short of the board's foot to stand
            // clear of the Deal button; the side piles reach a little past the board's sides.
            val layout = CardLayout(2, 2, width, height * 0.9f, gap = 0.12f, extra = CardStyle.PILE_OFFSET)
            val middle = (width - layout.cellWidth) / 2
            val across = layout.cellWidth + layout.gap
            val places = listOf(
                middle - across to (layout.y(0) + layout.y(1)) / 2,
                middle to layout.y(0),
                middle to layout.y(1),
                middle + across to (layout.y(0) + layout.y(1)) / 2,
            )
            state.piles.forEachIndexed { index, pile ->
                val (x, y) = places[index]
                pile(pile, x, y, layout.cardWidth) {
                    feedback.tryMove(if (outcome(state) == null) tap(state, index) else null, play)
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun AcesUpPreview() = GamePreview(AcesUp)

