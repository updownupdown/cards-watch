package com.updownupdown.cards.watch.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** How a deal ended: [score] for a won deal of a game that scores, and the time it was played for. */
data class GameResult(val won: Boolean, val score: Int?, val timeMs: Long)

/**
 * How a game's deals have ended. A deal counts once, the first time it's
 * won or lost; one given up with Reset doesn't count.
 *
 * @param streak wins in a row up to now; [bestStreak] the longest run.
 * @param bestScore the best score of a won deal, in games that score.
 * @param bestTimeMs the shortest time a deal was won in.
 */
@Serializable
data class GameStats(
    val won: Int = 0,
    val lost: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val bestScore: Int? = null,
    val bestTimeMs: Long? = null,
) {
    /** The share of finished deals that were won, or null before any has finished. */
    val winPercent: Int? get() = if (won + lost == 0) null else (won * 100f / (won + lost)).roundToInt()

    /** These stats after one more deal. [lowScoreWins] says whether a lower score is the better one. */
    fun with(result: GameResult, lowScoreWins: Boolean): GameStats {
        if (!result.won) return copy(lost = lost + 1, streak = 0)
        val run = streak + 1
        val score = result.score
        return copy(
            won = won + 1,
            streak = run,
            bestStreak = maxOf(bestStreak, run),
            bestScore = when {
                score == null -> bestScore
                bestScore == null -> score
                lowScoreWins -> minOf(bestScore, score)
                else -> maxOf(bestScore, score)
            },
            bestTimeMs = minOf(bestTimeMs ?: Long.MAX_VALUE, result.timeMs),
        )
    }
}

/** A time as minutes and seconds, such as 1:32. */
fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
