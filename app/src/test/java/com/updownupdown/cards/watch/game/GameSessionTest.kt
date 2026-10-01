package com.updownupdown.cards.watch.game

import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fifteen.FifteenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A session's undo, clock and results, on a clock stepped by hand. */
class GameSessionTest {
    private var clock = 0L
    private val results = mutableListOf<GameResult>()

    /** One slide from solved: the 3 of Spades and the gap swapped. */
    private val nearlySolved = FifteenPuzzle.slide(FifteenState(FifteenPuzzle.SOLVED), 14)!!.copy(moves = 9)

    private fun session(state: FifteenState = nearlySolved) =
        GameSession(FifteenPuzzle, SavedGame(1, state), onResult = { results += it }, now = { clock }) {}

    /** The move that solves [nearlySolved]. */
    private fun GameSession<FifteenState>.solve() = play(FifteenPuzzle.slide(state, 15)!!)

    @Test
    fun aWinReportsItsScoreAndTheTimePlayed() {
        val session = session()
        session.resume()
        clock += 5_000
        session.solve()
        assertEquals(listOf(GameResult(won = true, score = 10, timeMs = 5_000)), results)
        // The clock stops with the game.
        clock += 60_000
        assertEquals(5_000, session.timeMs)
    }

    @Test
    fun theClockOnlyRunsWhileTheScreenIsUp() {
        val session = session()
        clock += 9_000
        assertEquals(0, session.timeMs)
        session.resume()
        clock += 2_000
        session.pause()
        clock += 9_000
        session.resume()
        clock += 1_000
        assertEquals(3_000, session.timeMs)
    }

    @Test
    fun aDealCountsOnceHoweverItIsReplayed() {
        val session = session()
        session.solve()
        assertTrue(session.undo())
        session.solve()
        assertEquals(1, results.size)
    }

    @Test
    fun aResetCannotBeUndoneAndStartsTheClockAgain() {
        val session = session()
        session.resume()
        clock += 4_000
        session.play(FifteenPuzzle.slide(session.state, 10)!!)
        assertTrue(session.canUndo)
        session.newGame()
        assertFalse(session.canUndo)
        assertEquals(0, session.timeMs)
        assertNull(session.outcome)
    }

    @Test
    fun statsKeepStreaksBestScoresAndFastestTimes() {
        var stats = GameStats()
        stats = stats.with(GameResult(true, 40, 90_000), lowScoreWins = true)
        stats = stats.with(GameResult(true, 55, 60_000), lowScoreWins = true)
        stats = stats.with(GameResult(false, null, 10_000), lowScoreWins = true)
        stats = stats.with(GameResult(true, 30, 120_000), lowScoreWins = true)
        assertEquals(GameStats(won = 3, lost = 1, streak = 1, bestStreak = 2, bestScore = 30, bestTimeMs = 60_000), stats)
        assertEquals(75, stats.winPercent)
        assertEquals(55, GameStats().with(GameResult(true, 40, 1), false).with(GameResult(true, 55, 1), false).bestScore)
        assertEquals("1:32", formatTime(92_400))
    }

    @Test
    fun aNewGameIsDealtWhenNothingWasSaved() {
        val session = GameSession(FifteenPuzzle, null, now = { clock }) {}
        assertEquals(16, session.state.cells.size)
        assertEquals(FifteenPuzzle.newGame(Random(1)).cells.size, session.state.cells.size)
    }
}
