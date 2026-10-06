package com.updownupdown.cards.watch.games.augury

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.GridPlaces
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Removal
import com.updownupdown.cards.watch.cards.ScrollingCards
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalBoardScroll
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.LocalGameAccent
import com.updownupdown.cards.watch.ui.SideGauge
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** A card on the path, and the cards turned away while it was the last. */
@Serializable
data class PathEntry(val card: Card, val rejected: List<Card> = emptyList())

/**
 * @param deck the cards still to come, the next first.
 * @param law the hidden law's name ([Law]).
 * @param streak correct guesses in a row.
 * @param missed the card that did fit, played after a wrong "none fit".
 */
@Serializable
data class AuguryState(
    val deck: List<Card>,
    val hand: List<Card>,
    val path: List<PathEntry>,
    val law: String,
    val streak: Int = 0,
    val missed: Card? = null,
)

/** The phone's Easy laws: which cards may follow the path (oldest first). */
enum class Law(val text: String, val test: (List<Card>, Card) -> Boolean) {
    AlternateColours("Must alternate colour", { path, card -> card.isRed != path.last().isRed }),
    SuitOrRank("Must be the same suit or rank", { path, card -> card.suit == path.last().suit || card.rank == path.last().rank }),
    OddEven("Must alternate between odd and even ranks", { path, card -> odd(card) != odd(path.last()) }),
    HigherUntilCourt("Must be a higher rank; anything can follow a J, Q or K", { path, card -> court(path.last()) || card.rank.value > path.last().rank.value }),
    WithinTwo("Must be within two ranks", { path, card -> abs(card.rank.value - path.last().rank.value) <= 2 }),
    SameColour("Must be the same colour", { path, card -> card.isRed == path.last().isRed }),
    DifferentSuit("Must be a different suit", { path, card -> card.suit != path.last().suit }),
    HighLowAlternate("Must alternate between high (8–K) and low (A–7)", { path, card -> high(card) != high(path.last()) }),
    CourtAlternate("Must alternate between court and number cards", { path, card -> court(card) != court(path.last()) }),
    LowerUntilAce("Must be a lower rank; anything can follow an Ace", { path, card -> path.last().rank == Rank.Ace || card.rank.value < path.last().rank.value }),
    FarApart("Must be at least three ranks apart", { path, card -> abs(card.rank.value - path.last().rank.value) >= 3 }),
    ParityLock("Odds must follow odds, evens must follow evens", { path, card -> odd(card) == odd(path.last()) }),
    WithinTwoWrap("Must be within two ranks (Ace and King are neighbours)", { path, card ->
        val gap = abs(card.rank.value - path.last().rank.value)
        min(gap, 13 - gap) <= 2
    }),
    SuitSequence("Must follow the suits in order: spades, hearts, diamonds, clubs", { path, card ->
        SPADES_FIRST.indexOf(card.suit) == (SPADES_FIRST.indexOf(path.last().suit) + 1) % SPADES_FIRST.size
    }),
    SameSuitAscender("The same suit must be higher, the other suit of its colour lower; the other colour can follow anything", { path, card ->
        val last = path.last()
        when {
            card.suit == last.suit -> card.rank.value > last.rank.value
            card.isRed == last.isRed -> card.rank.value < last.rank.value
            else -> true
        }
    }),
    RedHighBlackLow("Red cards must be 8 or higher, black cards 7 or lower", { _, card -> card.isRed == high(card) }),
}

private val SPADES_FIRST = listOf(Suit.Spades, Suit.Hearts, Suit.Diamonds, Suit.Clubs)
private fun odd(card: Card) = card.rank.value % 2 == 1
private fun high(card: Card) = card.rank.value >= 8
private fun court(card: Card) = card.rank.value > 10

/** Eleusis for one: work out the hidden law that decides which cards may follow. */
object Augury : Game<AuguryState> {
    const val COLUMNS = 5
    const val HAND = 4
    const val TARGET = 8

    override val id = "augury"
    override val thumbnail = R.drawable.thumb_augury
    override val banner = R.drawable.banner_augury
    override val accent = R.color.game_augury
    override val accentPale = R.color.game_augury_pale
    override val title = "Augury"
    override val rating = Rating(difficulty = 50, complexity = 35, luck = 35)
    override val stateSerializer: KSerializer<AuguryState> = AuguryState.serializer()

    // Nothing under the board: it sits in the middle, a little larger.
    override val boardFraction = 0.68f
    override val boardLift = 0f

    override val rules = listOf(
        "## Objective",
        "Work out the **hidden law** well enough to make **eight correct guesses in a row**. The arc at the left fills with each.",
        "## Gameplay",
        "Each game has a secret law about which cards may follow the last card(s) on the path.",
        "- Tap a card in your hand if you think it fits the law. If it does, it joins the path; if not, it lies greyed out beside the last card.",
        "- If you think no card in your hand fits, tap the crossed-out card. If you're right, you get a new hand; if a card did fit, it is played for you (shown in red), and your run of correct guesses is reset.",
        "For all laws: A = 1, Jacks = 11, Queens = 12, Kings = 13.",
        "## Laws",
        "- Must be the same colour",
        "- Must alternate colour",
        "- Must be the same suit or rank",
        "- Must be a different suit",
        "- Must alternate between odd and even ranks",
        "- Must be higher rank; anything can follow J, Q or K",
        "- Must be within two ranks",
        "- Must be within two ranks (Ace and King are neighbours)",
        "- Must be at least three ranks apart",
        "- Must alternate between high (8–K) and low (A–7)",
        "- Must alternate between court and number cards",
        "- Must be lower rank; anything can follow an Ace",
        "- Odds must follow odds, evens must follow evens",
        "- Must follow suits in order: spades, hearts, diamonds, clubs",
        "- Same suit as the last must be higher; the other suit of its colour must be lower; the other colour can follow anything",
        "- Red cards must be 8 or higher; black cards must be 7 or lower",
    )

    fun law(state: AuguryState) = runCatching { Law.valueOf(state.law) }.getOrDefault(Law.AlternateColours)

    fun fits(state: AuguryState, card: Card) = law(state).test(state.path.map { it.card }, card)

    /** The hand topped up to [HAND] from the deck, sorted by suit, then rank. */
    private fun refill(state: AuguryState): AuguryState {
        val needed = (HAND - state.hand.size).coerceAtMost(state.deck.size)
        val hand = (state.hand + state.deck.take(needed)).sortedWith(compareBy({ it.suit.ordinal }, { it.rank.value }))
        return state.copy(hand = hand, deck = state.deck.drop(needed))
    }

    override fun newGame(random: Random): AuguryState {
        val deck = shuffledDeck(random)
        return refill(AuguryState(deck.drop(1), emptyList(), listOf(PathEntry(deck.first())), Law.entries.random(random).name))
    }

    /** Plays [card] from the hand: onto the path if it fits, beside the last card if not. */
    fun playCard(state: AuguryState, card: Card): AuguryState? {
        if (outcome(state) != null || card !in state.hand) return null
        val rest = state.copy(hand = state.hand - card, missed = null)
        val next = if (fits(state, card)) {
            rest.copy(path = state.path + PathEntry(card), streak = state.streak + 1)
        } else {
            val last = state.path.last()
            rest.copy(path = state.path.dropLast(1) + last.copy(rejected = last.rejected + card), streak = 0)
        }
        return refill(next)
    }

    /** The card in hand that fits, which a wrong "none fit" plays. */
    fun fitting(state: AuguryState) = state.hand.firstOrNull { fits(state, it) }

    /** Declares that no card in the hand fits: a new hand if so, the fitting card played and the run reset if not. */
    fun noneFit(state: AuguryState): AuguryState? {
        if (outcome(state) != null || state.hand.isEmpty()) return null
        val card = fitting(state)
        return refill(
            if (card != null) {
                state.copy(hand = state.hand - card, path = state.path + PathEntry(card), streak = 0, missed = card)
            } else {
                state.copy(hand = emptyList(), streak = state.streak + 1, missed = null)
            },
        )
    }

    // No undoing a guess: the law would be too easy to test.
    override fun isUndoStep(previous: AuguryState, next: AuguryState) = false

    override fun outcome(state: AuguryState) = when {
        state.streak >= TARGET -> Outcome(true, "The law: ${law(state).text.lowercase()}")
        state.hand.isEmpty() -> Outcome(false, "The law: ${law(state).text.lowercase()}")
        else -> null
    }

    /** What lies on the grid, in order: each path card, then those turned away after it. */
    private enum class Kind { Played, Rejected, Missed }

    private fun cells(state: AuguryState) = state.path.flatMap { entry ->
        listOf(entry.card to if (entry.card == state.missed) Kind.Missed else Kind.Played) + entry.rejected.map { it to Kind.Rejected }
    }

    private const val GAP = 0.12f
    /** Room round the hand for a border. */
    private val PAD = 2.dp
    /** The gap between the grid's window and the hand, × the gap between cards. */
    private const val HAND_GAP = 3.5f

    @Composable
    override fun Board(state: AuguryState, play: (AuguryState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val playing = outcome(state) == null
        val scroll = LocalBoardScroll.current ?: rememberScrollState()
        val density = LocalDensity.current
        val board = LocalBoardSize.current
        val accent = LocalGameAccent.current
        val places = GridPlaces(COLUMNS, board, GAP, 0.dp)
        val handY = board - places.cardHeight - PAD
        val window = handY - places.gap * HAND_GAP
        val cells = cells(state)
        val rows = (cells.size + COLUMNS - 1) / COLUMNS

        // Where the last card played left the hand, in the grid's own coordinates, so it glides up from there.
        var origin by remember { mutableStateOf<Pair<Card, DpOffset>?>(null) }
        // A "none fit" waits a frame, so the hand's cards are set to slide off before they go.
        var declaring by remember { mutableStateOf(false) }
        LaunchedEffect(declaring) {
            if (declaring) {
                withFrameNanos {}
                noneFit(state)?.let(play)
                declaring = false
            }
        }
        // New cards on the grid are brought into view; a new deal starts from the top.
        LaunchedEffect(cells.size) {
            if (cells.size <= 1) scroll.scrollTo(0) else {
                withFrameNanos {}
                scroll.animateScrollTo(scroll.maxValue)
            }
        }
        fun takeFrom(card: Card) {
            val slot = state.hand.indexOf(card) + 1
            origin = card to DpOffset(places.x(slot), handY + with(density) { scroll.value.toDp() })
        }

        Box(modifier) {
            SideGauge(state.streak / TARGET.toFloat(), accent, board, boardFraction, boardLift)
            ScrollingCards(COLUMNS, rows, board, window, gap = GAP, centre = true, scroll = scroll) { grid ->
                cells.forEachIndexed { index, (card, kind) ->
                    card(
                        key = card,
                        card = card,
                        x = grid.xOf(index),
                        y = grid.yOf(index),
                        width = grid.cardWidth,
                        overlay = if (kind == Kind.Missed) CardOverlay.Bad else null,
                        // A card turned away lies faded, on black.
                        face = if (kind == Kind.Rejected) Color.Black else null,
                        alpha = if (kind == Kind.Rejected) 0.5f else 1f,
                        from = origin?.takeIf { it.first == card }?.second,
                        turnFrom = false,
                    )
                }
            }
            // The hand, under the grid, and the "none fit" card before it.
            CardTable(Modifier.offset(0.dp, handY).size(board, places.cardHeight)) {
                val keep = if (declaring) fitting(state) else null
                state.hand.forEachIndexed { i, card ->
                    val x = places.x(i + 1)
                    card(
                        key = card,
                        card = card,
                        x = x,
                        y = 0.dp,
                        width = places.cardWidth,
                        // Played, it goes on from the grid; declared away, the hand slides off to the right.
                        removal = if (declaring && card != keep) Removal.Discard else Removal.Gone,
                    )
                    tap(x, 0.dp, places.cardWidth, places.cardHeight) {
                        val next = playCard(state, card)
                        if (next == null) feedback.reject() else {
                            takeFrom(card)
                            feedback.move()
                            play(next)
                        }
                    }
                }
            }
            GameButton(
                label = "None fit",
                onClick = {
                    if (!playing || declaring) feedback.reject() else {
                        fitting(state)?.let(::takeFrom)
                        feedback.move()
                        declaring = true
                    }
                },
                width = places.cardWidth,
                height = places.cardHeight,
                enabled = playing,
                modifier = Modifier.offset(0.dp, handY),
                color = accent,
                shape = RoundedCornerShape(places.cardWidth * CardStyle.CORNER),
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_not_available),
                        contentDescription = null,
                        tint = AppColors.GameButtonText,
                        modifier = Modifier.size(places.cardWidth * 0.6f),
                    )
                },
            )
        }
    }
}

@WearGamePreview
@Composable
private fun AuguryPreview() = GamePreview(Augury)
