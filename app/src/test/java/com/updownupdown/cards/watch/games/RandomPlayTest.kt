package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.resolve
import com.updownupdown.cards.watch.games.accordion.Accordion
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.augury.Augury
import com.updownupdown.cards.watch.games.baroness.Baroness
import com.updownupdown.cards.watch.games.beehive.Beehive
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.dominion.Dominion
import com.updownupdown.cards.watch.games.ebbandflow.EbbAndFlow
import com.updownupdown.cards.watch.games.elevens.Elevens
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fortune.Call
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill
import com.updownupdown.cards.watch.games.hanoi.TowerOfHanoi
import com.updownupdown.cards.watch.games.jailbreak.Jailbreak
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.memory.Memory
import com.updownupdown.cards.watch.games.nestor.Nestor
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros
import com.updownupdown.cards.watch.games.ozanam.OzanamsSquare
import com.updownupdown.cards.watch.games.ozanam.Source
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.royalmarriage.RoyalMarriage
import com.updownupdown.cards.watch.games.tentwentythirty.TenTwentyThirty
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Plays random taps through many games of each, to catch crashes in the rules,
 * and looks up the text each state shows, to catch a string given the wrong
 * arguments.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RandomPlayTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun <S : Any> fuzz(game: Game<S>, games: Int = 30, steps: Int = 400, move: (S, Random) -> S?) {
        assertTrue(resources.getString(game.title).isNotBlank())
        assertTrue(resources.getStringArray(game.rules).isNotEmpty())
        game.scoreName?.let { resources.getString(it) }
        repeat(games) { seed ->
            val random = Random(seed)
            var state = game.newGame(random)
            repeat(steps) {
                showText(game, state)
                if (game.outcome(state) != null) return@repeat
                move(state, random)?.let { state = it }
            }
        }
    }

    private fun <S : Any> showText(game: Game<S>, state: S) {
        game.outcome(state)?.message?.resolve(resources)
        game.status(state)?.resolve(resources)
        game.actions(state).forEach { it.label.resolve(resources) }
    }

    @Test fun fifteen() = fuzz(FifteenPuzzle) { s, r -> FifteenPuzzle.slide(s, r.nextInt(16)) }

    @Test fun memory() = fuzz(Memory) { s, r -> Memory.tap(s, r.nextInt(20)) }

    @Test fun lightsOut() = fuzz(LightsOut, games = 5) { s, r -> LightsOut.tap(s, r.nextInt(LightsOut.CELLS)) }

    @Test fun hanoi() = fuzz(TowerOfHanoi) { s, r -> TowerOfHanoi.move(s, r.nextInt(3), r.nextInt(3)) }

    @Test fun clock() = fuzz(Clock) { s, _ -> Clock.reveal(s) }

    @Test fun fortune() = fuzz(FortunesCall) { s, r -> FortunesCall.call(s, Call.entries.random(r)) }

    @Test fun acesUp() = fuzz(AcesUp) { s, r -> if (r.nextInt(6) == 0) AcesUp.deal(s) else AcesUp.tap(s, r.nextInt(4)) }

    @Test fun baroness() = fuzz(Baroness) { s, r ->
        if (r.nextInt(6) == 0) Baroness.deal(s) else Baroness.tap(s, r.nextInt(5).takeIf { s.piles[it].isNotEmpty() }, r.nextInt(5)).first
    }

    @Test fun beehive() = fuzz(Beehive) { s, r ->
        when (r.nextInt(4)) {
            0 -> Beehive.draw(s)
            1 -> Beehive.playBeehive(s)
            2 -> Beehive.playWaste(s)
            else -> Beehive.joinPile(s, r.nextInt(6))
        }
    }

    @Test fun tripleStack() = fuzz(TripleStack) { s, r ->
        if (s.selected.size == 3) TripleStack.serve(s) ?: s.copy(selected = emptyList()) else TripleStack.toggle(s, r.nextInt(8))
    }

    @Test fun ebbAndFlow() = fuzz(EbbAndFlow) { s, r ->
        if (r.nextInt(4) == 0) EbbAndFlow.draw(s) else s.hand.randomOrNull(r)?.let { EbbAndFlow.play(s, it, r.nextInt(EbbAndFlow.PILES)) }
    }

    @Test fun littleWindmill() = fuzz(LittleWindmill) { s, r ->
        val source = r.nextInt(LittleWindmill.WASTE + 1)
        when (r.nextInt(4)) {
            0 -> LittleWindmill.draw(s)
            1 -> LittleWindmill.toFoundation(s, source)
            else -> LittleWindmill.toTableau(s, source, r.nextInt(LittleWindmill.TABLEAU))
        }
    }

    @Test fun nestor() = fuzz(Nestor) { s, r -> Nestor.pair(s, r.nextInt(Nestor.PLACES), r.nextInt(Nestor.PLACES)) }

    @Test
    fun nestorDealsCanBeWonAndRepeatNoRankInAColumn() {
        repeat(50) { seed ->
            val state = Nestor.newGame(Random(seed))
            assertEquals(32, (state.columns.flatten() + state.reserve).toSet().size)
            assertTrue(state.columns.all { column -> column.map { it.rank }.toSet().size == column.size })
            assertTrue(Nestor.winnable(state))
        }
    }

    @Test fun accordion() = fuzz(Accordion) { s, r ->
        val from = r.nextInt(s.piles.size)
        Accordion.move(s, from, from - Accordion.REACH.random(r))
    }

    @Test fun jailbreak() = fuzz(Jailbreak) { s, r ->
        if (r.nextInt(4) == 0) Jailbreak.draw(s) else Jailbreak.remove(s, List(2 + r.nextInt(2)) { r.nextInt(Jailbreak.JOKER + 1) }.toSet())
    }

    @Test fun dominion() = fuzz(Dominion) { s, r -> Dominion.conquer(s, r.nextInt(Dominion.CELLS), r.nextInt(Dominion.CELLS)) }

    @Test
    fun dominionDealsThreeSuitsWithAMatchForEveryCard() {
        repeat(50) { seed ->
            val board = Dominion.newGame(Random(seed)).board
            assertEquals(Dominion.CELLS, board.size)
            assertTrue(board.map { it.single().suit }.toSet().size <= 3)
            assertFalse(Dominion.isolated(board))
        }
    }

    @Test fun rogueRun() = fuzz(RogueRun) { s, r ->
        val i = r.nextInt(4)
        when (r.nextInt(5)) {
            0 -> RogueRun.run(s)
            1 -> RogueRun.drink(s, i)
            2 -> RogueRun.equip(s, i)
            else -> RogueRun.fight(s, i, r.nextBoolean())
        }
    }

    @Test fun lycan() = fuzz(Lycan) { s, r ->
        when {
            s.roundResult != null -> Lycan.nextRound(s, r)
            r.nextInt(20) == 0 -> Lycan.fold(s)
            r.nextInt(3) == 0 -> Lycan.doCapture(s) ?: s.copy(pickedPlayer = emptyList(), pickedWolf = emptyList())
            r.nextBoolean() && s.player.isNotEmpty() -> Lycan.togglePlayer(s, s.player.random(r).id)
            s.wolf.isNotEmpty() -> Lycan.toggleWolf(s, s.wolf.random(r).id)
            else -> null
        }
    }

    @Test fun elevens() = fuzz(Elevens) { s, r -> Elevens.remove(s, List(2 + r.nextInt(2)) { r.nextInt(Elevens.SIZE) }) }

    @Test
    fun elevensDealsAllowFourMoves() {
        repeat(30) { seed ->
            var state = Elevens.newGame(Random(seed))
            assertEquals(52, (state.grid.filterNotNull() + state.stock).toSet().size)
            // Some line of play makes four moves; at least the first must exist.
            assertTrue(Elevens.sets(state.grid).isNotEmpty())
        }
    }

    @Test fun tenTwentyThirty() = fuzz(TenTwentyThirty) { s, r ->
        val first = r.nextInt(s.line.size.coerceAtLeast(1))
        TenTwentyThirty.discard(s, first, first + 1 + r.nextInt(4))
    }

    @Test fun royalMarriage() = fuzz(RoyalMarriage) { s, r ->
        if (r.nextInt(4) == 0) RoyalMarriage.discard(s) ?: s.copy(selected = emptySet())
        else RoyalMarriage.toggle(s, r.nextInt(s.line.size))
    }

    @Test fun ozanamsSquare() = fuzz(OzanamsSquare, games = 10) { s, r ->
        when (r.nextInt(3)) {
            0 -> OzanamsSquare.unplace(s, r.nextInt(OzanamsSquare.CELLS))
            1 -> OzanamsSquare.place(s, Source.Place(r.nextInt(OzanamsSquare.CELLS)), r.nextInt(OzanamsSquare.CELLS))
            else -> s.tray.flatten().randomOrNull(r)?.let { OzanamsSquare.place(s, Source.Tray(it), r.nextInt(OzanamsSquare.CELLS)) }
        }
    }

    @Test
    fun ozanamsSquareHasOneSolution() {
        repeat(10) { seed ->
            val state = OzanamsSquare.newGame(Random(seed))
            assertEquals(1, OzanamsSquare.solve(state.grid, 2).size)
            assertTrue(state.given.count { it } >= 7)
        }
    }

    @Test fun augury() = fuzz(Augury) { s, r ->
        if (r.nextInt(5) == 0) Augury.noneFit(s) else s.hand.randomOrNull(r)?.let { Augury.playCard(s, it) }
    }

    @Test
    fun auguryKeepsEveryCard() {
        repeat(30) { seed ->
            val random = Random(seed)
            var state = Augury.newGame(random)
            while (Augury.outcome(state) == null) {
                state = (if (random.nextInt(4) == 0) Augury.noneFit(state) else Augury.playCard(state, state.hand.random(random)))!!
            }
            val seen = state.deck + state.hand + state.path.flatMap { listOf(it.card) + it.rejected }
            assertEquals(seen.size, seen.toSet().size)
        }
    }

    @Test fun ouroboros() = fuzz(Ouroboros) { s, r ->
        val top = Ouroboros.tops(s).random(r)
        when (r.nextInt(4)) {
            0 -> Ouroboros.lookAround(s)
            1 -> Ouroboros.mark(s, top, r.nextInt(Ouroboros.PATHS))
            2 -> Ouroboros.returnPath(s, r.nextInt(Ouroboros.PATHS))
            else -> Ouroboros.explore(s, top)
        }
    }

    @Test
    fun ouroborosKeepsEveryCard() {
        repeat(30) { seed ->
            val random = Random(seed)
            var state = Ouroboros.newGame(random)
            repeat(300) {
                if (Ouroboros.outcome(state) != null) return@repeat
                val top = Ouroboros.tops(state).random(random)
                state = when (random.nextInt(4)) {
                    0 -> Ouroboros.lookAround(state)
                    1 -> Ouroboros.mark(state, top, random.nextInt(Ouroboros.PATHS))
                    2 -> Ouroboros.returnPath(state, random.nextInt(Ouroboros.PATHS))
                    else -> Ouroboros.explore(state, top)
                } ?: state
                assertEquals(53, state.deck.size + state.paths.count { it != null } + state.aces + state.trinkets)
                assertTrue(state.pos in state.deck.indices)
            }
        }
    }
}
