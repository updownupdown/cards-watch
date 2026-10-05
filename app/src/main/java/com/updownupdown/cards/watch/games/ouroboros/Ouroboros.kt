package com.updownupdown.cards.watch.games.ouroboros

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
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
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

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
        "- Collect **trinkets** (2 to 10) for extra points. \"A: 3 • T: 2\" counts what you have.",
        "## The Loop",
        "The dungeon is a loop of cards round the screen, starting at the top right. You explore by counting forward from its **top card**.",
        "- Landing on a **face down** card is always safe and does nothing.",
        "- Landing on a **face up** card triggers it: a jewel or trinket is collected; a trap kills you; the exit lets you out once you hold all four jewels.",
        "- Card suits have no meaning.",
        "## Actions",
        "- **Look around:** tap the top card while it's face down to turn it and the next one up.",
        "- **Pick:** tap either of the top two cards, face up, to pick it.",
        "- **Explore:** with a number card picked, the card it would land on is marked purple. Tap it to go there.",
        "- **Mark a path:** with a number card picked, tap an empty path (the map) to set it aside there. Up to three.",
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
    fun target(state: OuroborosState, index: Int) = (state.pos + state.deck[index].value) % state.deck.size

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
        state.ending == Ending.Escaped -> Outcome(true, "You got all four jewels and found the exit!")
        state.ending == Ending.Trap -> Outcome(false, "You landed on a trap!")
        stuck(state) -> Outcome(false, if (state.aces + state.trinkets > 0) "Lost in the dungeon, with your loot." else "Lost in the dungeon, with no way out!")
        else -> null
    }

    override fun status(state: OuroborosState): String? = null

    /** The loop's gap, at the top for the time, in degrees, and each card's share of the rest. */
    private const val GAP = 84f
    /** How far the top card and the next sit back into the gap, so the two stand clear of each other and the rest, in degrees. */
    private const val FIRST_BACK = 12f
    private const val SECOND_BACK = 0f
    /** A card's width, × the screen's. */
    private const val CARD = 0.115f
    /** How far the picked card and its target come in from the loop, × a card's height: the target further, to stand apart. */
    private const val PULL = 0.22f
    private const val TARGET_PULL = 0.6f

    @Composable
    override fun Board(state: OuroborosState, play: (OuroborosState) -> Unit, feedback: Feedback, modifier: Modifier) {
        // The card picked, kept by identity so it stays picked as the loop shifts.
        var picked by remember { mutableStateOf<LoopCard?>(null) }
        val playing = outcome(state) == null
        val pickedIndex = picked?.let { card -> tops(state).firstOrNull { state.deck[it] == card } }?.takeIf { playing }
        val targetIndex = pickedIndex?.takeIf { usable(state, it) }?.let { target(state, it) }
        val context = LocalContext.current
        val icons = remember(context) { CardIcons.get(context.assets) }
        val markColour = if (LocalAmbient.current) CardColors.Ambient.copy(alpha = 0.3f) else AppColors.SlotMark
        val board = LocalBoardSize.current

        Box(modifier) {
            CardTable(Modifier.size(board)) {
                val cardWidth = board * CARD
                val cardHeight = cardWidth * CARD_ASPECT
                val middle = board / 2
                val radius = board / 2 - cardHeight / 2 - 3.dp
                val count = state.deck.size
                val step = (360f - GAP) / count

                state.deck.forEachIndexed { index, card ->
                    // How far round from the top card, which starts just past the gap.
                    val k = (index - state.pos + count) % count
                    val angle = 270f + GAP / 2 + k * step - when (k) {
                        0 -> FIRST_BACK
                        1 -> SECOND_BACK
                        else -> 0f
                    }
                    val r = radius - cardHeight * when (index) {
                        targetIndex -> TARGET_PULL
                        pickedIndex -> PULL
                        else -> 0f
                    }
                    val rad = Math.toRadians(angle.toDouble())
                    val x = middle + r * cos(rad).toFloat() - cardWidth / 2
                    val y = middle + r * sin(rad).toFloat() - cardHeight / 2
                    card(
                        key = card.card ?: "joker",
                        card = card.card ?: Card(Rank.Ace, Suit.Spades),
                        x = x,
                        y = y,
                        width = cardWidth,
                        faceDown = !card.faceUp,
                        // The top card over the next, and so on round; the picked card over all, then its target.
                        z = when (index) {
                            pickedIndex -> count + 2f
                            targetIndex -> count + 1f
                            else -> (count - k).toFloat()
                        },
                        selection = when (index) {
                            pickedIndex -> if (card.isNumber) Selection.Neutral else Selection.Invalid
                            targetIndex -> Selection.Purple
                            else -> null
                        },
                        // Tops facing out, so the top cards, at the upper right, read upright.
                        rotation = angle + 90f,
                        joker = card.isJoker,
                    )
                }
                // The target and the top two cards take taps: the top card last, so it wins where they overlap.
                fun cardTap(index: Int, onTap: () -> Unit) {
                    val k = (index - state.pos + count) % count
                    val angle = 270f + GAP / 2 + k * step - if (k == 0) FIRST_BACK else if (k == 1) SECOND_BACK else 0f
                    val r = radius - cardHeight * when (index) {
                        targetIndex -> TARGET_PULL
                        pickedIndex -> PULL
                        else -> 0f
                    }
                    val rad = Math.toRadians(angle.toDouble())
                    val side = cardHeight * 0.9f
                    tap(middle + r * cos(rad).toFloat() - side / 2, middle + r * sin(rad).toFloat() - side / 2, side, side, onTap)
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
                // The marked paths, in a row across the middle, a little low.
                val gap = cardWidth * 0.35f
                val pathsY = middle - cardHeight / 2 + cardHeight * 0.3f
                val left = middle - (cardWidth * PATHS + gap * (PATHS - 1)) / 2
                state.paths.forEachIndexed { path, card ->
                    val x = left + (cardWidth + gap) * path
                    slot(x, pathsY, cardWidth, mark = { drawCentredIcon(icons.map, 0.7f, markColour) })
                    if (card != null) {
                        card(card.card ?: "joker", card.card ?: Card(Rank.Ace, Suit.Spades), x, pathsY, cardWidth, joker = card.isJoker)
                    }
                    tap(x, pathsY, cardWidth, cardHeight) {
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
            }
            // What's been collected, over the paths.
            val cardHeight = board * CARD * CARD_ASPECT
            BoardText(
                text = "A: ${state.aces}  •  T: ${state.trinkets}",
                modifier = Modifier.fillMaxWidth().offset(y = board / 2 - cardHeight / 2 + cardHeight * 0.3f - 22.dp),
            )
        }
    }
}

@WearGamePreview
@Composable
private fun OuroborosPreview() = GamePreview(Ouroboros)
