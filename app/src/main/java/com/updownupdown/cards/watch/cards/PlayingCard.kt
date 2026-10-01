package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** Every card is 25:35, height over width. Cards only ever scale. */
const val CARD_ASPECT = 35f / 25f

object CardColors {
    /** Pale beige faces. */
    val Face = Color(0xFFF6EED8)
    val Back = Color(0xFF6E2C37)
    val BackTrim = Color(0xFFA0586A)
    /** An empty pile or space: off-black, so it shows against the black screen. */
    val EmptySlot = Color(0xFF22252C)
    /** The thin dark edge around every card, so overlapping cards stand apart. */
    val Edge = Color(0xB3000000)
    /** Darkens the card under the top card of a pile. */
    val Under = Color(0x47000000)

    // A four-colour deck, in deep, muted inks.
    fun suit(suit: Suit) = when (suit) {
        Suit.Hearts -> Color(0xFFA8333E)
        Suit.Diamonds -> Color(0xFF2C5A8C)
        Suit.Clubs -> Color(0xFF2F6A45)
        Suit.Spades -> Color(0xFF23262D)
    }
}

/** Status colours that tint a card's face (the rank and suit stay on top). */
object CardOverlay {
    val Good = Color(0x4D78EBB1)
    val Bad = Color(0x4DE0485C)
    val Hint = Color(0x4DFFCF87)
}

/**
 * A selected card: an outside border and a 30% tint, blue while neutral,
 * green or red once the selection is known to be valid or not.
 */
enum class Selection(val color: Color) {
    Neutral(Color(0xFF7BD5FF)),
    Valid(Color(0xFF78EBB1)),
    Invalid(Color(0xFFE0485C)),
}

/** One text measurer for every card, so cards don't each keep a cache. */
val LocalCardText = staticCompositionLocalOf<TextMeasurer> { error("No card text measurer") }

/**
 * A playing card filling its bounds (which should be [CARD_ASPECT]): the rank
 * above the suit, or with [indexHeight], the rank and suit along the top edge
 * (so they show when cards overlap) and a large suit below.
 *
 * Shapes and text are measured once per size and kept, so redrawing is cheap.
 *
 * @param card the card to show, or null for an empty slot.
 * @param overlay a status colour (see [CardOverlay]) tinting the card.
 * @param under darkens the card, for the one under a pile's top card.
 */
@Composable
fun PlayingCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    overlay: Color? = null,
    selection: Selection? = null,
    indexHeight: Float? = null,
    under: Boolean = false,
) {
    val textMeasurer = LocalCardText.current
    Spacer(
        modifier.drawWithCache {
            val short = min(size.width, size.height)
            val corner = CornerRadius(short * 0.13f)
            val face = if (card != null && !faceDown) {
                if (indexHeight != null) indexFace(card, textMeasurer, indexHeight) else centreFace(card, textMeasurer)
            } else {
                null
            }
            val width = if (selection != null) 1.6f * density else 1f * density
            val border = selection?.color ?: if (card != null) CardColors.Edge else null
            onDrawBehind {
                // A thin edge, or the selection border, just outside the card.
                if (border != null) {
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(-width / 2, -width / 2),
                        size = Size(size.width + width, size.height + width),
                        cornerRadius = CornerRadius(corner.x + width / 2),
                        style = Stroke(width),
                    )
                }
                when {
                    card == null -> drawRoundRect(CardColors.EmptySlot, cornerRadius = corner)
                    faceDown -> drawBack(corner, short)
                    else -> {
                        drawRoundRect(CardColors.Face, cornerRadius = corner)
                        overlay?.let { drawRoundRect(it, cornerRadius = corner) }
                        selection?.let { drawRoundRect(it.color.copy(alpha = 0.3f), cornerRadius = corner) }
                        face?.invoke(this)
                    }
                }
                if (card != null && faceDown) {
                    overlay?.let { drawRoundRect(it, cornerRadius = corner) }
                    selection?.let { drawRoundRect(it.color.copy(alpha = 0.3f), cornerRadius = corner) }
                }
                if (card != null && under) drawRoundRect(CardColors.Under, cornerRadius = corner)
            }
        },
    )
}

private fun DrawScope.drawBack(corner: CornerRadius, short: Float) {
    drawRoundRect(CardColors.Back, cornerRadius = corner)
    val inset = short * 0.14f
    drawRoundRect(
        color = CardColors.BackTrim,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(corner.x * 0.6f),
        style = Stroke(width = short * 0.05f),
    )
}

private fun Density.rankLayout(card: Card, measurer: TextMeasurer, fontPx: Float, maxWidth: Float): TextLayoutResult {
    val color = CardColors.suit(card.suit)
    var px = fontPx
    var layout = measurer.measure(card.rank.label, rankStyle(px, color))
    if (layout.size.width > maxWidth) {
        px *= maxWidth / layout.size.width
        layout = measurer.measure(card.rank.label, rankStyle(px, color))
    }
    return layout
}

private fun Density.rankStyle(fontPx: Float, color: Color) = TextStyle(
    color = color,
    // Sized in pixels, so the system font size doesn't overflow the card.
    fontSize = (fontPx / density / fontScale).sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.04).em,
)

/** The rank above the suit, centred, measured for this size. */
private fun CacheDrawScope.centreFace(card: Card, measurer: TextMeasurer): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val color = CardColors.suit(card.suit)
    val layout = rankLayout(card, measurer, min(h * 0.44f, w * 0.62f), w * 0.9f)
    val rankAt = Offset((w - layout.size.width) / 2, h * 0.34f - layout.size.height / 2)
    val suitSize = min(w * 0.46f, h * 0.31f)
    val suit = suitPath(card.suit, Offset((w - suitSize) / 2, h * 0.72f - suitSize / 2), suitSize)
    return {
        drawText(layout, topLeft = rankAt)
        drawPath(suit, color)
    }
}

/** The rank and a small suit in the top strip, a large suit below. */
private fun CacheDrawScope.indexFace(card: Card, measurer: TextMeasurer, indexHeight: Float): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val color = CardColors.suit(card.suit)
    val strip = h * indexHeight
    val pad = w * 0.08f
    val layout = rankLayout(card, measurer, strip * 1.05f, w * 0.58f)
    val smallSuit = min(strip * 0.7f, w - pad * 3 - layout.size.width)
    val rowWidth = layout.size.width + pad * 0.5f + smallSuit
    val left = (w - rowWidth) / 2
    val rankAt = Offset(left, strip / 2 - layout.size.height / 2 + h * 0.01f)
    val small = suitPath(card.suit, Offset(left + layout.size.width + pad * 0.5f, (strip - smallSuit) / 2 + h * 0.01f), smallSuit)
    val body = h - strip
    val bigSize = min(w * 0.55f, body * 0.6f)
    val big = if (bigSize > strip * 0.8f) suitPath(card.suit, Offset((w - bigSize) / 2, strip + (body - bigSize) / 2), bigSize) else null
    return {
        drawText(layout, topLeft = rankAt)
        drawPath(small, color)
        big?.let { drawPath(it, color) }
    }
}

/** A suit symbol in the square at [topLeft] with side [s], as one path. */
fun suitPath(suit: Suit, topLeft: Offset, s: Float): Path {
    val pieces = mutableListOf<Path>()
    fun p(x: Float, y: Float) = Offset(topLeft.x + x * s, topLeft.y + y * s)
    fun polygon(vararg points: Pair<Float, Float>) {
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            val o = p(x, y)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        pieces += path
    }
    fun circle(x: Float, y: Float, r: Float) {
        pieces += Path().apply { addOval(Rect(p(x, y), r * s)) }
    }
    when (suit) {
        Suit.Diamonds -> polygon(0.5f to 0f, 0.88f to 0.5f, 0.5f to 1f, 0.12f to 0.5f)
        Suit.Hearts -> {
            circle(0.27f, 0.32f, 0.23f)
            circle(0.73f, 0.32f, 0.23f)
            polygon(0.045f to 0.38f, 0.5f to 0.32f, 0.955f to 0.38f, 0.5f to 0.96f)
        }
        Suit.Spades -> {
            circle(0.27f, 0.58f, 0.23f)
            circle(0.73f, 0.58f, 0.23f)
            polygon(0.048f to 0.52f, 0.5f to 0.02f, 0.952f to 0.52f, 0.5f to 0.6f)
            polygon(0.5f to 0.6f, 0.34f to 1f, 0.66f to 1f)
        }
        Suit.Clubs -> {
            circle(0.5f, 0.27f, 0.21f)
            circle(0.27f, 0.6f, 0.21f)
            circle(0.73f, 0.6f, 0.21f)
            circle(0.5f, 0.55f, 0.12f)
            polygon(0.5f to 0.5f, 0.34f to 1f, 0.66f to 1f)
        }
    }
    // One outline, so overlapping pieces never cancel out.
    return pieces.reduce { a, b -> Path.combine(PathOperation.Union, a, b) }
}
