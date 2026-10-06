package com.updownupdown.cards.watch.games.royalmarriage

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.ScrollingCards
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.ArtCredit
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

/**
 * @param line every card left, in order: the Queen of Hearts first and the King last.
 * @param selected the places picked for Discard (not an undo step).
 */
@Serializable
data class RoyalMarriageState(val line: List<Card>, val selected: Set<Int> = emptySet())

/** Discard what lies between matching cards, until the Queen and King of Hearts meet. */
object RoyalMarriage : Game<RoyalMarriageState> {
    const val COLUMNS = 5
    private const val CARDS = 52
    val QUEEN = Card(Rank.Queen, Suit.Hearts)
    val KING = Card(Rank.King, Suit.Hearts)

    /** How long a second tap has to follow the first, to be a double tap. */
    private const val DOUBLE_TAP_MS = 400L

    override val id = "royal-marriage"
    override val thumbnail = R.drawable.thumb_royal_marriage
    override val banner = R.drawable.banner_royal_marriage
    override val credit = ArtCredit("The Wedding by Proxy of Marie de' Medici to King Henry IV", "Peter Paul Rubens", "1622–25", "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:Peter_Paul_Rubens_-_The_Wedding_by_Proxy_of_Marie_de%27_Medici_to_King_Henry_IV.jpg")
    override val accent = R.color.game_royal_marriage
    override val accentPale = R.color.game_royal_marriage_pale
    override val title = "Royal Marriage"
    override val rating = Rating(difficulty = 85, complexity = 25, luck = 35)
    override val stateSerializer: KSerializer<RoyalMarriageState> = RoyalMarriageState.serializer()

    override val rules = listOf(
        "## Objective",
        "Discard every card except the **Queen of Hearts** and **King of Hearts**, which must end up side by side.",
        "## Deal",
        "All 52 cards lie in one line, read left to right and then down: the Queen of Hearts first, the King of Hearts last.",
        "## Discarding",
        "- One card, or two side by side, can be discarded if the cards either side of them match in **rank or suit**.",
        "- A longer run can go only if every card in it is of the **same suit**, and the cards either side match in rank or suit.",
        "- Tap the card or cards to remove, then **Discard**. Double-tap a single card to discard it.",
    )

    fun isCouple(card: Card) = card == QUEEN || card == KING

    private fun matches(a: Card, b: Card) = a.rank == b.rank || a.suit == b.suit

    /** Whether the [count] cards from [start] can go out. */
    fun canDiscard(line: List<Card>, start: Int, count: Int): Boolean {
        val left = line.getOrNull(start - 1) ?: return false
        val right = line.getOrNull(start + count) ?: return false
        if (count < 1) return false
        val run = line.subList(start, start + count)
        if (run.any(::isCouple) || !matches(left, right)) return false
        return count <= 2 || run.all { it.suit == run[0].suit }
    }

    /** Whether [places] are one run that can go out. */
    fun canDiscard(line: List<Card>, places: Set<Int>): Boolean {
        if (places.isEmpty()) return false
        val first = places.min()
        return places.max() - first + 1 == places.size && canDiscard(line, first, places.size)
    }

    fun hasMove(line: List<Card>) = (1 until line.size - 1).any { start ->
        (1 until line.size - start).any { canDiscard(line, start, it) }
    }

    override fun newGame(random: Random) =
        RoyalMarriageState(listOf(QUEEN) + fullDeck().filterNot(::isCouple).shuffled(random) + KING)

    /** Picks [place], or unpicks it. */
    fun toggle(state: RoyalMarriageState, place: Int): RoyalMarriageState? {
        if (state.line.getOrNull(place)?.let(::isCouple) != false) return null
        return state.copy(selected = if (place in state.selected) state.selected - place else state.selected + place)
    }

    fun discard(state: RoyalMarriageState, places: Set<Int> = state.selected): RoyalMarriageState? {
        if (!canDiscard(state.line, places)) return null
        return RoyalMarriageState(state.line.filterIndexed { i, _ -> i !in places })
    }

    override fun isUndoStep(previous: RoyalMarriageState, next: RoyalMarriageState) = previous.line != next.line

    override fun outcome(state: RoyalMarriageState) = when {
        state.line.size == 2 -> Outcome(true, "The Queen and King are wed!")
        !hasMove(state.line) -> Outcome(false, "No legal moves remain")
        else -> null
    }

    override fun actions(state: RoyalMarriageState) = listOf(
        GameAction<RoyalMarriageState>("Discard", enabled = outcome(state) == null && canDiscard(state.line, state.selected)) { discard(it) ?: it },
    )

    /** The room between the cards' window and the Discard button, on top of the layout's own. */
    private val BUTTON_GAP = 8.dp

    @Composable
    override fun Board(state: RoyalMarriageState, play: (RoyalMarriageState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val playing = outcome(state) == null
        val scroll = LocalBoardScroll.current ?: rememberScrollState()
        // A new deal starts from the top.
        LaunchedEffect(state.line.size == CARDS) {
            if (state.line.size == CARDS) scroll.scrollTo(0)
        }
        // When the last tap landed, and on which card, for telling a double tap.
        val lastTap = remember { longArrayOf(0L, -1L) }
        val board = LocalBoardSize.current
        val rows = (state.line.size + COLUMNS - 1) / COLUMNS
        val valid = canDiscard(state.line, state.selected)
        // The window stops short of the board's foot, to stand clear of the Discard button.
        Box(modifier, contentAlignment = Alignment.TopCenter) {
            ScrollingCards(COLUMNS, rows, board, board - BUTTON_GAP, scroll = scroll) { places ->
                state.line.forEachIndexed { index, card ->
                    val x = places.xOf(index)
                    val y = places.yOf(index)
                    card(
                        key = card,
                        card = card,
                        x = x,
                        y = y,
                        width = places.cardWidth,
                        selection = if (index in state.selected) (if (valid) Selection.Valid else Selection.Neutral) else null,
                        // The royal couple stays put: shaded, and never picked.
                        overlay = if (isCouple(card)) CardOverlay.Fixed else null,
                    )
                    tap(x, y, places.cardWidth, places.cardHeight) {
                        if (!playing || isCouple(card)) {
                            feedback.reject()
                            return@tap
                        }
                        val now = SystemClock.uptimeMillis()
                        val quick = lastTap[1] == index.toLong() && now - lastTap[0] <= DOUBLE_TAP_MS
                        lastTap[0] = now
                        lastTap[1] = index.toLong()
                        // The second tap of a double tap on a card that can go alone discards it.
                        val single = setOf(index)
                        if (quick && state.selected == single && canDiscard(state.line, single)) {
                            lastTap[1] = -1L
                            feedback.tryMove(discard(state, single), play)
                        } else {
                            toggle(state, index)?.let(play)
                        }
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun RoyalMarriagePreview() = GamePreview(RoyalMarriage)
