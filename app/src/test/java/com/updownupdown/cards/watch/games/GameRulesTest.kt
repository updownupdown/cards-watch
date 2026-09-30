package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.acesup.AcesUpState
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameRulesTest {
    @Test
    fun `every game deals, saves and reads back`() {
        for (game in ALL_GAMES) {
            @Suppress("UNCHECKED_CAST")
            val g = game as com.updownupdown.cards.watch.game.Game<Any>
            val state = g.newGame(Random(7))
            assertNull("${g.id} starts finished", g.outcome(state))
            val serializer = SavedGame.serializer(g.stateSerializer)
            val saved = SavedGame(g.saveVersion, state)
            assertEquals(g.id, saved, Json.decodeFromString(serializer, Json.encodeToString(serializer, saved)))
        }
    }

    @Test
    fun `lights out deals need at least the minimum taps`() {
        repeat(20) { seed ->
            val state = LightsOut.newGame(Random(seed))
            assertTrue(LightsOut.fewestTaps(state.faceUp)!! >= LightsOut.MIN_SOLUTION)
        }
        // One tap from solved is solved in one tap.
        val one = LightsOut.tap(com.updownupdown.cards.watch.games.lightsout.LightsOutState(List(25) { false }), 12)
        assertEquals(1, LightsOut.fewestTaps(one.faceUp))
    }

    @Test
    fun `aces up discards lower cards of a suit and keeps aces`() {
        fun c(rank: Rank, suit: Suit) = Card(rank, suit)
        val state = AcesUpState(
            piles = listOf(
                listOf(c(Rank.Five, Suit.Hearts)),
                listOf(c(Rank.Ace, Suit.Hearts)),
                listOf(c(Rank.King, Suit.Spades)),
                emptyList(),
            ),
            deck = emptyList(),
        )
        assertTrue(AcesUp.canDiscard(state, 0))
        assertFalse(AcesUp.canDiscard(state, 1))
        assertFalse(AcesUp.canDiscard(state, 2))
        assertNull(AcesUp.outcome(state))
    }

    @Test
    fun `triple stack sandwiches wrap and refill by suit`() {
        fun c(rank: Rank, suit: Suit) = Card(rank, suit)
        val wrap = listOf(c(Rank.Nine, Suit.Clubs), c(Rank.Queen, Suit.Clubs), c(Rank.Two, Suit.Clubs))
        assertTrue(TripleStack.isSandwich(wrap))
        assertEquals(4, TripleStack.refills(wrap))
        assertFalse(TripleStack.isSandwich(listOf(c(Rank.Ace, Suit.Clubs), c(Rank.Two, Suit.Hearts), c(Rank.Four, Suit.Clubs))))
    }

    @Test
    fun `clock turns up every card or stops at the fourth king`() {
        repeat(20) { seed ->
            var state = Clock.newGame(Random(seed))
            var steps = 0
            while (Clock.outcome(state) == null) {
                state = Clock.reveal(state)!!
                steps++
            }
            assertTrue(steps <= 52)
        }
    }
}
