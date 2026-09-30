package com.updownupdown.cards.watch.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min

private const val SLIDE_MS = 260
private val UnderCard = Color(0x40000000)

/**
 * Animates [target] into its place: a new item slides in from off screen on
 * the left and the one it replaces slides off to the right. Items with the
 * same [key] (say, the same card turned over) change in place; a null key
 * (an empty place) appears and disappears without sliding.
 */
@Composable
fun <T> SlideSlot(
    target: T,
    key: (T) -> Any?,
    modifier: Modifier = Modifier,
    exitDelayMs: Int = 0,
    content: @Composable (T) -> Unit,
) {
    val screen = LocalWindowInfo.current.containerSize.width
    AnimatedContent(
        targetState = target,
        contentKey = key,
        modifier = modifier,
        transitionSpec = {
            val enter = if (key(targetState) == null) {
                EnterTransition.None
            } else {
                slideInHorizontally(tween(SLIDE_MS)) { -screen }
            }
            val exit = if (key(initialState) == null) {
                ExitTransition.None
            } else {
                slideOutHorizontally(tween(SLIDE_MS, delayMillis = exitDelayMs)) { screen }
            }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "slide",
    ) { content(it) }
}

/**
 * A pile showing its top card and, when there's more than one card, the top
 * edge of the card under it. An empty pile is an empty slot.
 */
@Composable
fun CardPile(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    overlay: Color? = null,
    selected: Boolean = false,
    faceUp: (index: Int) -> Boolean = { true },
) {
    BoxWithConstraints(modifier) {
        val edge = maxHeight * 0.08f
        val cardHeight = maxHeight - edge
        val cardWidth = maxWidth
        if (cards.size > 1) {
            PlayingCard(
                card = cards[cards.lastIndex - 1],
                faceDown = !faceUp(cards.lastIndex - 1),
                overlay = UnderCard,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
            )
        }
        SlideSlot(cards.lastOrNull(), key = { it }, modifier = Modifier.offset(y = edge)) { top ->
            PlayingCard(
                card = top,
                faceDown = top != null && !faceUp(cards.lastIndex),
                overlay = overlay,
                selected = selected,
                modifier = Modifier.size(cardWidth, cardHeight),
            )
        }
    }
}

/**
 * A column of overlapping cards, first at the top, each showing its rank and
 * suit along its top edge. Cards are as wide as the column and never taller
 * than [maxAspect] times their width.
 *
 * @param selectedFrom index of the first selected card: it and those on it
 *   are drawn selected.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    maxAspect: Float = 1.4f,
    selectedFrom: Int? = null,
    overlay: (index: Int) -> Color? = { null },
) {
    BoxWithConstraints(modifier) {
        val cardWidth = maxWidth
        val cardHeight: Dp = min(maxHeight, maxWidth * maxAspect)
        val offset: Dp = if (cards.size > 1) {
            min(cardHeight * 0.3f, (maxHeight - cardHeight) / (cards.size - 1))
        } else {
            cardHeight * 0.3f
        }
        val strip = (offset / cardHeight).coerceIn(0.14f, 0.3f)
        if (cards.isEmpty()) {
            PlayingCard(null, Modifier.size(maxWidth, cardHeight))
        }
        cards.forEachIndexed { index, card ->
            Box(Modifier.offset(y = offset * index).size(cardWidth, cardHeight)) {
                PlayingCard(
                    card = card,
                    overlay = overlay(index),
                    selected = selectedFrom != null && index >= selectedFrom,
                    indexHeight = strip,
                    modifier = Modifier.size(cardWidth, cardHeight),
                )
            }
        }
    }
}
