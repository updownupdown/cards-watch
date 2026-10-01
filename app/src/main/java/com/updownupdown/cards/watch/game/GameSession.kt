package com.updownupdown.cards.watch.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/** The saved form of a game in progress: its state and undo history. */
@Serializable
data class SavedGame<S>(
    val version: Int,
    val state: S,
    val history: List<S> = emptyList(),
    /** Whether this deal's result is already in the stats. */
    val counted: Boolean = false,
    /** How long this deal has been played for. */
    val elapsedMs: Long = 0,
)

/**
 * A game being played: its current state, the states to undo back to, and
 * how long the deal has been played (while its screen is up).
 *
 * @param now a clock in milliseconds that only runs forwards.
 */
class GameSession<S : Any>(
    val game: Game<S>,
    saved: SavedGame<S>?,
    private val onResult: (GameResult) -> Unit = {},
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onChange: (SavedGame<S>) -> Unit,
) {
    private var counted = saved?.counted ?: false
    var state: S by mutableStateOf(saved?.state ?: game.newGame())
        private set
    private val history = mutableStateListOf<S>().apply { saved?.history?.let(::addAll) }

    /** Whether the last change was an undo, so the board can run it backwards. */
    var undone: Boolean by mutableStateOf(false)
        private set

    val canUndo: Boolean get() = history.isNotEmpty()
    val outcome: Outcome? get() = game.outcome(state)

    /** The time banked so far, and when the clock was last started (null while it's stopped). */
    private var elapsedMs = saved?.elapsedMs ?: 0L
    private var runningSince: Long? = null
    /** Whether the game's screen is up, so the clock should run while the deal is unfinished. */
    private var watched = false

    /** How long this deal has been played for. */
    val timeMs: Long get() = elapsedMs + (runningSince?.let { now() - it } ?: 0)

    /** A won deal's score, in games that score. */
    val score: Int? get() = outcome?.takeIf { it.won }?.let { game.score(state) }

    /** The game's screen came up: the clock runs. */
    fun resume() {
        watched = true
        startClock()
    }

    /** The game's screen went away (or the watch went to sleep): the clock stops. */
    fun pause() {
        watched = false
        if (runningSince != null) {
            stopClock()
            save()
        }
    }

    private fun startClock() {
        if (watched && runningSince == null && outcome == null) runningSince = now()
    }

    private fun stopClock() {
        elapsedMs = timeMs
        runningSince = null
    }

    fun play(next: S) {
        if (next == state) return
        if (game.isUndoStep(state, next)) {
            history.add(state)
            if (history.size > MAX_UNDO) history.removeAt(0)
        }
        undone = false
        state = next
        game.outcome(next)?.let {
            stopClock()
            // A deal counts once, however it's undone and replayed.
            if (!counted) {
                counted = true
                onResult(GameResult(it.won, if (it.won) game.score(next) else null, elapsedMs))
            }
        }
        save()
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        undone = true
        state = history.removeAt(history.lastIndex)
        startClock()
        save()
        return true
    }

    /** Deals a new game. There's no undoing back into the old one. */
    fun newGame() {
        history.clear()
        counted = false
        undone = false
        elapsedMs = 0
        runningSince = null
        state = game.newGame()
        startClock()
        save()
    }

    private fun save() = onChange(SavedGame(game.saveVersion, state, history.toList(), counted, timeMs))

    companion object {
        const val MAX_UNDO = 200
    }
}
