package com.updownupdown.cards.watch.game

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import com.updownupdown.cards.watch.ui.BOARD_FRACTION
import com.updownupdown.cards.watch.ui.BOARD_LIFT
import com.updownupdown.cards.watch.ui.BottomActions
import kotlinx.serialization.KSerializer
import kotlin.random.Random

/**
 * A game: its rules as pure functions over an immutable state [S], and the
 * board that draws that state.
 *
 * The state must be a serializable data class holding everything needed to
 * resume the game; the app keeps an undo history of states and saves them
 * after every move.
 */
interface Game<S : Any> {
    /** Stable id: saved games are stored under it, so never change it. */
    val id: String
    val title: String

    /** The game's picture as a small square, for the tile and complication. */
    @get:DrawableRes
    val thumbnail: Int

    /** The same picture cut wide (3:1), behind the game's button on the home screen. */
    @get:DrawableRes
    val banner: Int

    /** The picture's credit, as the phone app gives it. */
    val credit: ArtCredit

    /** A dark color from that picture (in colors.xml), laid over it on the button so the title reads. */
    @get:ColorRes
    val accent: Int

    /** The same color made pale, for the game's action button and its rules' headings. */
    @get:ColorRes
    val accentPale: Int

    /** Rules text: "## " starts a heading, "- " a bullet, **text** is bold. */
    val rules: List<String>

    val stateSerializer: KSerializer<S>

    /** How hard, involved and chancy the game is, from the phone app, under the rules. */
    val rating: Rating

    /**
     * Whether the game's cards are better told apart by suit than by colour,
     * for the home screen's Suit Colours set to Auto: true where suits matter
     * (Rogue Run), false where only red and black do (Jailbreak) or neither
     * does (Lycan). When in doubt, true.
     */
    val fourSuitColours: Boolean get() = true

    /** Whether the time shows above the board; not where the board reaches round the top. */
    val showsTime: Boolean get() = true

    /** The board's side, and how far it sits above the middle, × the screen's diameter. */
    val boardFraction: Float get() = BOARD_FRACTION
    val boardLift: Float get() = BOARD_LIFT

    /** Bump when [S] changes shape; older saves are then discarded. */
    val saveVersion: Int get() = 1

    fun newGame(random: Random = Random.Default): S

    /**
     * Whether going from [previous] to [next] can be undone. Changes that
     * aren't (such as picking cards kept in the state) replace the current
     * state instead of adding to the history.
     */
    fun isUndoStep(previous: S, next: S): Boolean = true

    /**
     * What the game's score counts ("Score", "Moves"), or null when a win
     * always scores the same and there's nothing to keep the best of.
     */
    val scoreName: String? get() = null

    /** Whether a lower score is the better one (moves). */
    val lowScoreWins: Boolean get() = false

    /** What a won [state] scored, in games with a [scoreName]. */
    fun score(state: S): Int? = null

    /** How the game ended, or null while it's still being played. */
    fun outcome(state: S): Outcome?

    /** Short text for the bottom of the screen, such as the move count. */
    fun status(state: S): String? = null

    /** Buttons for the bottom of the screen (one or two), such as "Deal". */
    fun actions(state: S): List<GameAction<S>> = emptyList()

    /**
     * Draws the board, which is square. Call [play] with the next state after
     * a move; each call is one undo step.
     */
    @Composable
    fun Board(state: S, play: (S) -> Unit, feedback: Feedback, modifier: Modifier)

    /** The crescent under the board: [actions], or else [status]. */
    @Composable
    fun Bottom(state: S, play: (S) -> Unit, feedback: Feedback, size: DpSize) {
        BottomActions(actions(state), status(state), state, play, feedback, size)
    }
}

/** Each out of 100, as the phone app rates its games. */
data class Rating(val difficulty: Int, val complexity: Int, val luck: Int)

/** A game's picture: what it is, who made it and when (when known), and where it's from. */
data class ArtCredit(val title: String, val artist: String, val date: String?, val source: String)

data class Outcome(val won: Boolean, val message: String)

/** @param color the button's color, when it isn't the usual one. */
class GameAction<S>(
    val label: String,
    val enabled: Boolean = true,
    val color: Color? = null,
    val perform: (S) -> S,
)

/** Haptic feedback, the app's only feedback besides what's on screen. */
interface Feedback {
    /** A move was made. */
    fun move()

    /** The tap did nothing: an illegal move. */
    fun reject()
}
