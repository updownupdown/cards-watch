package com.updownupdown.cards.watch.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    /** Rules text: "## " starts a heading, "- " a bullet, **text** is bold. */
    val rules: List<String>

    val stateSerializer: KSerializer<S>

    /** Bump when [S] changes shape; older saves are then discarded. */
    val saveVersion: Int get() = 1

    fun newGame(random: Random = Random.Default): S

    fun isWon(state: S): Boolean

    /** Short text for the bottom of the screen, such as the move count. */
    fun status(state: S): String? = null

    /** Message shown over the board once the game is won. */
    fun winMessage(state: S): String = "You won!"

    /** Buttons for the bottom of the screen, such as "Deal". */
    fun actions(state: S): List<GameAction<S>> = emptyList()

    /**
     * Draws the board, which is square. Call [play] with the next state after
     * a move; each call is one undo step.
     */
    @Composable
    fun Board(state: S, play: (S) -> Unit, feedback: Feedback, modifier: Modifier)
}

class GameAction<S>(
    val label: String,
    val enabled: Boolean = true,
    val perform: (S) -> S,
)

/** Haptic feedback, the app's only feedback besides what's on screen. */
interface Feedback {
    /** A move was made. */
    fun move()

    /** The tap did nothing: an illegal move. */
    fun reject()
}
