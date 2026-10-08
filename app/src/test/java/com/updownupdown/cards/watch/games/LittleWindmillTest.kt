package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill.TABLEAU
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill.WASTE
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmillState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LittleWindmillTest {
    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    private fun state(
        tableau: List<List<Card>> = List(TABLEAU) { emptyList() },
        foundations: List<List<Card>> = List(4) { emptyList() },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
    ) = LittleWindmillState(tableau, foundations, stock, waste)

    /** A cross with [cards] on its piles, a pile each. */
    private fun cross(vararg cards: Card?) = List(TABLEAU) { i -> listOfNotNull(cards.getOrNull(i)) }

    @Test
    fun `an ace from the deck starts the first corner`() {
        repeat(50) { seed ->
            val s = LittleWindmill.newGame(Random(seed))
            assertEquals(Rank.Ace, s.foundations[0].single().rank)
            assertTrue(s.foundations.drop(1).all { it.isEmpty() })
            // Five cards in the cross, the rest in the stock, every card once.
            assertTrue(s.tableau.all { it.size == 1 })
            assertEquals(52 - 1 - TABLEAU, s.stock.size)
            assertTrue(s.waste.isEmpty())
            val all = s.tableau.flatten() + s.foundations.flatten() + s.stock
            assertEquals(fullDeck().toSet(), all.toSet())
            assertEquals(52, all.size)
        }
    }

    @Test
    fun `empty corners take only an ace`() {
        val s = state(tableau = cross(c(Rank.Two, Suit.Hearts), c(Rank.Ace, Suit.Clubs)))
        assertNull(LittleWindmill.foundationFor(s, 0))
        assertEquals(0, LittleWindmill.foundationFor(s, 1))
        val moved = LittleWindmill.toFoundation(s, 1)!!
        assertEquals(listOf(c(Rank.Ace, Suit.Clubs)), moved.foundations[0])
        assertTrue(moved.tableau[1].isEmpty())
    }

    @Test
    fun `foundations build up by suit`() {
        val s = state(
            tableau = cross(c(Rank.Two, Suit.Hearts), c(Rank.Two, Suit.Spades), c(Rank.Three, Suit.Spades)),
            foundations = listOf(listOf(c(Rank.Ace, Suit.Spades)), emptyList(), emptyList(), emptyList()),
        )
        // Not the wrong suit, nor skipping a rank.
        assertNull(LittleWindmill.toFoundation(s, 0))
        assertNull(LittleWindmill.toFoundation(s, 2))
        val moved = LittleWindmill.toFoundation(s, 1)!!
        assertEquals(c(Rank.Two, Suit.Spades), moved.foundations[0].last())
        assertNotNull(LittleWindmill.toFoundation(moved, 2))
    }

    @Test
    fun `foundations stop at king`() {
        // Spades finished; the other corners up to their Queens, so an ace has no empty corner to start.
        val s = state(
            tableau = cross(c(Rank.Ace, Suit.Spades), c(Rank.King, Suit.Hearts)),
            foundations = listOf(Suit.Spades, Suit.Hearts, Suit.Clubs, Suit.Diamonds).map { suit ->
                Rank.entries.filter { suit == Suit.Spades || it != Rank.King }.map { c(it, suit) }
            },
        )
        // Nothing wraps round from the King to an Ace.
        assertNull(LittleWindmill.foundationFor(s, 0))
        assertNull(LittleWindmill.toFoundation(s, 0))
        assertEquals(1, LittleWindmill.foundationFor(s, 1))
    }

    @Test
    fun `every corner built to king wins`() {
        val s = state(foundations = Suit.entries.map { suit -> Rank.entries.map { c(it, suit) } })
        assertEquals(true, LittleWindmill.outcome(s)?.won)
    }

    @Test
    fun `the cross builds down regardless of suit`() {
        val s = state(
            tableau = cross(c(Rank.Nine, Suit.Hearts), c(Rank.Eight, Suit.Spades), c(Rank.Seven, Suit.Hearts), c(Rank.Nine, Suit.Clubs)),
        )
        assertTrue(LittleWindmill.canBuild(s, 1, 0))
        // Not up, not the same rank, not two down.
        assertFalse(LittleWindmill.canBuild(s, 0, 1))
        assertFalse(LittleWindmill.canBuild(s, 3, 0))
        assertFalse(LittleWindmill.canBuild(s, 2, 0))
        val moved = LittleWindmill.toTableau(s, 1, 0)!!
        assertEquals(listOf(c(Rank.Nine, Suit.Hearts), c(Rank.Eight, Suit.Spades)), moved.tableau[0])
        assertTrue(moved.tableau[1].isEmpty())
    }

    @Test
    fun `nothing goes on an ace in the cross`() {
        val s = state(
            tableau = cross(c(Rank.Ace, Suit.Hearts), c(Rank.Two, Suit.Spades), c(Rank.King, Suit.Clubs), c(Rank.Ace, Suit.Clubs)),
            waste = listOf(c(Rank.Queen, Suit.Diamonds)),
        )
        for (source in 0..WASTE) {
            assertFalse("$source on the ace", LittleWindmill.canBuild(s, source, 0))
            assertNull(LittleWindmill.toTableau(s, source, 0))
        }
        // An ace itself goes on a Two, but not round on a King.
        assertTrue(LittleWindmill.canBuild(s, 3, 1))
        assertFalse(LittleWindmill.canBuild(s, 3, 2))
    }

    @Test
    fun `any card fills a gap in the cross, but a lone card doesn't move gap to gap`() {
        val s = state(
            tableau = cross(null, c(Rank.King, Suit.Spades), c(Rank.Four, Suit.Hearts)),
            waste = listOf(c(Rank.Seven, Suit.Clubs)),
        )
        assertTrue(LittleWindmill.canBuild(s, WASTE, 0))
        assertFalse(LittleWindmill.canBuild(s, 1, 0))
        val stacked = s.copy(tableau = cross(null, c(Rank.King, Suit.Spades)).mapIndexed { i, p -> if (i == 1) listOf(c(Rank.Five, Suit.Hearts)) + p else p })
        assertTrue(LittleWindmill.canBuild(stacked, 1, 0))
    }

    @Test
    fun `the stock draws one card at a time onto the waste, with no redeal`() {
        val s = state(
            tableau = cross(c(Rank.King, Suit.Spades), c(Rank.King, Suit.Hearts), c(Rank.King, Suit.Clubs), c(Rank.King, Suit.Diamonds), c(Rank.Jack, Suit.Spades)),
            stock = listOf(c(Rank.Two, Suit.Clubs), c(Rank.Five, Suit.Hearts)),
        )
        val once = LittleWindmill.draw(s)!!
        assertEquals(listOf(c(Rank.Two, Suit.Clubs)), once.stock)
        assertEquals(listOf(c(Rank.Five, Suit.Hearts)), once.waste)
        val twice = LittleWindmill.draw(once)!!
        assertNull(LittleWindmill.draw(twice))
        // The waste's top card plays; with nothing left to move, it's over.
        assertEquals(c(Rank.Two, Suit.Clubs), LittleWindmill.top(twice, WASTE))
        assertEquals(false, LittleWindmill.outcome(twice)?.won)
        assertNull(LittleWindmill.outcome(once))
    }
}
