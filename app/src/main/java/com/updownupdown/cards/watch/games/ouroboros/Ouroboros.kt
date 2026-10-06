package com.updownupdown.cards.watch.games.ouroboros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardColors
import com.updownupdown.cards.watch.cards.CardIcons
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.drawCentredIcon
import com.updownupdown.cards.watch.cards.fullDeck
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros.MOST_STEP
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A card of the loop: [card] null for the Joker (the exit). */
@Serializable
data class LoopCard(val card: Card? = null, val faceUp: Boolean = false) {
    val isJoker: Boolean get() = card == null
    val isAce: Boolean get() = card?.rank == Rank.Ace
    val isTrap: Boolean get() = card != null && card.rank.value > 10
    val isNumber: Boolean get() = card != null && card.rank.value in 2..10
    val value: Int get() = card?.rank?.value ?: 0
}

/** How a deal ended by a move: landing on a trap, or on the exit with every jewel. */
@Serializable
enum class Ending { Trap, Escaped }

/**
 * @param deck the loop; [pos] is its top card, the next after it.
 * @param paths the three marked paths.
 * @param aces jewels collected, and [trinkets] number cards collected.
 */
@Serializable
data class OuroborosState(
    val deck: List<LoopCard>,
    val pos: Int = 0,
    val paths: List<LoopCard?> = List(Ouroboros.PATHS) { null },
    val aces: Int = 0,
    val trinkets: Int = 0,
    val ending: Ending? = null,
)

/** A dungeon crawl round a loop of cards: collect the four jewels and find the exit. */
object Ouroboros : Game<OuroborosState> {
    const val PATHS = 3

    override val id = "ouroboros"
    override val thumbnail = R.drawable.thumb_ouroboros
    override val banner = R.drawable.banner_ouroboros
    override val credit = ArtCredit("Alchemical Serpent (Ouroboros)", "Unknown medieval illuminator", "1478", "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:Serpiente_alquimica.jpg")
    override val accent = R.color.game_ouroboros
    override val accentPale = R.color.game_ouroboros_pale
    override val title = "Ouroboros"
    override val rating = Rating(difficulty = 40, complexity = 55, luck = 40)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<OuroborosState> = OuroborosState.serializer()

    // The loop runs round the whole round screen.
    override val boardFraction = 1f
    override val boardLift = 0f

    override val scoreName = "Loot"
    override fun score(state: OuroborosState) = state.aces + state.trinkets

    override val rules = listOf(
        "## Objective",
        "- Loot all four **jewels** (Aces) and escape alive by stepping onto the **exit** (Joker).",
        "- Landing on a **trap** (J, Q, K) while it is face up means instant death.",
        "- Collect **trinkets** (2 to 10) for extra points.",
        "## The Loop",
        "The dungeon is a loop of cards around the screen. Its **top card** is at the bottom. You explore by counting forward from the top card.",
        "- Landing on a **face down** card is always safe and does nothing.",
        "- Landing on a **face up** card triggers it: a jewel or trinket is collected; a trap kills you; the exit lets you out once you hold all four jewels.",
        "- Card suits have no meaning.",
        "## Actions",
        "- **Look around:** tap the top card while it's face down to turn it and the next one up.",
        "- **Pick:** tap either of the top two cards, face up, to pick it.",
        "- **Explore:** with a number card picked, you can explore down the dungeon by a number of cards equal to its value. Tap the purple card to go there.",
        "- **Mark a path:** with a number card picked, tap an empty path (one of the three maps) to set it aside there.",
        "- **Return to a path:** tap a marked path to put its card back on top of the loop, picked.",
        "## Tips",
        "- **Mark paths whenever you can!** If the top two cards aren't number cards and you have no marked path, you are lost.",
        "- Marking and returning paths, several times in a row, lets you change the top cards, to control how far you go and where you land.",
    )

    private fun bad(card: LoopCard) = card.isJoker || card.isAce || card.isTrap

    /** Whether one of the first two cards can explore to somewhere with a number card to go on with. */
    private fun safeStart(deck: List<LoopCard>): Boolean {
        if (deck.size < 2 || deck.take(2).all(::bad)) return false
        return deck.take(2).any { card ->
            card.isNumber && run {
                val target = card.value % deck.size
                !(bad(deck[target]) && bad(deck[(target + 1) % deck.size]))
            }
        }
    }

    override fun newGame(random: Random): OuroborosState {
        val cards = fullDeck().map { LoopCard(it) } + LoopCard(null)
        var deck = cards.shuffled(random)
        while (!safeStart(deck)) deck = cards.shuffled(random)
        return OuroborosState(deck)
    }

    private fun next(state: OuroborosState) = (state.pos + 1) % state.deck.size

    /** The places of the top two cards. */
    fun tops(state: OuroborosState) = listOf(state.pos, next(state))

    /** Turns the top two cards up, while the top one is face down. */
    fun lookAround(state: OuroborosState): OuroborosState? {
        if (state.ending != null || state.deck[state.pos].faceUp) return null
        val up = tops(state)
        return state.copy(deck = state.deck.mapIndexed { i, card -> if (i in up) card.copy(faceUp = true) else card })
    }

    /** Whether the card at [index] can explore or mark a path: one of the top two, face up, a number. */
    fun usable(state: OuroborosState, index: Int) =
        state.ending == null && index in tops(state) && state.deck[index].let { it.faceUp && it.isNumber }

    /** Where exploring with the card at [index] lands. */
    fun target(state: OuroborosState, index: Int) =
        (state.pos + state.deck[index].value) % state.deck.size

    fun explore(state: OuroborosState, index: Int): OuroborosState? {
        if (!usable(state, index)) return null
        val pos = target(state, index)
        val landed = state.deck[pos]
        val moved = state.copy(pos = pos)
        return when {
            !landed.faceUp -> moved
            landed.isTrap -> moved.copy(ending = Ending.Trap)
            landed.isJoker -> if (state.aces == 4) moved.copy(ending = Ending.Escaped) else moved
            else -> {
                // Collected: off the loop, the next card taking its place.
                val deck = state.deck.filterIndexed { i, _ -> i != pos }
                moved.copy(
                    deck = deck,
                    pos = pos % deck.size,
                    aces = state.aces + if (landed.isAce) 1 else 0,
                    trinkets = state.trinkets + if (landed.isAce) 0 else 1,
                )
            }
        }
    }

    /** Sets the card at [index] aside on the empty path [path]. */
    fun mark(state: OuroborosState, index: Int, path: Int): OuroborosState? {
        if (!usable(state, index) || state.paths.getOrNull(path) != null) return null
        val deck = state.deck.filterIndexed { i, _ -> i != index }
        val pos = if (index < state.pos) state.pos - 1 else state.pos
        return state.copy(
            deck = deck,
            pos = if (deck.isEmpty()) 0 else pos.coerceAtMost(deck.lastIndex),
            paths = state.paths.mapIndexed { i, card -> if (i == path) state.deck[index] else card },
        )
    }

    /** Puts the card on [path] back on top of the loop. */
    fun returnPath(state: OuroborosState, path: Int): OuroborosState? {
        val card = state.paths.getOrNull(path)
        if (state.ending != null || card == null) return null
        return state.copy(
            deck = state.deck.take(state.pos) + card + state.deck.drop(state.pos),
            paths = state.paths.mapIndexed { i, other -> if (i == path) null else other },
        )
    }

    /** Lost: no marked path, and the top two cards (the first face up) aren't number cards. */
    private fun stuck(state: OuroborosState): Boolean {
        val first = state.deck[state.pos]
        val second = state.deck[next(state)]
        return state.paths.all { it == null } && first.faceUp && !first.isNumber && !second.isNumber
    }

    override fun outcome(state: OuroborosState) = when {
        state.ending == Ending.Escaped -> Outcome(
            true,
            "You got all four jewels and found the exit!"
        )

        state.ending == Ending.Trap -> Outcome(false, "You landed on a trap!")
        stuck(state) -> Outcome(
            false,
            if (state.aces + state.trinkets > 0) "Lost in the dungeon, with your loot." else "Lost in the dungeon, with no way out!"
        )

        else -> null
    }

    override fun status(state: OuroborosState): String? = null

    /**
     * Where the loop starts and ends, in degrees clockwise from the right: the
     * top two either side of the bottom (6 o'clock), the last card at 4
     * o'clock, so the loop's one gap is at 5 o'clock, between them.
     */
    private const val BOTTOM = 90f
    private const val LAST = 30f + 360f

    /** A card's width, × the screen's. */
    private const val CARD = 0.115f

    /**
     * The space from one card of the loop to the next, × a card's width: the
     * top two apart, so their taps don't meet; the next ten, then ten closing
     * up to what's left for the rest, which share it. None is more than
     * [MOST_STEP], so cards always overlap a little (but the top two).
     */
    private const val FIRST_STEP = 1.1f
    private const val NEAR_STEP = 0.5f
    private const val NEAR = 10
    private const val CLOSING = 10
    private const val MOST_STEP = 0.85f

    /** How far the picked card and its target come in from the loop, × a card's height: the target nearly clear of it. */
    private const val PULL = 0.22f
    private const val TARGET_PULL = 0.95f

    /** The gap between the loop and the marked paths, and between the paths, × a card's width. */
    private const val PATHS_IN = 0.35f
    private const val PATHS_GAP = 0.35f

    /**
     * Each card's angle round the loop, in degrees clockwise from the right,
     * by its place from the top card ([k] 0). The top card sits just right of
     * the bottom and the next just left of it, both upright; the rest run on
     * clockwise, up the left, across the top and down the right, the last at
     * [LAST]. As the loop shortens it closes up behind the top card, so the
     * gap only ever grows, at 5 o'clock.
     *
     * @param step a card's width in degrees at the loop's radius.
     */
    private fun loopAngles(count: Int, step: Float): FloatArray {
        val first = BOTTOM - FIRST_STEP * step / 2
        val room = LAST - first
        // The space after each card but the last, as a fixed part and a share of what the rest leave.
        val spaces = (count - 1).coerceAtLeast(0)
        val fixed = FloatArray(spaces)
        val share = FloatArray(spaces)
        for (i in 0 until spaces) {
            when {
                i == 0 -> fixed[i] = FIRST_STEP * step
                i <= NEAR -> fixed[i] = NEAR_STEP * step
                i <= NEAR + CLOSING -> {
                    val t = (i - NEAR) / CLOSING.toFloat()
                    fixed[i] = NEAR_STEP * step * (1 - t)
                    share[i] = t
                }

                else -> share[i] = 1f
            }
        }
        val shares = share.sum()
        val rest = if (shares > 0f) (room - fixed.sum()) / shares else 0f
        val gaps = if (shares == 0f || rest > NEAR_STEP * step) {
            // Few cards: all but the top two evenly apart, no more than the most.
            FloatArray(spaces) {
                if (it == 0) FIRST_STEP * step else minOf(
                    MOST_STEP * step,
                    (room - FIRST_STEP * step) / (spaces - 1).coerceAtLeast(1)
                )
            }
        } else {
            FloatArray(spaces) { fixed[it] + share[it] * rest }
        }
        var angle = first
        return FloatArray(count) { k ->
            if (k > 0) angle += gaps[k - 1]
            angle % 360f
        }
    }

    @Composable
    override fun Board(
        state: OuroborosState,
        play: (OuroborosState) -> Unit,
        feedback: Feedback,
        modifier: Modifier
    ) {
        // The card picked, kept by identity so it stays picked as the loop shifts.
        var picked by remember { mutableStateOf<LoopCard?>(null) }
        val playing = outcome(state) == null
        val pickedIndex = picked?.let { card -> tops(state).firstOrNull { state.deck[it] == card } }
            ?.takeIf { playing }
        val targetIndex = pickedIndex?.takeIf { usable(state, it) }?.let { target(state, it) }
        val context = LocalContext.current
        val icons = remember(context) { CardIcons.get(context.assets) }
        val markColour =
            if (LocalAmbient.current) CardColors.Ambient.copy(alpha = 0.3f) else AppColors.SlotMark
        val board = LocalBoardSize.current
        val cardWidth = board * CARD
        val cardHeight = cardWidth * CARD_ASPECT
        val middle = board / 2
        val radius = board / 2 - cardHeight / 2 - 3.dp
        // The marked paths in an arch under the loop's inner edge, the middle one highest, each the same way in from it.
        val pathsLeft = middle - (cardWidth * PATHS + cardWidth * PATHS_GAP * (PATHS - 1)) / 2
        val inner = radius - cardHeight / 2 - cardWidth * PATHS_IN
        val pathXs = List(PATHS) { pathsLeft + (cardWidth + cardWidth * PATHS_GAP) * it }
        val pathYs = pathXs.map { x ->
            // Its corner furthest out on the circle [inner] round the middle.
            val out = maxOf(abs((x - middle).value), abs((x + cardWidth - middle).value))
            middle - sqrt(inner.value * inner.value - out * out).dp
        }
        val countsY = pathYs.max() + cardHeight + 6.dp

        Box(modifier) {
            // What's been collected, side by side under the paths, and under any card brought in over them.
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset(y = countsY),
                horizontalArrangement = Arrangement.spacedBy(
                    COUNTS_GAP,
                    Alignment.CenterHorizontally
                ),
            ) {
                Count("Jewels", state.aces)
                Count("Trinkets", state.trinkets)
            }
            CardTable(Modifier.size(board)) {
                val count = state.deck.size
                val angles =
                    loopAngles(count, Math.toDegrees((cardWidth / radius).toDouble()).toFloat())
                val centre = DpOffset(middle, middle)

                fun place(index: Int): Pair<Float, Dp> {
                    val k = (index - state.pos + count) % count
                    val r = radius - cardHeight * when (index) {
                        targetIndex -> TARGET_PULL
                        pickedIndex -> PULL
                        else -> 0f
                    }
                    return angles[k] to r
                }

                state.deck.forEachIndexed { index, card ->
                    val k = (index - state.pos + count) % count
                    val (angle, r) = place(index)
                    val rad = Math.toRadians(angle.toDouble())
                    card(
                        key = card.card ?: "joker",
                        card = card.card ?: Card(Rank.Ace, Suit.Spades),
                        x = middle + r * cos(rad).toFloat() - cardWidth / 2,
                        y = middle + r * sin(rad).toFloat() - cardHeight / 2,
                        width = cardWidth,
                        faceDown = !card.faceUp,
                        // The top card over the next, and so on round, even as the picked card and its target come in.
                        z = (count - k).toFloat(),
                        selection = when (index) {
                            pickedIndex -> if (card.isNumber) Selection.Neutral else Selection.Invalid
                            targetIndex -> Selection.Purple
                            else -> null
                        },
                        // Tops facing in, so the top cards, at the bottom, read upright.
                        rotation = angle - 90f,
                        joker = card.isJoker,
                        orbit = centre,
                        lift = false,
                    )
                }

                // The marked paths.
                state.paths.forEachIndexed { path, card ->
                    val x = pathXs[path]
                    val y = pathYs[path]
                    slot(x, y, cardWidth, mark = { drawCentredIcon(icons.map, 0.7f, markColour) })
                    if (card != null) {
                        card(
                            card.card ?: "joker",
                            card.card ?: Card(Rank.Ace, Suit.Spades),
                            x,
                            y,
                            cardWidth,
                            joker = card.isJoker
                        )
                    }
                    tap(x, y, cardWidth, cardHeight) {
                        val from = pickedIndex
                        when {
                            !playing -> feedback.reject()
                            card != null -> {
                                feedback.tryMove(returnPath(state, path), play)
                                picked = card
                            }

                            from != null && usable(state, from) -> {
                                feedback.tryMove(mark(state, from, path), play)
                                picked = null
                            }

                            else -> feedback.reject()
                        }
                    }
                }

                // The target over the paths it may cover, and the top two over that: the top card last, so it wins where they meet.
                fun cardTap(index: Int, onTap: () -> Unit) {
                    val (angle, r) = place(index)
                    val rad = Math.toRadians(angle.toDouble())
                    val turn = Math.toRadians((angle - 90f).toDouble())
                    // The turned card's bounds, but upright for the top two, which lie nearly so, so theirs don't meet.
                    val (w, h) = if (index in tops(state)) {
                        cardWidth to cardHeight
                    } else {
                        val c = abs(cos(turn)).toFloat()
                        val s = abs(sin(turn)).toFloat()
                        (cardWidth * c + cardHeight * s) to (cardWidth * s + cardHeight * c)
                    }
                    tap(
                        middle + r * cos(rad).toFloat() - w / 2,
                        middle + r * sin(rad).toFloat() - h / 2,
                        w,
                        h,
                        onTap
                    )
                }
                targetIndex?.let { target ->
                    cardTap(target) {
                        feedback.tryMove(explore(state, pickedIndex), play)
                        picked = null
                    }
                }
                tops(state).reversed().forEach { index ->
                    cardTap(index) {
                        val card = state.deck[index]
                        when {
                            !playing -> feedback.reject()
                            !card.faceUp -> if (index == state.pos) {
                                feedback.tryMove(lookAround(state), play)
                                picked = card.copy(faceUp = true)
                            } else {
                                feedback.reject()
                            }

                            index == pickedIndex -> picked = null
                            else -> picked = card
                        }
                    }
                }
            }
        }
    }

    /** The space between the jewels' count and the trinkets'. */
    private val COUNTS_GAP = 14.dp

    /** A count's name, small and pale, over its number. */
    @Composable
    private fun Count(name: String, value: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                name,
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                "$value",
                style = MaterialTheme.typography.numeralExtraSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@WearGamePreview
@Composable
private fun OuroborosPreview() = GamePreview(Ouroboros)
