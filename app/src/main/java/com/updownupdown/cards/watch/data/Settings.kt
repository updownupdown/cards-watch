package com.updownupdown.cards.watch.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
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

/**
 * The Settings screen's Colour Theme: nine of the phone's colour themes, by
 * their accent (src/styles/themes.ts there). On the watch a theme sets one
 * colour, the app's own accent: the logo, screen titles' icons, the stats'
 * totals, switches and choices, and the tile's subtitles. Backgrounds stay
 * black and each game keeps its own colours. Saved by name.
 */
enum class ColourTheme(@param:StringRes val label: Int, val accent: Color) {
    Nocturne(R.string.theme_nocturne, Color(0xFFFFCF87)),
    Folio(R.string.theme_folio, Color(0xFFE0674B)),
    Blossom(R.string.theme_blossom, Color(0xFFFF9ECB)),
    Verdigris(R.string.theme_verdigris, Color(0xFF72D8C4)),
    Ember(R.string.theme_ember, Color(0xFFFF9A5A)),
    Moss(R.string.theme_moss, Color(0xFFA8D46F)),
    CardRoom(R.string.theme_card_room, Color(0xFFE23B4C)),
    Sand(R.string.theme_sand, Color(0xFFE6CF9F)),
    Rosewood(R.string.theme_rosewood, Color(0xFFD98F8F)),
}
