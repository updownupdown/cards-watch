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

/** The cards' colours, all set in `ui/Theme.kt`. */
object CardColors {
    val Face get() = AppColors.CardFace
    val Back get() = AppColors.CardBack
    val BackLogo get() = AppColors.CardBackLogo
    val BackBorder get() = AppColors.CardBackBorder
    val EmptySlot get() = AppColors.CardEmptySlot
    /** The line round a place for several cards. */
    val Zone get() = AppColors.Track
    /** The line round a card's warning dot. */
    val DotEdge get() = Color.Black
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
    // The colours of the game buttons, for a selection that says what its action will be.
    Green(AppColors.ButtonGreen),
    Purple(AppColors.ButtonPurple),
    Pink(AppColors.ButtonPink),
    Orange(AppColors.ButtonOrange),
    Red(AppColors.ButtonRed),
    Blue(AppColors.ButtonBlue),
}

/**
 * A playing card filling its bounds (which should be [CARD_ASPECT]): the rank
 * over the suit, both in the middle. Every card in every game is drawn this way; [CardStyle]
 * sets the sizes.
 *
 * The layout is worked out once per size and kept, so redrawing is cheap.
 *
 * @param card the card to show, or null for an empty slot.
 * @param overlay a status colour (see [CardOverlay]) tinting the card.
 * @param under how far (0 to 1) the card is shaded as the one under a pile's
 *   top card. Read while drawing, so it can animate.
 * @param dot the colour of a warning dot in the top right corner of a face-up card.
 */
@Composable
fun PlayingCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    overlay: Color? = null,
    selection: Selection? = null,
    under: () -> Float = { 0f },
    dot: Color? = null,
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
                else -> face(card, icons, ink)
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
                if (card != null && !faceDown && dot != null) {
                    val radius = size.width * CardStyle.DOT_RADIUS
                    val middle = Offset(size.width * (1 - CardStyle.DOT_INSET), size.width * CardStyle.DOT_INSET)
                    drawCircle(CardColors.DotEdge, radius + size.width * CardStyle.DOT_EDGE, middle)
                    drawCircle(dot, radius, middle)
                }
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

/** The rank's mark [CardStyle.FACE_PAD_RANK] down from the top and the suit's [CardStyle.FACE_PAD_SUIT] up from the bottom, both in the middle. */
private fun CacheDrawScope.face(card: Card, icons: CardIcons, color: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val padRank = w * CardStyle.FACE_PAD_RANK
    val padSuit = w * CardStyle.FACE_PAD_SUIT
    val rank = icons.rank(card.rank)
    val suit = icons.suit(card.suit)
    val rankScale = w * CardStyle.RANK_SIZE / rank.viewBox
    val suitScale = w * CardStyle.SUIT_SIZE / suit.viewBox
    return {
        drawMark(rank, w / 2 - rank.bounds.center.x * rankScale, padRank - rank.bounds.top * rankScale, rankScale, color)
        drawMark(suit, w / 2 - suit.bounds.center.x * suitScale, h - padSuit - suit.bounds.bottom * suitScale, suitScale, color)
    }
}

/** Draws [icon] at [scale], its square's corner at ([x], [y]). */
private fun DrawScope.drawMark(icon: CardIcon, x: Float, y: Float, scale: Float, color: Color) {
    withTransform({
        translate(x, y)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(icon.path, color)
    }
}
