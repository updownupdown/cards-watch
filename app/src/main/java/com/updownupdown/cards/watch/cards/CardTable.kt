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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the board is showing an undo. Cards then go back the way they
 * came: those that had slid in from the left leave to the left, and those
 * that had left to the right come back from the right.
 */
val LocalUndoing = compositionLocalOf { false }

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
    /** How see-through the card is; changes fade. */
    val alpha: Float = 1f,
    /** Shows the rank and suit in this much of the card's left (× its width), for cards overlapping sideways. */
    val indexWidth: Float? = null,
    /**
     * Where the card is dealt from, instead of from off screen: it arrives
     * from here turning face up, and goes back here when it leaves.
     */
    val from: DpOffset? = null,
    /** Arrives from off screen on the right instead of the left (and goes back there when undone). */
    val fromRight: Boolean = false,
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
        alpha: Float = 1f,
        indexWidth: Float? = null,
        under: Boolean = false,
        from: DpOffset? = null,
        fromRight: Boolean = false,
    ) {
        cards += TableCard(key, card, x, y, width, faceDown, z, overlay, selection, indexHeight, under, removal, alpha, indexWidth, from, fromRight)
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
     * A pile at ([x], [y]): its top card, and the card under it shaded and
     * showing to the bottom left, so one card and several look different. A
     * card on its own isn't offset. The rest stay hidden under them, so cards
     * moving in and out of the pile glide rather than appear. Empty, it's an
     * empty slot. It takes [cardWidth] + [pileEdge] each way.
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
                // Up and to the right, but only off a card underneath.
                val lift = if (top > 0) edge else 0.dp
                TableCard(keyOf(i), card, x + lift, y + edge - lift, cardWidth, faceDown(i), z + 1f, overlay, selection, removal = removal)
            } else {
                TableCard(keyOf(i), card, x, y + edge, cardWidth, faceDown(i), z + i * 0.01f, under = true, removal = removal)
            }
        }
        if (top < 0) slot(x, y + edge, cardWidth, overlay)
        if (tap != null) tap(x, y, cardWidth + edge, cardWidth * CARD_ASPECT + edge, tap)
    }

    /**
     * A column of overlapping cards from ([x], [y]) down, first at the top,
     * each showing its rank and suit along its top edge, fitting in [maxHeight].
     * Each sits [spread] of a card's height below the last, or less to fit.
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
        spread: Float = CardStyle.FAN_SPREAD,
    ) {
        val cardHeight = cardWidth * CARD_ASPECT
        val offset = if (cards.size > 1) min(cardHeight * spread, (maxHeight - cardHeight) / (cards.size - 1)) else cardHeight * spread
        val strip = (offset / cardHeight).coerceIn(0.14f, spread)
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

/** How far a tap area reaches beyond its card, into the space around it. */
private val TAP_REACH = 8.dp

/**
 * Widens each tap area by up to [reach] on every side, into the gaps between
 * cards, stopping halfway to a neighbour: small cards are easier to hit, and
 * no area takes another's taps.
 */
internal fun spreadTaps(taps: List<Tap>, reach: Dp): List<Tap> = taps.map { tap ->
    // The gap to the nearest area lying wholly beyond one side, among those level with this one.
    fun room(level: (Tap) -> Boolean, gap: (Tap) -> Dp): Dp {
        val nearest = taps.filter { it !== tap && level(it) && gap(it) >= 0.dp }.minOfOrNull(gap)
        // Areas that overlap this one leave it as it is on that side.
        val crowded = taps.any { it !== tap && level(it) && overlaps(it, tap) }
        return if (crowded) 0.dp else min(reach, (nearest ?: (reach * 2)) / 2)
    }
    val inRow = { other: Tap -> other.y < tap.y + tap.height && other.y + other.height > tap.y }
    val inColumn = { other: Tap -> other.x < tap.x + tap.width && other.x + other.width > tap.x }
    val left = room(inRow) { tap.x - (it.x + it.width) }
    val right = room(inRow) { it.x - (tap.x + tap.width) }
    val top = room(inColumn) { tap.y - (it.y + it.height) }
    val bottom = room(inColumn) { it.y - (tap.y + tap.height) }
    tap.copy(x = tap.x - left, y = tap.y - top, width = tap.width + left + right, height = tap.height + top + bottom)
}

private fun overlaps(a: Tap, b: Tap) =
    a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y

/** The test tag of the card with [key]. */
internal fun cardTag(key: Any) = "card:$key"

/** How far a pile's top card sits from the card under it. */
fun pileEdge(cardWidth: Dp): Dp = cardWidth * CardStyle.PILE_OFFSET

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
 * its new place, a new card slides in from off screen on the left (or from
 * its [TableCard.from]), a removed card leaves as its [TableCard.removal]
 * says, and a card turned over flips. An undo ([LocalUndoing]) runs backwards.
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
        val undoing = LocalUndoing.current
        val leavingBack = remember { HashMap<Any, Boolean>() }
        // New cards set off one after another: the right-most first, or as dealt (bottom first) from a place.
        val arriving = spec.cards.filter { it.key !in previous }
            .sortedWith(compareBy({ it.from == null }, { if (it.from != null) it.z else -it.x.value }))
        val stagger = if (arriving.size > 1) minOf(CARD_DEAL_STAGGER_MS, CARD_DEAL_MAX_MS / (arriving.size - 1)) else 0
        val waits = arriving.withIndex().associate { (i, card) -> card.key to i * stagger }
        previous.forEach { (k, card) ->
            if (k !in current) {
                leaving[k] = card
                leavingBack[k] = undoing
            }
        }
        leaving.keys.removeAll(current.keys)
        previous.clear()
        previous.putAll(current)
        val animateIn = shown[0]
        SideEffect { shown[0] = true }

        spec.slots.forEach { slot ->
            PlayingCard(null, Modifier.offset(slot.x, slot.y).size(slot.width, slot.width * CARD_ASPECT), overlay = slot.overlay)
        }
        for (card in spec.cards) {
            key(card.key) { MovingCard(card, leaving = false, enter = animateIn, back = undoing, wait = waits[card.key] ?: 0, screen = screen) {} }
        }
        for (card in leaving.values.toList()) {
            key(card.key) {
                MovingCard(card, leaving = true, enter = false, back = leavingBack[card.key] == true, wait = 0, screen = screen) {
                    leaving.remove(card.key)
                    leavingBack.remove(card.key)
                    gone++
                }
            }
        }
        overlay()
        spreadTaps(spec.taps, TAP_REACH).forEach { tap ->
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

/**
 * @param enter whether the card arrives with a move (not when the table is first shown).
 * @param back whether this change is an undo, so the card moves the other way.
 * @param wait how long the card holds before arriving, its turn in a deal.
 */
@Composable
private fun MovingCard(card: TableCard, leaving: Boolean, enter: Boolean, back: Boolean, wait: Int, screen: Float, onGone: () -> Unit) {
    val density = LocalDensity.current
    val targetX = with(density) { card.x.toPx() }
    val targetY = with(density) { card.y.toPx() }
    val origin = card.from?.let { with(density) { Offset(it.x.toPx(), it.y.toPx()) } }
    val start = remember {
        when {
            !enter -> Offset(targetX, targetY)
            origin != null -> origin
            // Undone: back from the right, or back in place if it had vanished there.
            back -> if (card.removal == Removal.Vanish) Offset(targetX, targetY) else Offset(targetX + screen, targetY)
            card.fromRight -> Offset(targetX + screen, targetY)
            else -> Offset(targetX - screen, targetY)
        }
    }
    val x = remember { Animatable(start.x) }
    val y = remember { Animatable(start.y) }
    // A dealt card arrives turning over, and turns back as it's taken away.
    val dealt = origin != null
    val flip = remember { Animatable(if (card.faceDown || enter && dealt) 180f else 0f) }
    // Its turn in a deal, kept from when it arrived.
    val turn = remember { IntArray(1) { if (enter) wait else 0 } }
    val spec = tween<Float>(CARD_MOVE_MS, easing = FastOutSlowInEasing)

    LaunchedEffect(targetX, targetY, leaving) {
        if (leaving) {
            when {
                origin != null -> coroutineScope {
                    launch { x.animateTo(origin.x, spec) }
                    launch { y.animateTo(origin.y, spec) }
                    launch { flip.animateTo(180f, spec) }
                }
                back -> x.animateTo(x.value + if (card.fromRight) screen else -screen, spec)
                card.removal == Removal.Discard -> x.animateTo(x.value + screen, spec)
                else -> delay(CARD_MOVE_MS.toLong())
            }
            onGone()
        } else {
            if (turn[0] > 0) delay(turn[0].toLong())
            val arriving = turn[0] >= 0 && dealt && flip.value != 0f && !card.faceDown
            turn[0] = -1
            coroutineScope {
                launch { x.animateTo(targetX, spec) }
                launch { y.animateTo(targetY, spec) }
                if (arriving) launch { flip.animateTo(0f, spec) }
            }
        }
    }
    LaunchedEffect(card.faceDown) {
        val target = if (card.faceDown) 180f else 0f
        // Not while it waits to be dealt, which turns it over itself.
        if (turn[0] < 0 && flip.targetValue != target) flip.animateTo(target, tween(CARD_FLIP_MS))
    }

    // Shades as another card lands on it.
    val shade = animateFloatAsState(if (card.under) 1f else 0f, tween(CARD_MOVE_MS), label = "under")
    val fade = animateFloatAsState(card.alpha, tween(CARD_MOVE_MS), label = "alpha")
    val moving = x.isRunning || y.isRunning
    val sliding = leaving && (card.removal == Removal.Discard || back || dealt)
    Box(
        Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(card.width, card.height)
            .testTag(cardTag(card.key))
            // A card on the move (or leaving) passes over the others.
            .zIndex(if (sliding || moving) card.z + 1000f else if (leaving) card.z - 0.25f else card.z)
            .graphicsLayer {
                rotationY = flip.value
                alpha = fade.value
                cameraDistance = 12f * this.density
            },
    ) {
        PlayingCard(
            card = card.card,
            faceDown = flip.value > 90f,
            overlay = card.overlay,
            selection = card.selection,
            indexHeight = card.indexHeight,
            indexWidth = card.indexWidth,
            under = { shade.value },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
