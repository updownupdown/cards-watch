package com.updownupdown.cards.watch.games

import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle

/** Every game, in the order the home screen lists them. */
val ALL_GAMES: List<Game<*>> = listOf(
    FifteenPuzzle,
)

fun gameById(id: String?): Game<*>? = ALL_GAMES.firstOrNull { it.id == id }
