package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.baroness.Baroness
import com.updownupdown.cards.watch.games.beehive.Beehive
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.dominion.Dominion
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fortune.Call
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.games.hanoi.TowerOfHanoi
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.memory.Memory
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.seer.Seer
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import org.junit.Test
import kotlin.random.Random

/** Plays random taps through many games of each, to catch crashes in the rules. */
class RandomPlayTest {
    private fun <S : Any> fuzz(game: Game<S>, games: Int = 30, steps: Int = 400, move: (S, Random) -> S?) {
        repeat(games) { seed ->
            val random = Random(seed)
            var state = game.newGame(random)
            repeat(steps) {
                if (game.outcome(state) != null) return@repeat
                move(state, random)?.let { state = it }
            }
        }
    }

    @Test fun fifteen() = fuzz(FifteenPuzzle) { s, r -> FifteenPuzzle.slide(s, r.nextInt(16)) }

    @Test fun memory() = fuzz(Memory) { s, r -> Memory.tap(s, r.nextInt(20)) }

    @Test fun lightsOut() = fuzz(LightsOut, games = 5) { s, r -> LightsOut.tap(s, r.nextInt(25)) }

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

    @Test fun dominion() = fuzz(Dominion) { s, r ->
        when {
            s.drawn != null -> Dominion.place(s, r.nextInt(12))
            r.nextInt(5) == 0 -> Dominion.drawReserve(s)
            else -> Dominion.conquer(s, r.nextInt(12), r.nextInt(12))
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

    @Test fun seer() = fuzz(Seer) { s, r ->
        if (s.freed.isNotEmpty()) Seer.dispose(s, r.nextBoolean()) else Seer.predict(s, r.nextInt(8))
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
}
