package com.updownupdown.cards.watch.data

import com.updownupdown.cards.watch.game.Game

/** The home screen's Suit Colours: how cards colour their suits. */
enum class SuitColours(val label: String) {
    /** Four where the game's suits matter, two where only colours or neither do ([Game.fourSuitColours]). */
    Auto("Auto"),
    /** Red and white, as on a paper deck. */
    Two("Two"),
    /** A colour for each suit. */
    Four("Four");

    fun fourIn(game: Game<*>): Boolean = when (this) {
        Two -> false
        Four -> true
        Auto -> game.fourSuitColours
    }
}

/** The choices for how many games Continue Playing shows; 0 hides it. */
val CONTINUE_CHOICES = listOf(0, 1, 3)
