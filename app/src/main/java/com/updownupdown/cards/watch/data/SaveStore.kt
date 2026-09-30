package com.updownupdown.cards.watch.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps one game in progress per game, in memory and in a file per game,
 * written after every move. Also remembers the last game played.
 */
class SaveStore(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val sessions = mutableMapOf<String, GameSession<*>>()
    // One write at a time, in order, so an older state never lands last.
    private val writer = Dispatchers.IO.limitedParallelism(1)

    var lastGameId: String?
        get() = prefs.getString(KEY_LAST_GAME, null)
        set(value) = prefs.edit().putString(KEY_LAST_GAME, value).apply()

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> session(game: Game<S>): GameSession<S> =
        sessions.getOrPut(game.id) {
            val saved = load(game)
            GameSession(game, saved) { write(game, it) }.also {
                if (saved == null) write(game, SavedGame(game.saveVersion, it.state))
            }
        } as GameSession<S>

    private fun file(game: Game<*>) = AtomicFile(File(dir, "${game.id}.json"))

    private fun <S : Any> load(game: Game<S>): SavedGame<S>? = try {
        val file = file(game)
        if (!file.baseFile.exists()) {
            null
        } else {
            val text = file.readFully().decodeToString()
            json.decodeFromString(SavedGame.serializer(game.stateSerializer), text)
                .takeIf { it.version == game.saveVersion }
        }
    } catch (e: Exception) {
        // A save we can't read is dropped rather than crashing the app.
        Log.w(TAG, "Discarding unreadable save for ${game.id}", e)
        null
    }

    private fun <S : Any> write(game: Game<S>, saved: SavedGame<S>) {
        val text = json.encodeToString(SavedGame.serializer(game.stateSerializer), saved)
        scope.launch(writer) {
            val file = file(game)
            val out = file.startWrite()
            try {
                out.write(text.encodeToByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                Log.w(TAG, "Couldn't save ${game.id}", e)
            }
        }
    }

    private companion object {
        const val TAG = "SaveStore"
        const val KEY_LAST_GAME = "lastGame"
        val json = Json { ignoreUnknownKeys = true }
    }
}
