package com.updownupdown.cards.watch.games.tentwentythirty

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.ScrollingCards
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalBoardScroll
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** The line, read left to right and then down, and the stock, its next card first. */
@Serializable
data class TenTwentyThirtyState(val line: List<Card>, val stock: List<Card>)

/** Runs of cards next to each other in a line that total 10, 20 or 30 go out. */
object TenTwentyThirty : Game<TenTwentyThirtyState> {
    const val COLUMNS = 5
    private const val START = 3
    private val DECADES = setOf(10, 20, 30)

    override val id = "ten-twenty-thirty"
    override val thumbnail = R.drawable.thumb_ten_twenty_thirty
    override val banner = R.drawable.banner_ten_twenty_thirty
    override val accent = R.color.game_ten_twenty_thirty
    override val accentPale = R.color.game_ten_twenty_thirty_pale
    override val title = "Ten-Twenty-Thirty"
    override val rating = Rating(difficulty = 45, complexity = 15, luck = 75)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<TenTwentyThirtyState> = TenTwentyThirtyState.serializer()

    override val rules = listOf(
        "## Objective",
        "**Win** by discarding all 52 cards.",
        "**Lose** when the stock is empty and no run adds up.",
        "## Gameplay",
        "- Cards count their face value; Jacks, Queens and Kings count **10**.",
        "- Tap **Stock** to add the next card to the end of the line.",
        "- Any **two or more cards next to each other** in the line that total **10, 20 or 30** can be discarded.",
        "- Tap the cards of a run one after another, each next to the last; once they add up they go, and the cards after them close up.",
    )

    fun points(card: Card) = minOf(card.rank.value, 10)

    fun isDecade(cards: List<Card>) = cards.size >= 2 && cards.sumOf(::points) in DECADES

    fun hasDecade(line: List<Card>): Boolean = line.indices.any { start ->
        var sum = points(line[start])
        (start + 1 until line.size).any { end ->
            sum += points(line[end])
            sum in DECADES
        }
    }

    override fun newGame(random: Random): TenTwentyThirtyState {
        val deck = shuffledDeck(random)
        return TenTwentyThirtyState(deck.take(START), deck.drop(START))
    }

    fun draw(state: TenTwentyThirtyState): TenTwentyThirtyState? {
        val card = state.stock.firstOrNull() ?: return null
        return TenTwentyThirtyState(state.line + card, state.stock.drop(1))
    }

    /** Discards the run from [first] to [last], or null when it doesn't total a decade. */
    fun discard(state: TenTwentyThirtyState, first: Int, last: Int): TenTwentyThirtyState? {
        if (first < 0 || last >= state.line.size || first > last) return null
        if (!isDecade(state.line.subList(first, last + 1))) return null
        return state.copy(line = state.line.take(first) + state.line.drop(last + 1))
    }

    override fun outcome(state: TenTwentyThirtyState) = when {
        state.line.isEmpty() && state.stock.isEmpty() -> Outcome(true, "Every card counted out!")
        state.stock.isEmpty() && !hasDecade(state.line) -> Outcome(false, "No run adds up")
        else -> null
    }

    override fun actions(state: TenTwentyThirtyState) = listOf(
        GameAction<TenTwentyThirtyState>("Stock (${state.stock.size})", enabled = state.stock.isNotEmpty() && outcome(state) == null) { draw(it) ?: it },
    )

    @Composable
    override fun Board(state: TenTwentyThirtyState, play: (TenTwentyThirtyState) -> Unit, feedback: Feedback, modifier: Modifier) {
        // The picked run, as the places of its first and last cards.
        var picked by remember(state) { mutableStateOf<IntRange?>(null) }
        val playing = outcome(state) == null
        val scroll = LocalBoardScroll.current ?: rememberScrollState()
        // A card drawn goes on the end of the line: bring it into view.
        var shown by remember { mutableStateOf(state.line.size) }
        LaunchedEffect(state.line.size) {
            if (state.line.size > shown) {
                withFrameNanos {}
                scroll.animateScrollTo(scroll.maxValue)
            } else if (state.line.size <= START) {
                scroll.scrollTo(0)
            }
            shown = state.line.size
        }
        val board = LocalBoardSize.current
        val rows = (state.line.size + COLUMNS - 1) / COLUMNS
        Box(modifier, contentAlignment = Alignment.Center) {
            ScrollingCards(COLUMNS, rows, board, board, scroll = scroll) { places ->
                val run = picked
                state.line.forEachIndexed { index, card ->
                    val x = places.xOf(index)
                    val y = places.yOf(index)
                    card(card, card, x, y, places.cardWidth, selection = if (run != null && index in run) Selection.Neutral else null)
                    tap(x, y, places.cardWidth, places.cardHeight) {
                        if (!playing) {
                            feedback.reject()
                            return@tap
                        }
                        val next = when {
                            run == null -> index..index
                            index in run -> when (index) {
                                // Only an end comes off, so the run stays unbroken.
                                run.first -> if (run.first == run.last) null else run.first + 1..run.last
                                run.last -> run.first until run.last
                                else -> run
                            }
                            index == run.first - 1 -> index..run.last
                            index == run.last + 1 -> run.first..index
                            else -> index..index
                        }
                        val sum = next?.sumOf { points(state.line[it]) } ?: 0
                        when {
                            next == null -> picked = null
                            next.first != next.last && sum in DECADES -> feedback.tryMove(discard(state, next.first, next.last), play)
                            sum > DECADES.max() -> picked = index..index
                            else -> picked = next
                        }
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun TenTwentyThirtyPreview() = GamePreview(TenTwentyThirty)
