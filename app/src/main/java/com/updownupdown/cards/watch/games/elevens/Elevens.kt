package com.updownupdown.cards.watch.games.elevens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** The 3×3 grid (null where a place is empty) and the stock, its next card first. */
@Serializable
data class ElevensState(val grid: List<Card?>, val stock: List<Card>)

/** Pairs making 11, or a Jack, Queen and King together, out of a 3×3 grid. */
object Elevens : Game<ElevensState> {
    const val SIZE = 9
    private const val TOTAL = 11
    private val ROYALS = setOf(Rank.Jack, Rank.Queen, Rank.King)

    /** Only deals that allow this many moves in a row are dealt (the phone's rule). */
    private const val PLAYABLE_MOVES = 4
    private const val DEAL_ATTEMPTS = 100

    override val id = "elevens"
    override val thumbnail = R.drawable.thumb_elevens
    override val banner = R.drawable.banner_elevens
    override val credit = ArtCredit("Allegory of Arithmetic", "Laurent de La Hyre", "1650", "The Walters Art Museum")
    override val accent = R.color.game_elevens
    override val accentPale = R.color.game_elevens_pale
    override val title = "Elevens"
    override val rating = Rating(difficulty = 55, complexity = 10, luck = 80)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<ElevensState> = ElevensState.serializer()

    override val rules = listOf(
        "## Objective",
        "Win by clearing the whole deck, in pairs that total **11**, or in royal **Jack-Queen-King** triplets.",
        "## Gameplay",
        "Tap two cards that total 11: **A and 10, 2 and 9, 3 and 8, 4 and 7, 5 and 6**.",
        "For face cards, tap one Jack, one Queen and one King, which will be removed together.",
    )

    private fun isRoyal(card: Card) = card.rank in ROYALS

    /** Two cards totalling 11, or one each of Jack, Queen and King. */
    fun isSet(cards: List<Card>): Boolean = when (cards.size) {
        2 -> cards.none(::isRoyal) && cards[0].rank.value + cards[1].rank.value == TOTAL
        3 -> cards.map { it.rank }.toSet() == ROYALS
        else -> false
    }

    /** Whether picked cards could still grow into a set. */
    fun isPartial(cards: List<Card>): Boolean = when (cards.size) {
        1 -> true
        2 -> cards.all(::isRoyal) && cards[0].rank != cards[1].rank
        else -> false
    }

    /** The places that could go out together now. */
    fun sets(grid: List<Card?>): List<List<Int>> {
        val found = mutableListOf<List<Int>>()
        for (i in grid.indices) for (j in i + 1 until grid.size) {
            val a = grid[i] ?: continue
            val b = grid[j] ?: continue
            if (isSet(listOf(a, b))) found += listOf(i, j)
            for (k in j + 1 until grid.size) {
                val c = grid[k] ?: continue
                if (isSet(listOf(a, b, c))) found += listOf(i, j, k)
            }
        }
        return found
    }

    /** Fills each empty place from the stock, in order. */
    private fun refill(grid: List<Card?>, stock: List<Card>): ElevensState {
        val rest = stock.toMutableList()
        return ElevensState(grid.map { it ?: rest.removeFirstOrNull() }, rest)
    }

    /** Removes the cards at [places] and deals new ones in, or null when they aren't a set. */
    fun remove(state: ElevensState, places: List<Int>): ElevensState? {
        val cards = places.map { state.grid.getOrNull(it) ?: return null }
        if (places.toSet().size != places.size || !isSet(cards)) return null
        return refill(state.grid.mapIndexed { i, card -> if (i in places) null else card }, state.stock)
    }

    private fun cleared(state: ElevensState) = state.stock.isEmpty() && state.grid.all { it == null }

    /** Whether [moves] moves in a row can be made from [state] (or it can be cleared sooner). */
    private fun canPlay(state: ElevensState, moves: Int): Boolean =
        moves == 0 || sets(state.grid).any { set ->
            val next = remove(state, set)!!
            cleared(next) || canPlay(next, moves - 1)
        }

    override fun newGame(random: Random): ElevensState {
        var state = refill(List(SIZE) { null }, shuffledDeck(random))
        repeat(DEAL_ATTEMPTS - 1) {
            if (canPlay(state, PLAYABLE_MOVES)) return state
            state = refill(List(SIZE) { null }, shuffledDeck(random))
        }
        return state
    }

    override fun outcome(state: ElevensState) = when {
        cleared(state) -> Outcome(true, "The whole deck cleared!")
        sets(state.grid).isEmpty() -> Outcome(false, "No pair or royal triplet left")
        else -> null
    }

    override fun status(state: ElevensState) = "Stock: ${state.stock.size}"

    @Composable
    override fun Board(state: ElevensState, play: (ElevensState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var picked by remember(state) { mutableStateOf(emptyList<Int>()) }
        val playing = outcome(state) == null
        CardTable(modifier) {
            val layout = CardLayout(3, 3, width, height, gap = 0.12f)
            state.grid.forEachIndexed { index, card ->
                val x = layout.xOf(index)
                val y = layout.yOf(index)
                slot(x, y, layout.cardWidth)
                if (card != null) {
                    card(card, card, x, y, layout.cardWidth, selection = if (index in picked) Selection.Neutral else null)
                }
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    if (!playing || card == null) {
                        feedback.reject()
                        return@tap
                    }
                    if (index in picked) {
                        picked = picked - index
                        return@tap
                    }
                    val trying = picked + index
                    val cards = trying.map { state.grid[it]!! }
                    when {
                        isSet(cards) -> {
                            feedback.move()
                            play(remove(state, trying)!!)
                        }
                        isPartial(cards) -> picked = trying
                        else -> picked = listOf(index)
                    }
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun ElevensPreview() = GamePreview(Elevens)
