package com.updownupdown.cards.watch.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.updownupdown.cards.watch.ui.LocalAmbient
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

/**
 * Which deal the board shows, counting up with each new one. When it changes
 * every card on the table slides off to the right, and only then is the new
 * deal dealt, even cards that were in the last one.
 */
val LocalDeal = compositionLocalOf { 0 }

/** A card on the table in one deal: the same card in the next deal is another. */
private data class Placed(val deal: Int, val key: Any)

/** Why a card left the table. */
private enum class Exit { Played, Undone, Cleared }

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
    val under: Boolean = false,
    val removal: Removal = Removal.Discard,
    /** How see-through the card is; changes show at once. */
    val alpha: Float = 1f,
    /**
     * Where the card is dealt from, instead of from off screen: it arrives
     * from here turning face up, and goes back here when it leaves.
     */
    val from: DpOffset? = null,
    /** Arrives from off screen on the right instead of the left (and goes back there when undone). */
    val fromRight: Boolean = false,
    /** How far the card is turned clockwise, in degrees; changes turn it the short way round. */
    val rotation: Float = 0f,
    /** A dot in the card's top right corner, in this colour: a warning. */
    val dot: Color? = null,
    /** How long the card takes to turn over in place. */
    val flipMs: Int = CARD_FLIP_MS,
) {
    val height: Dp get() = width * CARD_ASPECT
}

internal data class Slot(val x: Dp, val y: Dp, val width: Dp, val overlay: Color?, val height: Dp = width * CARD_ASPECT, val mark: (DrawScope.() -> Unit)? = null)
internal data class Zone(val x: Dp, val y: Dp, val width: Dp, val height: Dp, val corner: Dp)
internal data class Tap(val x: Dp, val y: Dp, val width: Dp, val height: Dp, val onTap: () -> Unit)

/** Collects what to draw on a [CardTable] of the given size. */
class TableBuilder(val width: Dp, val height: Dp) {
    internal val cards = mutableListOf<TableCard>()
    internal val slots = mutableListOf<Slot>()
    internal val zones = mutableListOf<Zone>()
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
        removal: Removal = Removal.Discard,
        alpha: Float = 1f,
        under: Boolean = false,
        from: DpOffset? = null,
        fromRight: Boolean = false,
        rotation: Float = 0f,
        dot: Color? = null,
    ) {
        cards += TableCard(key, card, x, y, width, faceDown, z, overlay, selection, under, removal, alpha, from, fromRight, rotation, dot)
    }

    /** A place for several cards [cardWidth] wide, such as a spread: a line just inside its edge. */
    fun zone(x: Dp, y: Dp, width: Dp, cardWidth: Dp) {
        zones += Zone(x, y, width, cardWidth * CARD_ASPECT, cardWidth * CardStyle.CORNER)
    }

    /** An empty place for a card, with a [mark] drawn on it (under any cards there), such as Jailbreak's Joker. */
    fun slot(x: Dp, y: Dp, width: Dp, overlay: Color? = null, height: Dp = width * CARD_ASPECT, mark: (DrawScope.() -> Unit)? = null) {
        slots += Slot(x, y, width, overlay, height, mark)
    }

    /** A tappable area, on top of everything. */
    fun tap(x: Dp, y: Dp, width: Dp, height: Dp, onTap: () -> Unit) {
        taps += Tap(x, y, width, height, onTap)
    }

    /**
     * A pile at ([x], [y]): its top card, and the card under it shaded and
     * showing to the bottom left, so one card and several look different. A
     * card on its own isn't offset. The rest stay hidden under them, so cards
     * moving in and out of the pile glide rather than appear. An empty slot
     * lies under it all, which shows once it's empty. It takes [cardWidth] + [pileEdge] each way.
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
        flipMs: Int = CARD_FLIP_MS,
        tap: (() -> Unit)? = null,
    ) {
        val edge = pileEdge(cardWidth)
        val top = cards.lastIndex
        cards.forEachIndexed { i, card ->
            this.cards += if (i == top) {
                // Up and to the right, but only off a card underneath.
                val lift = if (top > 0) edge else 0.dp
                TableCard(keyOf(i), card, x + lift, y + edge - lift, cardWidth, faceDown(i), z + 1f, overlay, selection, removal = removal, flipMs = flipMs)
            } else {
                TableCard(keyOf(i), card, x, y + edge, cardWidth, faceDown(i), z + i * 0.01f, under = true, removal = removal, flipMs = flipMs)
            }
        }
        // Its place always shows under it, so it looks the same while cards come and go.
        slot(x, y + edge, cardWidth, overlay)
        if (tap != null) tap(x, y, cardWidth + edge, cardWidth * CARD_ASPECT + edge, tap)
    }

    /**
     * A column of overlapping cards from ([x], [y]) down, first at the top,
     * each showing the rank in its top corner, fitting in [maxHeight].
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
        slot(x, y, cardWidth)
        cards.forEachIndexed { i, card ->
            this.cards += TableCard(
                key = keyOf(i),
                card = card,
                x = x,
                y = y + offset * i,
                width = cardWidth,
                z = z + i,
                selection = if (selectedFrom != null && i >= selectedFrom) selection else null,
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
 *
 * @param overlay drawn over the cards; [underlay] is under them and their slots.
 */
@Composable
fun CardTable(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    underlay: @Composable BoxScope.() -> Unit = {},
    build: TableBuilder.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val spec = TableBuilder(maxWidth, maxHeight).apply(build)
        val screen = LocalWindowInfo.current.containerSize.width.toFloat()

        // Cards from the last frame, and those still leaving, and how each leaves.
        val previous = remember { HashMap<Placed, TableCard>() }
        val leaving = remember { LinkedHashMap<Placed, TableCard>() }
        val exits = remember { HashMap<Placed, Exit>() }
        var gone by remember { mutableIntStateOf(0) }
        val shown = remember { BooleanArray(1) }
        gone // Read, so a card finishing its exit redraws the table.

        val deal = LocalDeal.current
        val current = spec.cards.associateBy { Placed(deal, it.key) }
        val undoing = LocalUndoing.current
        val animateIn = shown[0]
        // A new deal clears the table first: the new cards wait for the old to go.
        val clearing = animateIn && previous.keys.any { it.deal != deal }
        val pause = if (clearing) CARD_MOVE_MS + CARD_CLEAR_PAUSE_MS else 0
        // New cards set off one after another: the right-most first, or as dealt (bottom first) from a place.
        val arriving = current.filterKeys { it !in previous }.values
            .sortedWith(compareBy({ it.from == null }, { if (it.from != null) it.z else -it.x.value }))
        val stagger = if (arriving.size > 1) minOf(CARD_DEAL_STAGGER_MS, CARD_DEAL_MAX_MS / (arriving.size - 1)) else 0
        val waits = arriving.withIndex().associate { (i, card) -> Placed(deal, card.key) to pause + i * stagger }
        previous.forEach { (k, card) ->
            if (k !in current) {
                leaving[k] = card
                exits[k] = when {
                    k.deal != deal -> Exit.Cleared
                    undoing -> Exit.Undone
                    else -> Exit.Played
                }
            }
        }
        leaving.keys.removeAll(current.keys)
        previous.clear()
        previous.putAll(current)
        SideEffect { shown[0] = true }

        underlay()
        spec.slots.forEach { slot ->
            PlayingCard(null, Modifier.offset(slot.x, slot.y).size(slot.width, slot.height), overlay = slot.overlay)
            slot.mark?.let { Canvas(Modifier.offset(slot.x, slot.y).size(slot.width, slot.height), onDraw = it) }
        }
        val zoneColour = if (LocalAmbient.current) CardColors.Ambient.copy(alpha = 0.3f) else CardColors.Zone
        spec.zones.forEach { zone ->
            Box(Modifier.offset(zone.x, zone.y).size(zone.width, zone.height).border(CardStyle.EDGE_DP.dp, zoneColour, RoundedCornerShape(zone.corner)))
        }
        for ((placed, card) in current) {
            key(placed) { MovingCard(card, leaving = false, enter = animateIn, back = undoing, cleared = false, wait = waits[placed] ?: 0, screen = screen) {} }
        }
        for ((placed, card) in leaving.entries.toList()) {
            key(placed) {
                val exit = exits[placed]
                MovingCard(card, leaving = true, enter = false, back = exit == Exit.Undone, cleared = exit == Exit.Cleared, wait = 0, screen = screen) {
                    leaving.remove(placed)
                    exits.remove(placed)
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
 * @param cleared whether the card leaves for a new deal: off to the right, whatever it is.
 * @param wait how long the card holds before arriving, its turn in a deal.
 */
@Composable
private fun MovingCard(card: TableCard, leaving: Boolean, enter: Boolean, back: Boolean, cleared: Boolean, wait: Int, screen: Float, onGone: () -> Unit) {
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
                cleared -> x.animateTo(x.value + screen, spec)
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
        if (turn[0] < 0 && flip.targetValue != target) flip.animateTo(target, tween(card.flipMs))
    }

    // Shades as another card lands on it.
    val shade = animateFloatAsState(if (card.under) 1f else 0f, tween(CARD_MOVE_MS), label = "under")
    // Turned the short way round to its angle.
    val turned = remember { Animatable(card.rotation) }
    LaunchedEffect(card.rotation) {
        val by = ((card.rotation - turned.value) % 360 + 540) % 360 - 180
        if (by != 0f) turned.animateTo(turned.value + by, spec)
    }
    val moving = x.isRunning || y.isRunning
    val sliding = leaving && (card.removal == Removal.Discard || back || dealt || cleared)
    Box(
        Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(card.width, card.height)
            .testTag(cardTag(card.key))
            // A card on the move (or leaving) passes over the others.
            .zIndex(if (sliding || moving) card.z + 1000f else if (leaving) card.z - 0.25f else card.z)
            .graphicsLayer {
                rotationZ = turned.value
                alpha = card.alpha
            },
    ) {
        // The flip is its own layer inside the turn, so the card flips about its own vertical axis.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = flip.value
                    cameraDistance = 12f * this.density
                },
        ) {
            PlayingCard(
                card = card.card,
                faceDown = flip.value > 90f,
                overlay = card.overlay,
                selection = card.selection,
                dot = card.dot,
                under = { shade.value },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
