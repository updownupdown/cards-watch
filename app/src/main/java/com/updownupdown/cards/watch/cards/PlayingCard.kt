package com.updownupdown.cards.watch.cards

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.min

object CardColors {
    val Face = Color(0xFFF4F1EA)
    val Back = Color(0xFF7A2E3A)
    val BackTrim = Color(0xFFB0596A)
    /** An empty pile or space: off-black, so it shows against the black screen. */
    val EmptySlot = Color(0xFF22262C)
    /** The border colour of a selected card. Reserved for selection. */
    val Selected = Color(0xFFFFC400)

    // A four-colour deck, so suits are told apart at a glance.
    fun suit(suit: Suit) = when (suit) {
        Suit.Hearts -> Color(0xFFD32F2F)
        Suit.Diamonds -> Color(0xFF1565C0)
        Suit.Clubs -> Color(0xFF2E7D32)
        Suit.Spades -> Color(0xFF1A1A1A)
    }
}

/** Status colours that tint a card's face (the rank and suit stay on top). */
object CardOverlay {
    val Good = Color(0x6634B35A)
    val Bad = Color(0x66E53935)
    val Hint = Color(0x662979FF)
}

/**
 * A playing card that fills its bounds, scaled to its size: the rank above
 * the suit, or with [indexHeight], the rank and suit side by side along the
 * top edge (so they show when cards overlap) and a large suit below.
 *
 * @param card the card to show, or null for an empty slot.
 * @param overlay a status colour (see [CardOverlay]) tinting the card.
 * @param selected draws the selection border. Nothing else uses a border.
 * @param indexHeight the height of the top strip, as a fraction of the card's.
 */
@Composable
fun PlayingCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    overlay: Color? = null,
    selected: Boolean = false,
    indexHeight: Float? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val short = min(size.width, size.height)
        val corner = CornerRadius(short * 0.14f)
        when {
            card == null -> drawRoundRect(CardColors.EmptySlot, cornerRadius = corner)
            faceDown -> {
                drawBack(corner, short)
                if (overlay != null) drawRoundRect(overlay, cornerRadius = corner)
            }
            else -> {
                drawRoundRect(CardColors.Face, cornerRadius = corner)
                if (overlay != null) drawRoundRect(overlay, cornerRadius = corner)
                if (indexHeight != null) {
                    drawIndexFace(card, textMeasurer, indexHeight)
                } else {
                    drawFace(card, textMeasurer)
                }
            }
        }
        if (selected) {
            val stroke = short * 0.1f
            drawRoundRect(
                color = CardColors.Selected,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(corner.x - stroke / 2),
                style = Stroke(width = stroke),
            )
        }
    }
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

private fun DrawScope.drawFace(card: Card, textMeasurer: TextMeasurer) {
    val color = CardColors.suit(card.suit)
    val w = size.width
    val h = size.height

    // Rank in the upper part, shrunk to fit if it's too wide (the "10").
    var fontPx = min(h * 0.5f, w * 0.62f)
    var layout = textMeasurer.measure(card.rank.label, rankStyle(fontPx, color))
    if (layout.size.width > w * 0.9f) {
        fontPx *= w * 0.9f / layout.size.width
        layout = textMeasurer.measure(card.rank.label, rankStyle(fontPx, color))
    }
    drawText(
        layout,
        topLeft = Offset(
            (w - layout.size.width) / 2,
            h * 0.36f - layout.size.height / 2,
        ),
    )

    val suitSize = min(w * 0.42f, h * 0.32f)
    drawSuit(card.suit, Offset((w - suitSize) / 2, h * 0.74f - suitSize / 2), suitSize, color)
}

private fun DrawScope.drawIndexFace(card: Card, textMeasurer: TextMeasurer, indexHeight: Float) {
    val color = CardColors.suit(card.suit)
    val w = size.width
    val h = size.height
    val strip = h * indexHeight

    // Rank then a small suit, in a row filling the strip.
    val pad = w * 0.08f
    var fontPx = strip * 1.05f
    var layout = textMeasurer.measure(card.rank.label, rankStyle(fontPx, color))
    val maxRankWidth = w * 0.58f
    if (layout.size.width > maxRankWidth) {
        fontPx *= maxRankWidth / layout.size.width
        layout = textMeasurer.measure(card.rank.label, rankStyle(fontPx, color))
    }
    val suitSize = min(strip * 0.7f, w - pad * 3 - layout.size.width)
    val rowWidth = layout.size.width + pad * 0.5f + suitSize
    val left = (w - rowWidth) / 2
    drawText(layout, topLeft = Offset(left, strip / 2 - layout.size.height / 2 + h * 0.01f))
    drawSuit(card.suit, Offset(left + layout.size.width + pad * 0.5f, (strip - suitSize) / 2 + h * 0.01f), suitSize, color)

    // A large suit in the rest of the card, when there's room for it.
    val body = h - strip
    val bigSuit = min(w * 0.55f, body * 0.6f)
    if (bigSuit > strip * 0.8f) {
        drawSuit(card.suit, Offset((w - bigSuit) / 2, strip + (body - bigSuit) / 2), bigSuit, color)
    }
}

private fun DrawScope.rankStyle(fontPx: Float, color: Color) = TextStyle(
    color = color,
    // Sized in pixels, so the system font size doesn't overflow the card.
    fontSize = (fontPx / density / fontScale).sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.04).em,
)

/** Draws a suit symbol in the square at [topLeft] with side [s]. */
fun DrawScope.drawSuit(suit: Suit, topLeft: Offset, s: Float, color: Color) {
    fun p(x: Float, y: Float) = Offset(topLeft.x + x * s, topLeft.y + y * s)
    fun polygon(vararg points: Pair<Float, Float>) {
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            val o = p(x, y)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        drawPath(path, color)
    }
    fun circle(x: Float, y: Float, r: Float) = drawCircle(color, r * s, p(x, y))

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
}
