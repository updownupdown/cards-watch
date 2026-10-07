package com.updownupdown.cards.watch.games.lightsout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.text
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Which cards are face up, row by row. */
@Serializable
data class LightsOutState(val faceUp: List<Boolean>, val moves: Int = 0)

/**
 * Five across and four down: the largest cards that fit the board. Every
 * board this size has exactly one solution, of 10 taps on average (the same
 * as 5×5), so play is as good; 4×4 and 5×3 fall in about four or five taps
 * (see docs/GAMES.md).
 */
object LightsOut : Game<LightsOutState> {
    const val COLUMNS = 5
    const val ROWS = 4
    const val CELLS = COLUMNS * ROWS
    /** Deals needing fewer taps than this are too easy, so aren't dealt. */
    const val MIN_SOLUTION = 7

    override val id = "lights-out"
    override val thumbnail = R.drawable.thumb_lights_out
    override val banner = R.drawable.banner_lights_out
    override val art = R.drawable.art_lights_out
    override val credit = ArtCredit("Eruption of Mt Vesuvius, Seen from the Ponte della Maddalena in Naples", "Francesco Piranesi", "c. 1783", "Rijksmuseum", "https://www.rijksmuseum.nl/en/collection/object/Eruption-of-Mt-Vesuvius-Seen-from-the-Ponte-della-Maddalena-in-Naples--e30197f5500c476812d6b072e2b4186d")
    override val accent = R.color.game_lights_out
    override val accentPale = R.color.game_lights_out_pale
    override val title = R.string.lights_out_title
    override val rating = Rating(difficulty = 50, complexity = 10, luck = 0)
    override val stateSerializer: KSerializer<LightsOutState> = LightsOutState.serializer()
    override val saveVersion = 2

    override val rules = R.array.lights_out_rules

    /** The cards shown when face up: a fixed pattern, as in the phone game. */
    val CARDS: List<Card> = List(CELLS) { Card(Rank.entries[it % Rank.entries.size], Suit.entries[it % Suit.entries.size]) }

    fun affected(index: Int): List<Int> {
        val row = index / COLUMNS
        val col = index % COLUMNS
        return buildList {
            add(index)
            if (row > 0) add(index - COLUMNS)
            if (row < ROWS - 1) add(index + COLUMNS)
            if (col > 0) add(index - 1)
            if (col < COLUMNS - 1) add(index + 1)
        }
    }

    fun tap(state: LightsOutState, index: Int): LightsOutState {
        val flipped = affected(index).toSet()
        return LightsOutState(
            faceUp = state.faceUp.mapIndexed { i, up -> if (i in flipped) !up else up },
            moves = state.moves + 1,
        )
    }

    override fun newGame(random: Random): LightsOutState {
        while (true) {
            var state = LightsOutState(List(CELLS) { false })
            val presses = (0 until CELLS).shuffled(random).take(random.nextInt(8, 16))
            presses.forEach { state = tap(state, it) }
            val fewest = fewestTaps(state.faceUp)
            if (fewest != null && fewest >= MIN_SOLUTION) return LightsOutState(state.faceUp)
        }
    }

    /**
     * The fewest taps that solve [faceUp], or null if it can't be solved:
     * Gaussian elimination over GF(2), trying every combination of the free
     * taps (none on a 5×4 board).
     */
    fun fewestTaps(faceUp: List<Boolean>): Int? {
        val n = faceUp.size
        // Row r: which taps flip card r, then whether card r is face up.
        val rows = Array(n) { r -> BooleanArray(n + 1) { c -> if (c == n) faceUp[r] else r in affected(c) } }
        val pivots = mutableListOf<Int>()
        var rank = 0
        for (col in 0 until n) {
            val pivot = (rank until n).firstOrNull { rows[it][col] } ?: continue
            rows[pivot] = rows[rank].also { rows[rank] = rows[pivot] }
            for (r in 0 until n) {
                if (r != rank && rows[r][col]) {
                    for (c in 0..n) rows[r][c] = rows[r][c] xor rows[rank][c]
                }
            }
            pivots += col
            rank++
        }
        if ((rank until n).any { rows[it][n] }) return null
        val free = (0 until n).filter { it !in pivots }
        var best = Int.MAX_VALUE
        for (mask in 0 until (1 shl free.size)) {
            val taps = BooleanArray(n)
            free.forEachIndexed { i, col -> taps[col] = (mask shr i) and 1 == 1 }
            pivots.forEachIndexed { i, col ->
                var value = rows[i][n]
                for (f in free) if (rows[i][f] && taps[f]) value = !value
                taps[col] = value
            }
            best = minOf(best, taps.count { it })
        }
        return best
    }

    override fun outcome(state: LightsOutState) =
        if (state.faceUp.none { it }) Outcome(true, text(R.string.lights_out_won, state.moves)) else null

    override val scoreName = R.string.score_moves
    override val lowScoreWins = true
    override fun score(state: LightsOutState) = state.moves

    // Nothing under the board: the board takes that room, for larger cards.
    override val boardFraction = 0.7f
    override val boardLift = -0.02f

    @Composable
    override fun Board(state: LightsOutState, play: (LightsOutState) -> Unit, feedback: Feedback, modifier: Modifier) {
        CardTable(modifier) {
            val layout = CardLayout(COLUMNS, ROWS, width, height, gap = 0.12f)
            CARDS.forEachIndexed { i, card ->
                val x = layout.xOf(i)
                val y = layout.yOf(i)
                card(card, card, x, y, layout.cardWidth, faceDown = !state.faceUp[i])
                tap(x, y, layout.cardWidth, layout.cardHeight) {
                    feedback.tryMove(if (outcome(state) != null) null else tap(state, i), play)
                }
            }
        }
    }
}

@WearGamePreview
@Composable
private fun LightsOutPreview() = GamePreview(LightsOut)

