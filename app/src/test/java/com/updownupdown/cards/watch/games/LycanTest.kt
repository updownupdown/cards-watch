package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.lycan.LycanCard
import com.updownupdown.cards.watch.games.lycan.LycanState
import com.updownupdown.cards.watch.games.lycan.RoundResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LycanTest {
    private var nextId = 0
    private fun card(rank: Int) = LycanCard(nextId++, Card(Rank.entries[rank - 1], Suit.entries[nextId % 4]))

    @Test
    fun `a capture that empties your hand folds the round`() {
        // A lone 9 splits a 4 and a 3, and the Wolf draws the next card.
        val nine = card(9)
        val wolf = listOf(card(4), card(5), card(2))
        val state = LycanState(deck = listOf(card(7)), player = listOf(nine), wolf = wolf)
        val picked = state.copy(pickedPlayer = listOf(nine.id), pickedWolf = listOf(wolf[0].id, wolf[1].id))
        val next = Lycan.doCapture(picked)!!
        assertEquals(emptyList<LycanCard>(), next.player)
        assertEquals(RoundResult(won = false, points = 2 + 7), next.roundResult)
        assertEquals(9, next.wolfScore)
    }

    @Test
    fun `clearing the Wolf's hand wins even with yours empty`() {
        val nine = card(9)
        val wolf = listOf(card(4), card(5))
        val state = LycanState(deck = emptyList(), player = listOf(nine), wolf = wolf)
        val picked = state.copy(pickedPlayer = listOf(nine.id), pickedWolf = wolf.map { it.id })
        val next = Lycan.doCapture(picked)!!
        assertEquals(RoundResult(won = true, points = 0), next.roundResult)
    }

    @Test
    fun `a hand that keeps cards plays on`() {
        val five = card(5)
        val wolfFive = card(5)
        val state = LycanState(deck = listOf(card(3)), player = listOf(five, card(2)), wolf = listOf(wolfFive, card(8)))
        val next = Lycan.doCapture(state.copy(pickedPlayer = listOf(five.id), pickedWolf = listOf(wolfFive.id)))!!
        assertNull(next.roundResult)
    }
}
