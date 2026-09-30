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
)

/** A game being played: its current state, and the states to undo back to. */
class GameSession<S : Any>(
    val game: Game<S>,
    saved: SavedGame<S>?,
    private val onChange: (SavedGame<S>) -> Unit,
) {
    var state: S by mutableStateOf(saved?.state ?: game.newGame())
        private set
    private val history = mutableStateListOf<S>().apply { saved?.history?.let(::addAll) }

    val canUndo: Boolean get() = history.isNotEmpty()
    val outcome: Outcome? get() = game.outcome(state)

    fun play(next: S) {
        if (next == state) return
        if (game.isUndoStep(state, next)) {
            history.add(state)
            if (history.size > MAX_UNDO) history.removeAt(0)
        }
        state = next
        save()
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeAt(history.lastIndex)
        save()
        return true
    }

    /** Deals a new game. It's an undo step, so an accidental reset can be undone. */
    fun newGame() = play(game.newGame())

    private fun save() = onChange(SavedGame(game.saveVersion, state, history.toList()))

    companion object {
        const val MAX_UNDO = 200
    }
}
