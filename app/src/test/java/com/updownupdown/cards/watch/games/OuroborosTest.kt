package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.games.ouroboros.Ending
import com.updownupdown.cards.watch.games.ouroboros.LoopCard
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.BOTTOM
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.CLOSING
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.FIRST_STEP
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.LAST
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.MOST_STEP
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.NEAR
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.NEAR_STEP
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.TARGET_PULL
import com.updownupdown.cards.watch.games.ouroboros.OuroborosState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OuroborosTest {
    // The ring spacing, by angle: clockwise from the right, so 90° is 6 o'clock.

    /** A card's width in degrees round the loop, on a small and a large round watch (192 and 227 dp). */
    private val steps = listOf(16.3f, 16.2f, 14f, 18f)

    private val tolerance = 0.01f

    /** [Ouroboros.loopAngles] unwrapped, so each card is further round than the one before it. */
    private fun angles(count: Int, step: Float): List<Float> {
        var turns = 0f
        var last = Float.NEGATIVE_INFINITY
        return Ouroboros.loopAngles(count, step).map { a ->
            if (a + turns < last) turns += 360f
            (a + turns).also { last = it }
        }
    }

    private fun gaps(count: Int, step: Float) = angles(count, step).zipWithNext { a, b -> b - a }

    @Test
    fun `the top two sit either side of 6 o'clock, apart`() {
        for (step in steps) for (count in listOf(53, 30, 12, 3, 2)) {
            val a = angles(count, step)
            assertEquals(BOTTOM - FIRST_STEP * step / 2, a[0], tolerance)
            assertEquals(BOTTOM + FIRST_STEP * step / 2, a[1], tolerance)
            // Further apart than any other two, so their taps don't meet.
            assertTrue(gaps(count, step).drop(1).all { it < FIRST_STEP * step })
        }
    }

    @Test
    fun `the full loop runs clockwise round to 4 o'clock`() {
        for (step in steps) {
            val a = angles(53, step)
            assertEquals(53, a.size)
            assertTrue("clockwise", gaps(53, step).all { it > 0f })
            assertEquals(LAST, a.last(), tolerance)
            // Up the left, across the top and down the right: every quarter has cards.
            for (quarter in listOf(180f, 270f, 360f)) assertTrue(a.any { it in quarter - 45f..quarter + 45f })
        }
    }

    @Test
    fun `the ten after the top two are evenly apart so each rank shows`() {
        for (step in steps) for (count in listOf(53, 40, 30)) {
            val g = gaps(count, step)
            for (i in 1..NEAR) assertEquals("count $count, gap $i", NEAR_STEP * step, g[i], tolerance)
        }
    }

    @Test
    fun `the next ten close up to what the rest share`() {
        for (step in steps) {
            val g = gaps(53, step)
            val rest = g.drop(NEAR + CLOSING + 1)
            // The rest share one spacing, closer than the near ones.
            assertTrue(rest.all { kotlin.math.abs(it - rest.first()) < tolerance })
            assertTrue(rest.first() < NEAR_STEP * step)
            // Closing up steadily between the two.
            val closing = g.subList(NEAR, NEAR + CLOSING + 1)
            assertTrue(closing.zipWithNext().all { (a, b) -> b <= a + tolerance })
            assertEquals(rest.first(), closing.last(), tolerance)
        }
    }

    @Test
    fun `cards always overlap a little, but the top two`() {
        for (step in steps) for (count in 2..53) {
            for ((i, gap) in gaps(count, step).withIndex()) {
                if (i > 0) assertTrue("count $count, gap $i: $gap", gap <= MOST_STEP * step + tolerance)
                assertTrue(gap > 0f)
            }
        }
    }

    @Test
    fun `a short loop spreads its cards evenly`() {
        for (step in steps) for (count in 3..12) {
            val g = gaps(count, step).drop(1)
            assertTrue("count $count", g.all { kotlin.math.abs(it - g.first()) < tolerance })
        }
    }

    @Test
    fun `the gap is at 5 o'clock and only grows as the loop shortens`() {
        for (step in steps) {
            var lastEnd = Float.POSITIVE_INFINITY
            for (count in 53 downTo 2) {
                val a = angles(count, step)
                // The gap runs from the last card round to the top card, through 5 o'clock (60°, or 420° unwrapped).
                assertTrue(a.last() <= LAST + tolerance)
                assertTrue(a.first() + 360f > 60f + 360f)
                assertTrue("count $count", a.last() <= lastEnd + tolerance)
                lastEnd = a.last()
                // Never at the top: the loop reaches past 12 o'clock while it's long.
                if (count >= 30) assertTrue(a.last() > 270f + 30f)
            }
        }
    }

    @Test
    fun `a loop of none or one is fine`() {
        assertEquals(0, Ouroboros.loopAngles(0, 16f).size)
        assertEquals(BOTTOM - FIRST_STEP * 8f, Ouroboros.loopAngles(1, 16f).single(), tolerance)
    }

    @Test
    fun `the target comes in less the further round it is`() {
        assertEquals(TARGET_PULL, Ouroboros.targetPull(1), tolerance)
        assertEquals(TARGET_PULL, Ouroboros.targetPull(2), tolerance)
        assertEquals(TARGET_PULL / 2, Ouroboros.targetPull(10), tolerance)
        assertEquals(TARGET_PULL / 2, Ouroboros.targetPull(20), tolerance)
        val pulls = (1..12).map(Ouroboros::targetPull)
        assertTrue(pulls.zipWithNext().all { (a, b) -> b <= a })
    }

    // The rules.

    private fun card(rank: Rank, suit: Suit = Suit.Spades, up: Boolean = true) = LoopCard(Card(rank, suit), up)

    /** A loop of face-down Sevens, but for [cards] at their places. */
    private fun loop(size: Int, vararg cards: Pair<Int, LoopCard>): List<LoopCard> {
        val at = cards.toMap()
        return List(size) { at[it] ?: LoopCard(Card(Rank.Seven, Suit.entries[it % 4]), faceUp = false) }
    }

    @Test
    fun `every deal starts with a number card that can go somewhere`() {
        repeat(100) { seed ->
            val s = Ouroboros.newGame(Random(seed))
            assertEquals(53, s.deck.size)
            assertEquals(1, s.deck.count { it.isJoker })
            assertTrue(s.deck.none { it.faceUp })
            assertNull(Ouroboros.outcome(s))
            val up = Ouroboros.lookAround(s)!!
            assertTrue(Ouroboros.tops(up).any { Ouroboros.usable(up, it) })
        }
    }

    @Test
    fun `looking around turns up the top two, once`() {
        val s = OuroborosState(loop(10), pos = 9)
        // The top two wrap round the loop's end.
        assertEquals(listOf(9, 0), Ouroboros.tops(s))
        val up = Ouroboros.lookAround(s)!!
        assertTrue(up.deck[9].faceUp && up.deck[0].faceUp)
        assertEquals(8, up.deck.count { !it.faceUp })
        assertNull(Ouroboros.lookAround(up))
    }

    @Test
    fun `exploring counts on round the loop from the top card`() {
        val s = OuroborosState(loop(10, 0 to card(Rank.Three), 1 to card(Rank.Five)))
        assertEquals(3, Ouroboros.target(s, 0))
        assertEquals(5, Ouroboros.target(s, 1))
        val wrapped = OuroborosState(loop(10, 8 to card(Rank.Four), 9 to card(Rank.Nine)), pos = 8)
        assertEquals(2, Ouroboros.target(wrapped, 8))
        assertEquals(7, Ouroboros.target(wrapped, 9))
        // Landing face down just moves the top there.
        assertEquals(2, Ouroboros.explore(wrapped, 8)!!.pos)
        // Only the top two, face up, numbers.
        assertNull(Ouroboros.explore(s, 2))
        assertNull(Ouroboros.explore(OuroborosState(loop(10, 0 to card(Rank.Jack))), 0))
        assertNull(Ouroboros.explore(OuroborosState(loop(10, 0 to card(Rank.Three, up = false))), 0))
    }

    @Test
    fun `landing on a face up card collects it, or springs a trap`() {
        val trinket = OuroborosState(loop(10, 0 to card(Rank.Two), 2 to card(Rank.Six, Suit.Hearts)))
        val got = Ouroboros.explore(trinket, 0)!!
        assertEquals(9, got.deck.size)
        assertEquals(1, got.trinkets)
        // The next card takes its place as the top.
        assertEquals(2, got.pos)
        assertEquals(trinket.deck[3], got.deck[2])

        val jewel = Ouroboros.explore(OuroborosState(loop(10, 0 to card(Rank.Two), 2 to card(Rank.Ace, Suit.Hearts))), 0)!!
        assertEquals(1, jewel.aces)
        assertEquals(0, jewel.trinkets)

        val trap = Ouroboros.explore(OuroborosState(loop(10, 0 to card(Rank.Two), 2 to card(Rank.Queen))), 0)!!
        assertEquals(Ending.Trap, trap.ending)
        assertEquals(false, Ouroboros.outcome(trap)?.won)
        assertNull(Ouroboros.lookAround(trap))
    }

    @Test
    fun `the exit lets you out only with every jewel`() {
        val exit = 0 to card(Rank.Two)
        val joker = 2 to LoopCard(null, faceUp = true)
        val early = Ouroboros.explore(OuroborosState(loop(10, exit, joker), aces = 3), 0)!!
        assertNull(early.ending)
        assertEquals(2, early.pos)
        assertEquals(10, early.deck.size)
        val out = Ouroboros.explore(OuroborosState(loop(10, exit, joker), aces = 4), 0)!!
        assertEquals(Ending.Escaped, out.ending)
        assertEquals(true, Ouroboros.outcome(out)?.won)
    }

    @Test
    fun `a marked path puts its card back on top`() {
        val s = OuroborosState(loop(10, 4 to card(Rank.Two), 5 to card(Rank.Eight)), pos = 4)
        val marked = Ouroboros.mark(s, 5, 1)!!
        assertEquals(card(Rank.Eight), marked.paths[1])
        assertEquals(9, marked.deck.size)
        assertEquals(4, marked.pos)
        // A path holds one card.
        assertNull(Ouroboros.mark(marked, 4, 1))
        // Back on top of the loop, the path empty again.
        val back = Ouroboros.returnPath(marked, 1)!!
        assertEquals(card(Rank.Eight), back.deck[back.pos])
        assertTrue(back.paths.all { it == null })
        assertNull(Ouroboros.returnPath(back, 1))
    }

    @Test
    fun `stuck with no numbers on top and no path is lost`() {
        val stuck = OuroborosState(loop(10, 0 to card(Rank.King), 1 to card(Rank.Jack, up = false)))
        assertEquals(false, Ouroboros.outcome(stuck)?.won)
        // A marked path is a way on.
        assertNull(Ouroboros.outcome(stuck.copy(paths = listOf(card(Rank.Four), null, null))))
        // While the top card is face down, there's still a look to take.
        assertNull(Ouroboros.outcome(OuroborosState(loop(10, 0 to card(Rank.King, up = false), 1 to card(Rank.Jack)))))
    }

    @Test
    fun `loot is jewels and trinkets`() {
        assertEquals(5, Ouroboros.score(OuroborosState(loop(3), aces = 2, trinkets = 3)))
        assertFalse(Ouroboros.lowScoreWins)
    }
}
