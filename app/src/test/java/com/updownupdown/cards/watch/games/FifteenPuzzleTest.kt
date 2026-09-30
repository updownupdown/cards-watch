package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fifteen.FifteenState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FifteenPuzzleTest {
    @Test
    fun `new games are shuffled and hold every card once`() {
        repeat(50) { seed ->
            val state = FifteenPuzzle.newGame(Random(seed))
            assertFalse(FifteenPuzzle.isSolved(state))
            assertEquals(FifteenPuzzle.SOLVED.toSet(), state.cells.toSet())
        }
    }

    @Test
    fun `only cards beside the gap slide`() {
        val solved = FifteenState(FifteenPuzzle.SOLVED)
        assertNull(FifteenPuzzle.slide(solved, 0))
        val moved = FifteenPuzzle.slide(solved, 14)!!
        assertNull(moved.cells[14])
        assertEquals(FifteenPuzzle.SOLVED[14], moved.cells[15])
        assertEquals(1, moved.moves)
        assertTrue(FifteenPuzzle.isSolved(FifteenPuzzle.slide(moved, 15)!!))
    }

    @Test
    fun `a saved game reads back the same`() {
        val serializer = SavedGame.serializer(FifteenPuzzle.stateSerializer)
        val state = FifteenPuzzle.newGame(Random(1))
        val saved = SavedGame(1, state, listOf(FifteenState(FifteenPuzzle.SOLVED)))
        assertEquals(saved, Json.decodeFromString(serializer, Json.encodeToString(serializer, saved)))
    }
}
