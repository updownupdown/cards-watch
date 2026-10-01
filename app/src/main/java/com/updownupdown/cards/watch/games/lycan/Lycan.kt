package com.updownupdown.cards.watch.games.lycan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.BottomActions
import com.updownupdown.cards.watch.ui.LocalBoardSize
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Lycan's deck has five copies of each rank, so cards carry an id. */
@Serializable
data class LycanCard(val id: Int, val card: Card) {
    val value get() = card.rank.value
}

/**
 * @param deck drawn from the end; its last card is the face-up next card.
 * @param pickedPlayer, pickedWolf the cards picked for a capture (not undo steps).
 * @param roundResult set once a round is over: the points it scored and who won.
 */
@Serializable
data class LycanState(
    val deck: List<LycanCard>,
    val player: List<LycanCard> = emptyList(),
    val wolf: List<LycanCard> = emptyList(),
    val playerScore: Int = 0,
    val wolfScore: Int = 0,
    val round: Int = 1,
    val roundResult: RoundResult? = null,
    val pickedPlayer: List<Int> = emptyList(),
    val pickedWolf: List<Int> = emptyList(),
)

@Serializable
data class RoundResult(val won: Boolean, val points: Int)

enum class Capture(val label: String) { Perfect("Perfect"), Sum("Sum"), Split("Split"), Over("Over") }

/** The phone's Medium deck: Aces to 9s, five of each. */
object Lycan : Game<LycanState> {
    private const val MAX_RANK = 9
    private const val HAND = 4
    const val TARGET = 100

    override val id = "lycan"
    override val thumbnail = R.drawable.thumb_lycan
    override val banner = R.drawable.banner_lycan
    override val accent = Color(0xFF1C3F47)
    override val title = "Lycan"
    override val stateSerializer: KSerializer<LycanState> = LycanState.serializer()

    override val rules = listOf(
        "## Objective",
        "Empty the **Wolf's hand** (top) each round, and race the Wolf to **100 points**.",
        "## Capturing",
        "Tap your cards (bottom) and the Wolf's cards to pick a capture, then tap its button. The next card is shown in the middle.",
        "- **Perfect:** one of your cards takes a Wolf card of the same rank. You draw the next card.",
        "- **Sum:** two or more of your cards add up to one Wolf card. You draw the next card.",
        "- **Split:** one of your cards equals several Wolf cards added up. The Wolf draws the next card.",
        "- **Over:** one of your cards takes a lower Wolf card. The Wolf draws the difference in cards.",
        "- **Fold** ends the round at any time.",
        "## Scoring",
        "- Empty the Wolf's hand and you score the cards left in your hand.",
        "- Otherwise the Wolf scores the cards left in its hand.",
        "- The first to **100** wins.",
    )

    private fun buildDeck(random: Random): List<LycanCard> {
        val suits = Suit.entries
        var id = 0
        return (0 until 5).flatMap { copy ->
            (1..MAX_RANK).map { rank -> LycanCard(id++, Card(Rank.entries[rank - 1], suits[copy % suits.size])) }
        }.shuffled(random)
    }

    private fun deal(state: LycanState): LycanState {
        val deck = state.deck.toMutableList()
        val wolf = List(HAND) { deck.removeAt(deck.lastIndex) }
        val player = List(HAND) { deck.removeAt(deck.lastIndex) }
        return state.copy(
            deck = deck,
            wolf = wolf.sortedBy { it.value },
            player = player.sortedBy { it.value },
            roundResult = null,
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
    }

    override fun newGame(random: Random) = deal(LycanState(deck = buildDeck(random)))

    fun capture(player: List<LycanCard>, wolf: List<LycanCard>): Capture? {
        if (player.isEmpty() || wolf.isEmpty()) return null
        val mine = player.sumOf { it.value }
        val theirs = wolf.sumOf { it.value }
        return when {
            player.size == 1 && wolf.size == 1 -> when {
                mine == theirs -> Capture.Perfect
                mine > theirs -> Capture.Over
                else -> null
            }
            player.size >= 2 && wolf.size == 1 -> if (mine == theirs) Capture.Sum else null
            player.size == 1 && wolf.size >= 2 -> if (mine == theirs) Capture.Split else null
            else -> null
        }
    }

    private fun picked(state: LycanState) =
        state.player.filter { it.id in state.pickedPlayer } to state.wolf.filter { it.id in state.pickedWolf }

    fun pickedCapture(state: LycanState): Capture? {
        val (mine, theirs) = picked(state)
        return capture(mine, theirs)
    }

    private fun endRound(state: LycanState, won: Boolean): LycanState {
        val points = (if (won) state.player else state.wolf).sumOf { it.value }
        return state.copy(
            playerScore = state.playerScore + if (won) points else 0,
            wolfScore = state.wolfScore + if (won) 0 else points,
            roundResult = RoundResult(won, points),
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
    }

    fun doCapture(state: LycanState): LycanState? {
        if (state.roundResult != null) return null
        val (mine, theirs) = picked(state)
        val kind = capture(mine, theirs) ?: return null
        var player = state.player - mine.toSet()
        var wolf = state.wolf - theirs.toSet()
        val deck = state.deck.toMutableList()
        val (toPlayer, count) = when (kind) {
            Capture.Perfect, Capture.Sum -> true to 1
            Capture.Split -> false to 1
            Capture.Over -> false to mine.single().value - theirs.single().value
        }
        repeat(count) {
            val card = deck.removeLastOrNull() ?: return@repeat
            if (toPlayer) player = player + card else wolf = wolf + card
        }
        val next = state.copy(
            deck = deck,
            player = player.sortedBy { it.value },
            wolf = wolf.sortedBy { it.value },
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
        return if (next.wolf.isEmpty()) endRound(next, won = true) else next
    }

    fun fold(state: LycanState): LycanState? = if (state.roundResult != null) null else endRound(state, won = false)

    fun nextRound(state: LycanState, random: Random = Random.Default): LycanState? {
        if (state.roundResult == null || outcome(state) != null) return null
        // Every card, captured ones included, is shuffled back for each round.
        return deal(state.copy(deck = buildDeck(random), player = emptyList(), wolf = emptyList(), round = state.round + 1))
    }

    fun togglePlayer(state: LycanState, id: Int) = state.copy(
        pickedPlayer = if (id in state.pickedPlayer) state.pickedPlayer - id else state.pickedPlayer + id,
    )

    fun toggleWolf(state: LycanState, id: Int) = state.copy(
        pickedWolf = if (id in state.pickedWolf) state.pickedWolf - id else state.pickedWolf + id,
    )

    override fun isUndoStep(previous: LycanState, next: LycanState) =
        previous.copy(pickedPlayer = emptyList(), pickedWolf = emptyList()) !=
            next.copy(pickedPlayer = emptyList(), pickedWolf = emptyList())

    override val scoreName = "Score"

    override fun score(state: LycanState) = state.playerScore

    override fun outcome(state: LycanState) = when {
        state.playerScore >= TARGET -> Outcome(true, "You beat the Wolf ${state.playerScore} to ${state.wolfScore}")
        state.wolfScore >= TARGET -> Outcome(false, "The Wolf wins ${state.wolfScore} to ${state.playerScore}")
        else -> null
    }

    override fun actions(state: LycanState): List<GameAction<LycanState>> {
        if (state.roundResult != null) {
            return listOf(GameAction("Next round") { nextRound(it) ?: it })
        }
        val kind = pickedCapture(state)
        val (mine, theirs) = picked(state)
        val label = when (kind) {
            null -> "Take"
            Capture.Over -> "Over +${mine.single().value - theirs.single().value}"
            else -> kind.label
        }
        return listOf(
            GameAction("Fold") { fold(it) ?: it },
            GameAction(label, enabled = kind != null) { doCapture(it) ?: it },
        )
    }

    @Composable
    override fun Bottom(state: LycanState, play: (LycanState) -> Unit, feedback: Feedback, size: DpSize) {
        BottomActions(actions(state), null, state, play, feedback, size)
    }

    /** The gap between the three rows, and the cards' height × the most that would fit. */
    private val ROW_GAP = 4.4.dp
    private const val CARD_SCALE = 0.93f
    /** How see-through the next card is. */
    private const val NEXT_ALPHA = 1f
    /** The scores' size, × the names over them. */
    private const val SCORE_SCALE = 1.53f
    /** How far the names and scores sit from the next card, × a card's width, on top of [SCORE_GAP]. */
    private const val SCORE_OUT = 0.2f
    private val SCORE_GAP = 8.dp

    private fun cardHeight(board: Dp) = (board - ROW_GAP * 2) / 3 * CARD_SCALE

    @Composable
    private fun Score(name: String, score: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoardText(name)
            val style = MaterialTheme.typography.labelSmall
            Text(
                text = "$score",
                style = style.copy(fontSize = style.fontSize * SCORE_SCALE, lineHeight = style.lineHeight * SCORE_SCALE),
                color = Color.White,
            )
        }
    }

    @Composable
    override fun Board(state: LycanState, play: (LycanState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val playing = state.roundResult == null && outcome(state) == null
        val result = state.roundResult
        val board = LocalBoardSize.current
        CardTable(
            modifier,
            overlay = {
                // The scores either side of the next card; once a round ends, what it scored in the card's place.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(SCORE_GAP + cardHeight(board) / CARD_ASPECT * SCORE_OUT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Score("Wolf", state.wolfScore)
                    Box(Modifier.widthIn(min = cardHeight(board) / CARD_ASPECT), contentAlignment = Alignment.Center) {
                        if (result != null) BoardText(if (result.won) "You +${result.points}" else "Wolf +${result.points}")
                    }
                    Score("You", state.playerScore)
                }
            },
        ) {
            // Three rows of one card size: the Wolf's hand, the next card, yours.
            val cardHeight = cardHeight(height)
            val cardWidth = cardHeight / CARD_ASPECT
            val top = (height - cardHeight * 3 - ROW_GAP * 2) / 2
            val gap = 4.dp
            fun hand(cards: List<LycanCard>, picked: List<Int>, y: Dp, toggle: (LycanState, Int) -> LycanState) {
                // One row, overlapping once it outgrows the width.
                val step = if (cards.size > 1) min(cardWidth + gap, (width - cardWidth) / (cards.size - 1)) else 0.dp
                val left = (width - step * (cards.size - 1) - cardWidth) / 2
                cards.forEachIndexed { i, c ->
                    val x = left + step * i
                    card(c.id, c.card, x, y, cardWidth, z = i.toFloat(), selection = if (c.id in picked) Selection.Neutral else null)
                    tap(x, y, if (i == cards.lastIndex) cardWidth else step, cardHeight) {
                        if (playing) feedback.tryMove(toggle(state, c.id), play) else feedback.reject()
                    }
                }
            }
            hand(state.wolf, state.pickedWolf, top, ::toggleWolf)
            state.deck.lastOrNull()?.let {
                // It fades away when the round ends.
                card(it.id, it.card, (width - cardWidth) / 2, top + cardHeight + ROW_GAP, cardWidth, alpha = if (result == null) NEXT_ALPHA else 0f)
            }
            hand(state.player, state.pickedPlayer, top + (cardHeight + ROW_GAP) * 2, ::togglePlayer)
        }
    }
}
