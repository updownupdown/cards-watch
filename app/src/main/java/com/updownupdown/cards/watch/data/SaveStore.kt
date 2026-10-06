package com.updownupdown.cards.watch.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameResult
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.ALL_GAMES
import com.updownupdown.cards.watch.widget.refreshSurfaces
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Keeps one game in progress per game, in memory and in a file per game,
 * written after every move. Also remembers the last game played.
 */
class SaveStore(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val sessions = mutableMapOf<String, GameSession<*>>()
    // One write at a time, in order, so an older state never lands last.
    private val writer = Dispatchers.IO.limitedParallelism(1)

    private val stats = mutableStateMapOf<String, GameStats>().apply {
        ALL_GAMES.forEach { game ->
            val saved = prefs.getString(KEY_STATS + game.id, null)
            put(
                game.id,
                // Before streaks, scores and times were kept, only the two counts were.
                runCatching { saved?.let { json.decodeFromString(GameStats.serializer(), it) } }.getOrNull()
                    ?: GameStats(prefs.getInt(KEY_WON + game.id, 0), prefs.getInt(KEY_LOST + game.id, 0)),
            )
        }
    }

    /** The games played, the last one first, for the tile. */
    var recentGameIds: List<String> by mutableStateOf(
        prefs.getString(KEY_RECENT, null)?.split(',')?.filter { it.isNotEmpty() }
            ?: listOfNotNull(prefs.getString(KEY_LAST_GAME, null)),
    )
        private set

    var lastGameId: String?
        get() = recentGameIds.firstOrNull()
        set(value) {
            if (value == null || value == recentGameIds.firstOrNull()) return
            recentGameIds = (listOf(value) + (recentGameIds - value)).take(MAX_RECENT)
            prefs.edit().putString(KEY_RECENT, recentGameIds.joinToString(",")).apply()
            refreshSurfaces(context)
        }

    /** The games pinned to the top of the home list, in the order they were pinned. */
    var pinnedGameIds: List<String> by mutableStateOf(prefs.getString(KEY_PINNED, null)?.split(',')?.filter { it.isNotEmpty() } ?: emptyList())
        private set

    /** Whether the app buzzes (the home screen's switch). */
    var hapticsOn: Boolean by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set

    fun setHaptics(on: Boolean) {
        hapticsOn = on
        prefs.edit().putBoolean(KEY_HAPTICS, on).apply()
    }

    /** How cards colour their suits (the Settings screen's Suit Colours). */
    var suitColours: SuitColours by mutableStateOf(
        runCatching { SuitColours.valueOf(prefs.getString(KEY_SUIT_COLOURS, null) ?: "") }.getOrDefault(SuitColours.Auto),
    )
        private set

    fun chooseSuitColours(colours: SuitColours) {
        suitColours = colours
        prefs.edit().putString(KEY_SUIT_COLOURS, colours.name).apply()
    }

    /** How many games the home screen's Continue Playing shows (0 hides it). */
    var continueShown: Int by mutableStateOf(prefs.getInt(KEY_CONTINUE, 3).takeIf { it in CONTINUE_CHOICES } ?: 3)
        private set

    fun chooseContinueShown(count: Int) {
        continueShown = count
        prefs.edit().putInt(KEY_CONTINUE, count).apply()
    }

    /** Pins [game] to the top of the home list, or unpins it. */
    fun togglePin(game: Game<*>) {
        pinnedGameIds = if (game.id in pinnedGameIds) pinnedGameIds - game.id else pinnedGameIds + game.id
        prefs.edit().putString(KEY_PINNED, pinnedGameIds.joinToString(",")).apply()
    }

    /** How [game]'s deals have ended. Reads as state, so the screen follows it. */
    fun stats(game: Game<*>): GameStats = stats[game.id] ?: GameStats()

    /** Every game's stats, by its id. Reads as state. */
    fun allStats(): Map<String, GameStats> = ALL_GAMES.associate { it.id to stats(it) }

    private fun record(game: Game<*>, result: GameResult) {
        val now = stats(game).with(result, game.lowScoreWins)
        stats[game.id] = now
        prefs.edit().putString(KEY_STATS + game.id, json.encodeToString(GameStats.serializer(), now)).apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> session(game: Game<S>): GameSession<S> =
        sessions.getOrPut(game.id) {
            val saved = load(game)
            GameSession(game, saved, onResult = { record(game, it) }) { write(game, it) }.also {
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
        const val KEY_RECENT = "recentGames"
        const val KEY_PINNED = "pinnedGames"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SUIT_COLOURS = "suitColours"
        const val KEY_CONTINUE = "continueShown"
        const val KEY_STATS = "stats:"
        const val KEY_WON = "won:"
        const val KEY_LOST = "lost:"
        const val MAX_RECENT = 4
        val json = Json { ignoreUnknownKeys = true }
    }
}
