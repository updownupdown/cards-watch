package com.updownupdown.cards.watch.data

import androidx.annotation.StringRes
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game

/** The home screen's Suit Colours: how cards colour their suits. */
enum class SuitColours(@param:StringRes val label: Int) {
    /** Four where the game's suits matter, two where only colours or neither do ([Game.fourSuitColours]). */
    Auto(R.string.suit_colours_auto),
    /** Red and white, as on a paper deck. */
    Two(R.string.suit_colours_two),
    /** A colour for each suit. */
    Four(R.string.suit_colours_four);

    fun fourIn(game: Game<*>): Boolean = when (this) {
        Two -> false
        Four -> true
        Auto -> game.fourSuitColours
    }
}

/** The choices for how many games Continue Playing shows; 0 hides it. */
val CONTINUE_CHOICES = listOf(0, 1, 3)
