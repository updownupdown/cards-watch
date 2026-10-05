package com.updownupdown.cards.watch.cards

import android.content.res.AssetManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser

/** An SVG's paths as one outline, in a square [viewBox] across. */
class CardIcon(val path: Path, val viewBox: Float) {
    /** Where the mark itself lies in that square, to line its edges up with a card's. */
    val bounds: Rect = path.getBounds()
}

/**
 * The rank, suit, back and Joker icons, read once from the SVGs in
 * `src/main/card-icons` (packed as assets). Only each path's outline is used:
 * the colours come from the theme.
 */
class CardIcons private constructor(assets: AssetManager) {
    private val ranks = Rank.entries.associateWith { assets.icon("numbers/${it.label}.svg") }
    private val suits = Suit.entries.associateWith { assets.icon("suits/${it.name}.svg") }
    val back = assets.icon("Back.svg")

    /** Jailbreak's prisoner, in the empty middle. */
    val joker = assets.icon("Joker.svg")

    val arrowUp = assets.icon("ArrowUp.svg")
    val arrowDown = assets.icon("ArrowDown.svg")

    /** Rogue Run's weapon and monster zone icons. */
    val sword = assets.icon("Sword.svg")
    val dragon = assets.icon("Dragon.svg")

    /** Ouroboros's mark on an empty marked path. */
    val map = assets.icon("Map.svg")

    fun rank(rank: Rank) = ranks.getValue(rank)
    fun suit(suit: Suit) = suits.getValue(suit)

    companion object {
        @Volatile
        private var loaded: CardIcons? = null

        fun get(assets: AssetManager): CardIcons =
            loaded ?: synchronized(this) { loaded ?: CardIcons(assets).also { loaded = it } }
    }
}

/** Draws [icon] in the middle of the area, its square [fraction] of the area's width across. */
fun DrawScope.drawCentredIcon(icon: CardIcon, fraction: Float, color: Color) {
    val side = size.width * fraction
    val scale = side / icon.viewBox
    withTransform({
        translate((size.width - side) / 2, (size.height - side) / 2)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(icon.path, color)
    }
}

private val VIEW_BOX = Regex("""viewBox="0 0 ([\d.]+)""")
private val PATH_DATA = Regex("""\sd="([^"]+)"""")

private fun AssetManager.icon(file: String): CardIcon {
    val svg = open(file).bufferedReader().use { it.readText() }
    val viewBox = VIEW_BOX.find(svg)?.groupValues?.get(1)?.toFloat() ?: error("No viewBox in $file")
    val data = PATH_DATA.findAll(svg).joinToString(" ") { it.groupValues[1] }
    val path = PathParser().parsePathString(data).toPath()
    if ("evenodd" in svg) path.fillType = PathFillType.EvenOdd
    return CardIcon(path, viewBox)
}
