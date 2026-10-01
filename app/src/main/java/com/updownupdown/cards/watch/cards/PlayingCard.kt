package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.LocalAmbient
import kotlin.math.min

/** The cards' colours, all set in `ui/Theme.kt`. */
object CardColors {
    val Face get() = AppColors.CardFace
    val Back get() = AppColors.CardBack
    val BackLogo get() = AppColors.CardBackLogo
    val BackBorder get() = AppColors.CardBackBorder
    val EmptySlot get() = AppColors.CardEmptySlot
    val Edge get() = AppColors.CardEdge
    val Under get() = AppColors.CardUnderShade
    /** Everything on a card while the watch is asleep. */
    val Ambient get() = AppColors.Ambient

    fun suit(suit: Suit) = when (suit) {
        Suit.Hearts -> AppColors.Hearts
        Suit.Diamonds -> AppColors.Diamonds
        Suit.Clubs -> AppColors.Clubs
        Suit.Spades -> AppColors.Spades
    }
}

/** Status colours that tint a card's face (the rank and suit stay on top). */
object CardOverlay {
    val Good = AppColors.Good.copy(alpha = 0.3f)
    val Bad = AppColors.Bad.copy(alpha = 0.3f)
    val Hint = AppColors.PaleYellow.copy(alpha = 0.3f)
    /** A place the picked card can go. */
    val Target = AppColors.Neutral.copy(alpha = 0.3f)
}

/**
 * A selected card: a border centred on its edge and a 30% tint, blue while neutral,
 * green or red once the selection is known to be valid or not.
 */
enum class Selection(val color: Color) {
    Neutral(AppColors.Neutral),
    Valid(AppColors.Good),
    Invalid(AppColors.Bad),
}

/**
 * A playing card filling its bounds (which should be [CARD_ASPECT]): the rank
 * above the suit, or with [indexHeight], the rank and suit along the top edge
 * (so they show when cards overlap) and a large suit below, or with
 * [indexWidth], both down the left edge (for cards overlapping sideways). [CardStyle] sets
 * the sizes.
 *
 * The layout is worked out once per size and kept, so redrawing is cheap.
 *
 * @param card the card to show, or null for an empty slot.
 * @param overlay a status colour (see [CardOverlay]) tinting the card.
 * @param under how far (0 to 1) the card is shaded as the one under a pile's
 *   top card. Read while drawing, so it can animate.
 */
@Composable
fun PlayingCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    overlay: Color? = null,
    selection: Selection? = null,
    indexHeight: Float? = null,
    indexWidth: Float? = null,
    under: () -> Float = { 0f },
) {
    val icons = CardIcons.get(LocalContext.current.assets)
    val ambient = LocalAmbient.current
    Spacer(
        modifier.drawWithCache {
            val corner = CornerRadius(size.width * CardStyle.CORNER)
            val ink = if (ambient || card == null) CardColors.Ambient else CardColors.suit(card.suit)
            val face = when {
                card == null -> null
                faceDown -> backFace(icons, ambient)
                indexHeight != null -> indexFace(card, icons, indexHeight, ink)
                indexWidth != null -> sideFace(card, icons, indexWidth, ink)
                else -> centreFace(card, icons, ink)
            }
            if (ambient) {
                // Asleep: a grey outline and the card's marks, on black so cards underneath don't show through.
                val line = Stroke(density)
                return@drawWithCache onDrawBehind {
                    if (card != null) drawRoundRect(Color.Black, cornerRadius = corner)
                    drawRoundRect(CardColors.Ambient.copy(alpha = if (card == null) 0.3f else 0.7f), cornerRadius = corner, style = line)
                    face?.invoke(this)
                }
            }
            val edge = CardStyle.EDGE_DP * density
            val selected = Stroke(CardStyle.SELECTION_DP * density)
            val fill = when {
                card == null -> CardColors.EmptySlot
                faceDown -> CardColors.Back
                else -> CardColors.Face
            }
            onDrawBehind {
                // A dark edge just outside the card.
                if (card != null && selection == null) {
                    drawRoundRect(
                        color = CardColors.Edge,
                        topLeft = Offset(-edge / 2, -edge / 2),
                        size = Size(size.width + edge, size.height + edge),
                        cornerRadius = CornerRadius(corner.x + edge / 2),
                        style = Stroke(edge),
                    )
                }
                drawRoundRect(fill, cornerRadius = corner)
                overlay?.let { drawRoundRect(it, cornerRadius = corner) }
                if (card != null) selection?.let { drawRoundRect(it.color.copy(alpha = 0.3f), cornerRadius = corner) }
                face?.invoke(this)
                val shade = if (card != null) under() else 0f
                if (shade > 0f) {
                    drawRoundRect(CardColors.Under.copy(alpha = CardColors.Under.alpha * shade), cornerRadius = corner)
                }
                // The selection border, centred on the card's edge.
                if (card != null && selection != null) drawRoundRect(selection.color, cornerRadius = corner, style = selected)
            }
        },
    )
}

/** Draws [icon] in the square of side [side] centred on ([x], [y]). */
private fun DrawScope.drawIcon(icon: CardIcon, x: Float, y: Float, side: Float, color: Color) {
    val scale = side / icon.viewBox
    withTransform({
        translate(x - side / 2, y - side / 2)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(icon.path, color)
    }
}

/** The logo, centred, inside a line set in from the card's edge. */
private fun CacheDrawScope.backFace(icons: CardIcons, ambient: Boolean): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val inset = w * CardStyle.BACK_BORDER_INSET
    val line = Stroke(w * CardStyle.BACK_BORDER_WIDTH)
    val lineCorner = CornerRadius(w * CardStyle.BACK_BORDER_CORNER)
    // Asleep, just the logo, faintly.
    if (ambient) return { drawIcon(icons.back, w / 2, h / 2, w * CardStyle.BACK_LOGO_SIZE, CardColors.Ambient.copy(alpha = 0.5f)) }
    return {
        drawRoundRect(
            color = CardColors.BackBorder,
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = lineCorner,
            style = line,
        )
        drawIcon(icons.back, w / 2, h / 2, w * CardStyle.BACK_LOGO_SIZE, CardColors.BackLogo)
    }
}

/** The rank above the suit, centred. */
private fun CacheDrawScope.centreFace(card: Card, icons: CardIcons, color: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val rank = icons.rank(card.rank)
    val suit = icons.suit(card.suit)
    return {
        drawIcon(rank, w / 2, h * CardStyle.RANK_Y, w * CardStyle.RANK_SIZE, color)
        drawIcon(suit, w / 2, h * CardStyle.SUIT_Y, w * CardStyle.SUIT_SIZE, color)
    }
}

/** The rank above the suit, in the strip down the card's left. */
private fun CacheDrawScope.sideFace(card: Card, icons: CardIcons, indexWidth: Float, color: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val rank = icons.rank(card.rank)
    val suit = icons.suit(card.suit)
    val strip = w * indexWidth
    val scale = min(1f, strip * 0.9f / (w * CardStyle.SUIT_SIZE))
    return {
        drawIcon(rank, strip / 2, h * CardStyle.RANK_Y, w * CardStyle.RANK_SIZE * scale, color)
        drawIcon(suit, strip / 2, h * CardStyle.SUIT_Y, w * CardStyle.SUIT_SIZE * scale, color)
    }
}

/** The rank and a small suit in the top strip, a large suit below. */
private fun CacheDrawScope.indexFace(card: Card, icons: CardIcons, indexHeight: Float, color: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val rank = icons.rank(card.rank)
    val suit = icons.suit(card.suit)
    val strip = h * indexHeight
    val small = min(strip * CardStyle.INDEX_SIZE, w * CardStyle.INDEX_MAX_SIZE)
    val spread = small * CardStyle.INDEX_SPREAD
    val stripY = strip / 2 + h * 0.01f
    val body = h - strip
    val big = min(w * CardStyle.SUIT_SIZE, body * 0.6f)
    return {
        drawIcon(rank, w / 2 - spread, stripY, small, color)
        drawIcon(suit, w / 2 + spread, stripY, small, color)
        if (big > strip * 0.8f) drawIcon(suit, w / 2, strip + body / 2, big, color)
    }
}
