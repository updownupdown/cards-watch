package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.accordion.Accordion
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.baroness.Baroness
import com.updownupdown.cards.watch.games.beehive.Beehive
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.dominion.Dominion
import com.updownupdown.cards.watch.games.ebbandflow.EbbAndFlow
import com.updownupdown.cards.watch.games.elevens.Elevens
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.games.fourseasons.FourSeasons
import com.updownupdown.cards.watch.games.hanoi.TowerOfHanoi
import com.updownupdown.cards.watch.games.irisgarden.IrisGarden
import com.updownupdown.cards.watch.games.jailbreak.Jailbreak
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.memory.Memory
import com.updownupdown.cards.watch.games.nestor.Nestor
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.royalmarriage.RoyalMarriage
import com.updownupdown.cards.watch.games.seer.Seer
import com.updownupdown.cards.watch.games.tentwentythirty.TenTwentyThirty
import com.updownupdown.cards.watch.games.triplestack.TripleStack

/** Every game, in the order the home screen lists them. */
val ALL_GAMES: List<Game<*>> = listOf(
    FifteenPuzzle,
    Memory,
    LightsOut,
    TowerOfHanoi,
    Clock,
    FortunesCall,
    AcesUp,
    Baroness,
    Beehive,
    TripleStack,
    Dominion,
    RogueRun,
    Seer,
    Lycan,
    EbbAndFlow,
    FourSeasons,
    Nestor,
    Jailbreak,
    Accordion,
    Elevens,
    TenTwentyThirty,
    IrisGarden,
    RoyalMarriage,
)

fun gameById(id: String?): Game<*>? = ALL_GAMES.firstOrNull { it.id == id }
