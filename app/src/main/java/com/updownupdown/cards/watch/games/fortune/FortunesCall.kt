package com.updownupdown.cards.watch.games.fortune

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.ui.GameButton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Call(val label: String, val points: Int) {
    Red("Red", 1),
    Black("Black", 1),
    Higher("Higher", 2),
    Lower("Lower", 2),
}

/**
 * @param anchor the face-up card the next call is made against.
 * @param previous the card before it, shown with whether the call was right.
 */
@Serializable
data class FortuneState(
    val deck: List<Card>,
    val anchor: Card,
    val previous: Card? = null,
    val lastCorrect: Boolean? = null,
    val score: Int = 0,
    val misses: Int = 0,
)

object FortunesCall : Game<FortuneState> {
    const val TARGET = 12
    const val MAX_MISSES = 3

    override val id = "fortunes-call"
    override val title = "Fortune's Call"
    override val stateSerializer: KSerializer<FortuneState> = FortuneState.serializer()

    override val rules = listOf(
        "## Objective",
        "Score 12 points before making three wrong calls.",
        "## Calls",
        "- **Red** or **Black** calls the next card's colour and scores 1 point.",
        "- **Higher** or **Lower** compares its rank with the face-up card and scores 2 points.",
        "- Equal ranks are neither higher nor lower.",
        "- The new card becomes the one to call against. The last one stays on the left, green if you called it right and red if not.",
    )

    override fun newGame(random: Random): FortuneState {
        val deck = shuffledDeck(random)
        return FortuneState(deck = deck.drop(1), anchor = deck.first())
    }

    fun matches(call: Call, anchor: Card, next: Card) = when (call) {
        Call.Red -> next.isRed
        Call.Black -> !next.isRed
        Call.Higher -> next.rank.value > anchor.rank.value
        Call.Lower -> next.rank.value < anchor.rank.value
    }

    fun call(state: FortuneState, call: Call): FortuneState? {
        if (outcome(state) != null) return null
        val next = state.deck.firstOrNull() ?: return null
        val correct = matches(call, state.anchor, next)
        return FortuneState(
            deck = state.deck.drop(1),
            anchor = next,
            previous = state.anchor,
            lastCorrect = correct,
            score = state.score + if (correct) call.points else 0,
            misses = state.misses + if (correct) 0 else 1,
        )
    }

    override fun outcome(state: FortuneState) = when {
        state.score >= TARGET -> Outcome(true, "12 points!")
        state.misses >= MAX_MISSES -> Outcome(false, "Three misses")
        state.deck.isEmpty() -> Outcome(false, "Out of cards")
        else -> null
    }

    override fun status(state: FortuneState) = "Pts ${state.score}/$TARGET · Miss ${state.misses}/$MAX_MISSES"

    @Composable
    override fun Board(state: FortuneState, play: (FortuneState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val gap = 6.dp
        CardTable(
            modifier,
            overlay = {
                // The four calls under the cards.
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f),
                    verticalArrangement = Arrangement.spacedBy(gap, Alignment.Bottom),
                ) {
                    Call.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            pair.forEach { call ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    GameButton(
                                        label = call.label,
                                        width = 200.dp,
                                        height = 200.dp,
                                        enabled = outcome(state) == null,
                                        modifier = Modifier.matchParentSize(),
                                        onClick = {
                                            val next = call(state, call)
                                            when {
                                                next == null -> feedback.reject()
                                                next.lastCorrect == true -> feedback.move().also { play(next) }
                                                else -> feedback.reject().also { play(next) }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) {
            val cardHeight = height * 0.56f
            val cardWidth = cardHeight / CARD_ASPECT
            val left = width / 2 - cardWidth - gap / 2
            state.previous?.let {
                card(it, it, left, 0.dp, cardWidth, overlay = if (state.lastCorrect == true) CardOverlay.Good else CardOverlay.Bad)
            }
            card(state.anchor, state.anchor, width / 2 + gap / 2, 0.dp, cardWidth)
        }
    }
}
