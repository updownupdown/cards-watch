package com.updownupdown.cards.watch.cards

import android.content.res.AssetManager
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/** An SVG's paths as one outline, in a square [viewBox] across. */
class CardIcon(val path: Path, val viewBox: Float)

/**
 * The rank, suit and back icons, read once from the SVGs in
 * `src/main/card-icons` (packed as assets). Only each path's outline is used:
 * the colours come from the theme.
 */
class CardIcons private constructor(assets: AssetManager) {
    private val ranks = Rank.entries.associateWith { assets.icon("numbers/${it.label}.svg") }
    private val suits = Suit.entries.associateWith { assets.icon("suits/${it.name}.svg") }
    val back = assets.icon("Back.svg")

    fun rank(rank: Rank) = ranks.getValue(rank)
    fun suit(suit: Suit) = suits.getValue(suit)

    companion object {
        @Volatile
        private var loaded: CardIcons? = null

        fun get(assets: AssetManager): CardIcons =
            loaded ?: synchronized(this) { loaded ?: CardIcons(assets).also { loaded = it } }
    }
}

private val VIEW_BOX = Regex("""viewBox="0 0 ([\d.]+)""")
private val PATH_DATA = Regex("""\sd="([^"]+)"""")

private fun AssetManager.icon(file: String): CardIcon {
    val svg = open(file).bufferedReader().use { it.readText() }
    val viewBox = VIEW_BOX.find(svg)?.groupValues?.get(1)?.toFloat() ?: error("No viewBox in $file")
    val data = PATH_DATA.findAll(svg).joinToString(" ") { it.groupValues[1] }
    return CardIcon(PathParser().parsePathString(data).toPath(), viewBox)
}
