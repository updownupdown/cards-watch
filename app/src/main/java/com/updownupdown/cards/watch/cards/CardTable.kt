package com.updownupdown.cards.watch.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long a card takes to slide in, out or across. */
const val CARD_MOVE_MS = 1000
/** How long a card takes to turn over. */
const val CARD_FLIP_MS = 400

/** What a card does when it leaves the table. */
enum class Removal {
    /** Slides off to the right, as if to a discard pile. */
    Discard,
    /** Stays still for a move's length (while a card lands on it), then disappears. */
    Vanish,
}

/** A card placed on the table. Cards are matched between frames by [key]. */
data class TableCard(
    val key: Any,
    val card: Card,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val faceDown: Boolean = false,
    val z: Float = 0f,
    val overlay: Color? = null,
    val selection: Selection? = null,
    val indexHeight: Float? = null,
    val under: Boolean = false,
    val removal: Removal = Removal.Discard,
) {
    val height: Dp get() = width * CARD_ASPECT
}

internal data class Slot(val x: Dp, val y: Dp, val width: Dp, val overlay: Color?)
internal data class Tap(val x: Dp, val y: Dp, val width: Dp, val height: Dp, val onTap: () -> Unit)

/** Collects what to draw on a [CardTable] of the given size. */
class TableBuilder(val width: Dp, val height: Dp) {
    internal val cards = mutableListOf<TableCard>()
    internal val slots = mutableListOf<Slot>()
    internal val taps = mutableListOf<Tap>()

    fun card(card: TableCard) {
        cards += card
    }

    fun card(
        key: Any,
        card: Card,
        x: Dp,
        y: Dp,
        width: Dp,
        faceDown: Boolean = false,
        z: Float = 0f,
        overlay: Color? = null,
        selection: Selection? = null,
        indexHeight: Float? = null,
        removal: Removal = Removal.Discard,
    ) {
        cards += TableCard(key, card, x, y, width, faceDown, z, overlay, selection, indexHeight, removal = removal)
    }

    /** An empty place for a card. */
    fun slot(x: Dp, y: Dp, width: Dp, overlay: Color? = null) {
        slots += Slot(x, y, width, overlay)
    }

    /** A tappable area, on top of everything. */
    fun tap(x: Dp, y: Dp, width: Dp, height: Dp, onTap: () -> Unit) {
        taps += Tap(x, y, width, height, onTap)
    }

    /**
     * A pile at ([x], [y]): its top card, and the card under it darkened and
     * showing to the top left, so one card and several look different. The
     * rest stay hidden under them, so cards moving in and out of the pile
     * glide rather than appear. Empty, it's an empty slot. It takes
     * [cardWidth] + [pileEdge] each way.
     */
    fun pile(
        cards: List<Card>,
        x: Dp,
        y: Dp,
        cardWidth: Dp,
        keyOf: (index: Int) -> Any = { cards[it] },
        faceDown: (index: Int) -> Boolean = { false },
        overlay: Color? = null,
        selection: Selection? = null,
        removal: Removal = Removal.Discard,
        z: Float = 0f,
        tap: (() -> Unit)? = null,
    ) {
        val edge = pileEdge(cardWidth)
        val top = cards.lastIndex
        cards.forEachIndexed { i, card ->
            this.cards += if (i == top) {
                TableCard(keyOf(i), card, x + edge, y + edge, cardWidth, faceDown(i), z + 1f, overlay, selection, removal = removal)
            } else {
                TableCard(keyOf(i), card, x, y, cardWidth, faceDown(i), z + i * 0.01f, under = true, removal = removal)
            }
        }
        if (top < 0) slot(x + edge, y + edge, cardWidth, overlay)
        if (tap != null) tap(x, y, cardWidth + edge, cardWidth * CARD_ASPECT + edge, tap)
    }

    /**
     * A column of overlapping cards from ([x], [y]) down, first at the top,
     * each showing its rank and suit along its top edge, fitting in [maxHeight].
     */
    fun fan(
        cards: List<Card>,
        x: Dp,
        y: Dp,
        cardWidth: Dp,
        maxHeight: Dp,
        keyOf: (index: Int) -> Any = { cards[it] },
        selectedFrom: Int? = null,
        selection: Selection = Selection.Neutral,
        z: Float = 0f,
    ) {
        val cardHeight = cardWidth * CARD_ASPECT
        val offset = if (cards.size > 1) min(cardHeight * 0.3f, (maxHeight - cardHeight) / (cards.size - 1)) else cardHeight * 0.3f
        val strip = (offset / cardHeight).coerceIn(0.14f, 0.3f)
        if (cards.isEmpty()) slot(x, y, cardWidth)
        cards.forEachIndexed { i, card ->
            this.cards += TableCard(
                key = keyOf(i),
                card = card,
                x = x,
                y = y + offset * i,
                width = cardWidth,
                z = z + i,
                selection = if (selectedFrom != null && i >= selectedFrom) selection else null,
                indexHeight = strip,
            )
        }
    }
}

/** How far a pile's top card sits from the card under it. */
fun pileEdge(cardWidth: Dp): Dp = cardWidth * 0.1f

/**
 * Cards of one size laid out in [columns] × [rows], as large as fit in
 * [width] × [height] (plus [extraWidth] and [extraHeight] per card, for pile
 * edges), centred. [gap] is a fraction of the card width.
 */
class CardLayout(
    val columns: Int,
    val rows: Int,
    width: Dp,
    height: Dp,
    gap: Float = 0.1f,
    extra: Float = 0f,
) {
    val cardWidth: Dp
    val gap: Dp
    val cellWidth: Dp
    val cellHeight: Dp
    private val left: Dp
    private val top: Dp

    init {
        // Width per column: card (1 + extra) + gap; height per row: card × aspect + extra + gap.
        val byWidth = width / (columns * (1 + extra) + (columns - 1) * gap)
        val byHeight = height / (rows * (CARD_ASPECT + extra) + (rows - 1) * gap)
        cardWidth = min(byWidth, byHeight)
        this.gap = cardWidth * gap
        cellWidth = cardWidth * (1 + extra)
        cellHeight = cardWidth * (CARD_ASPECT + extra)
        left = (width - cellWidth * columns - this.gap * (columns - 1)) / 2
        top = (height - cellHeight * rows - this.gap * (rows - 1)) / 2
    }

    val cardHeight: Dp get() = cardWidth * CARD_ASPECT

    fun x(column: Int): Dp = left + (cellWidth + gap) * column
    fun y(row: Int): Dp = top + (cellHeight + gap) * row
    fun xOf(index: Int): Dp = x(index % columns)
    fun yOf(index: Int): Dp = y(index / columns)
}

/**
 * A board of cards that animate between frames: a card that moves glides to
 * its new place, a new card slides in from off screen on the left, a removed
 * card leaves as its [TableCard.removal] says, and a card turned over flips.
 * Nothing animates the first time the table is shown.
 */
@Composable
fun CardTable(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    build: TableBuilder.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val spec = TableBuilder(maxWidth, maxHeight).apply(build)
        val screen = LocalWindowInfo.current.containerSize.width.toFloat()

        // Cards from the last frame, and those still leaving.
        val previous = remember { HashMap<Any, TableCard>() }
        val leaving = remember { LinkedHashMap<Any, TableCard>() }
        var gone by remember { mutableIntStateOf(0) }
        val shown = remember { BooleanArray(1) }
        gone // Read, so a card finishing its exit redraws the table.

        val current = spec.cards.associateBy { it.key }
        previous.forEach { (k, card) -> if (k !in current) leaving[k] = card }
        leaving.keys.removeAll(current.keys)
        previous.clear()
        previous.putAll(current)
        val animateIn = shown[0]
        SideEffect { shown[0] = true }

        spec.slots.forEach { slot ->
            PlayingCard(null, Modifier.offset(slot.x, slot.y).size(slot.width, slot.width * CARD_ASPECT), overlay = slot.overlay)
        }
        for (card in spec.cards) {
            key(card.key) { MovingCard(card, leaving = false, enter = animateIn, screen = screen) {} }
        }
        for (card in leaving.values.toList()) {
            key(card.key) {
                MovingCard(card, leaving = true, enter = false, screen = screen) {
                    leaving.remove(card.key)
                    gone++
                }
            }
        }
        overlay()
        spec.taps.forEach { tap ->
            Box(
                Modifier
                    .offset(tap.x, tap.y)
                    .size(tap.width, tap.height)
                    .zIndex(10_000f)
                    .clickable(interactionSource = null, indication = null, onClick = tap.onTap),
            )
        }
    }
}

@Composable
private fun MovingCard(card: TableCard, leaving: Boolean, enter: Boolean, screen: Float, onGone: () -> Unit) {
    val density = LocalDensity.current
    val targetX = with(density) { card.x.toPx() }
    val targetY = with(density) { card.y.toPx() }
    val x = remember { Animatable(if (enter) targetX - screen else targetX) }
    val y = remember { Animatable(targetY) }
    val spec = tween<Float>(CARD_MOVE_MS, easing = FastOutSlowInEasing)

    LaunchedEffect(targetX, targetY, leaving) {
        if (leaving) {
            if (card.removal == Removal.Discard) x.animateTo(x.value + screen, spec) else kotlinx.coroutines.delay(CARD_MOVE_MS.toLong())
            onGone()
        } else {
            coroutineScope {
                launch { x.animateTo(targetX, spec) }
                launch { y.animateTo(targetY, spec) }
            }
        }
    }

    val turn by animateFloatAsState(if (card.faceDown) 180f else 0f, tween(CARD_FLIP_MS), label = "flip")
    val moving = x.isRunning || y.isRunning
    Box(
        Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(card.width, card.height)
            // A card on the move (or leaving) passes over the others.
            .zIndex(if (leaving && card.removal == Removal.Discard || moving) card.z + 1000f else if (leaving) card.z - 0.25f else card.z)
            .graphicsLayer {
                rotationY = turn
                cameraDistance = 12f * this.density
            },
    ) {
        PlayingCard(
            card = card.card,
            faceDown = turn > 90f,
            overlay = card.overlay,
            selection = card.selection,
            indexHeight = card.indexHeight,
            under = card.under,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

