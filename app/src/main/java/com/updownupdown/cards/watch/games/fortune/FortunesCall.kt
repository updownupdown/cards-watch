package com.updownupdown.cards.watch.games.fortune

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.CARD_FLIP_MS
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardOverlay
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.LocalUndoing
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class Call(val label: String, val points: Int) {
    Red("Red", 1),
    Black("Black", 1),
    Higher("Higher", 2),
    Lower("Lower", 2),
}

/**
 * @param anchor the face-up card the next call is made against.
 * @param previous the card before it, shown with whether the call was right.
 */
@Serializable
data class FortuneState(
    val deck: List<Card>,
    val anchor: Card,
    val previous: Card? = null,
    val lastCorrect: Boolean? = null,
    val score: Int = 0,
    val misses: Int = 0,
)

object FortunesCall : Game<FortuneState> {
    const val TARGET = 20
    const val MAX_MISSES = 3

    override val id = "fortunes-call"
    override val thumbnail = R.drawable.thumb_fortunes_call
    override val banner = R.drawable.banner_fortunes_call
    override val accent = R.color.game_fortunes_call
    override val accentPale = R.color.game_fortunes_call_pale
    override val title = "Fortune's Call"
    override val stateSerializer: KSerializer<FortuneState> = FortuneState.serializer()

    override val rules = listOf(
        "## Objective",
        "Score 20 points by making correct guesses before making three wrong calls.",
        "## Gameplay",
        "Predict the colour of the face-down card, or its rank relative to the face up card.",
        "Correct **Red** or **Black** guesses score 1 point.",
        "Correct **Higher** or **Lower** guesses score 2 points.",
        "Equal ranks are neither higher nor lower.",
    )

    override fun newGame(random: Random): FortuneState {
        val deck = shuffledDeck(random)
        return FortuneState(deck = deck.drop(1), anchor = deck.first())
    }

    fun matches(call: Call, anchor: Card, next: Card) = when (call) {
        Call.Red -> next.isRed
        Call.Black -> !next.isRed
        Call.Higher -> next.rank.value > anchor.rank.value
        Call.Lower -> next.rank.value < anchor.rank.value
    }

    fun call(state: FortuneState, call: Call): FortuneState? {
        if (outcome(state) != null) return null
        val next = state.deck.firstOrNull() ?: return null
        val correct = matches(call, state.anchor, next)
        return FortuneState(
            deck = state.deck.drop(1),
            anchor = next,
            previous = state.anchor,
            lastCorrect = correct,
            score = state.score + if (correct) call.points else 0,
            misses = state.misses + if (correct) 0 else 1,
        )
    }

    override fun outcome(state: FortuneState) = when {
        state.score >= TARGET -> Outcome(true, "You reached $TARGET points!")
        state.misses >= MAX_MISSES -> Outcome(false, "Three misses")
        state.deck.isEmpty() -> Outcome(false, "Out of cards")
        else -> null
    }

    /** A card's height, × the board's, and the gap between the two cards. */
    private const val CARD_HEIGHT = 0.39f
    private val CARD_GAP = 12.dp
    /** The gap between a card and the buttons over and under it. */
    private val GAP = 15.dp
    /** The buttons' corners. */
    private val BUTTON_CORNER = 10.dp

    // Nothing sits under the board, so it's in the middle of the screen.
    override val boardLift = 0f

    /** The two cards, centred, and the buttons over and under each, as wide as a card. */
    private class Places(board: Dp) {
        val cardHeight = board * CARD_HEIGHT
        val cardWidth = cardHeight / CARD_ASPECT
        val left = board / 2 - cardWidth - CARD_GAP / 2
        val right = board / 2 + CARD_GAP / 2
        val cardY = (board - cardHeight) / 2
        /** The buttons' height: what the board has left over the card and its two gaps, shared. */
        val button = (board - cardHeight - GAP * 2) / 2
        /** The lower buttons' top. */
        val footY = board - button
    }

    // The points and misses: two arcs on the screen's left, filling upwards, the points' the larger.
    private const val GAUGE_SWEEP = 46f
    /** How many degrees shorter the misses' arc is than the points'. */
    private const val MISSES_SHORTER = 20f
    private val GAUGE_WIDTH = 7.5.dp
    private val GAUGE_EDGE = 9.dp
    private val GAUGE_GAP = 8.5.dp
    /** The buttons' letters, × the usual button text. */
    private const val LETTER_SCALE = 1.2f

    @Composable
    private fun Letter(text: String) {
        val style = MaterialTheme.typography.labelMedium
        BasicText(
            text = text,
            style = style.copy(color = AppColors.ListButtonText, fontSize = style.fontSize * LETTER_SCALE, lineHeight = style.lineHeight * LETTER_SCALE),
        )
    }

    /** A black arrow, up or down. */
    @Composable
    private fun Arrow(up: Boolean) {
        val ink = Color.Black
        Canvas(Modifier.size(14.dp)) {
            val line = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tip = if (up) 0f else size.height
            val tail = size.height - tip
            val head = if (up) size.height * 0.45f else size.height * 0.55f
            drawLine(ink, Offset(size.width / 2, tail), Offset(size.width / 2, tip), line.width, StrokeCap.Round)
            drawLine(ink, Offset(0f, head), Offset(size.width / 2, tip), line.width, StrokeCap.Round)
            drawLine(ink, Offset(size.width, head), Offset(size.width / 2, tip), line.width, StrokeCap.Round)
        }
    }

    /** The points (green, the outer arc) and the misses (red, inside it). */
    @Composable
    private fun Gauges(state: FortuneState, board: Dp) {
        val screen = RoundScreen(board, boardLift)
        val points by animateFloatAsState((state.score / TARGET.toFloat()).coerceIn(0f, 1f), tween(CARD_MOVE_MS), label = "points")
        val misses by animateFloatAsState((state.misses / MAX_MISSES.toFloat()).coerceIn(0f, 1f), tween(CARD_MOVE_MS), label = "misses")
        val outer = screen.size / 2 - GAUGE_EDGE - GAUGE_WIDTH / 2
        val inner = outer - GAUGE_WIDTH - GAUGE_GAP
        Canvas(Modifier.offset((board - screen.size) / 2, screen.middleY - screen.size / 2).wrapContentSize(Alignment.TopStart, unbounded = true).size(screen.size)) {
            listOf(
                Triple(outer, GAUGE_SWEEP, points to AppColors.Good),
                Triple(inner, GAUGE_SWEEP - MISSES_SHORTER, misses to AppColors.Bad),
            ).forEach { (radius, sweep, fill) ->
                // Centred on the left, level with the screen's middle.
                val start = 180f - sweep / 2
                val corner = Offset(center.x - radius.toPx(), center.y - radius.toPx())
                val box = Size(radius.toPx() * 2, radius.toPx() * 2)
                val line = Stroke(GAUGE_WIDTH.toPx(), cap = StrokeCap.Round)
                drawArc(AppColors.Track, start, sweep, false, corner, box, style = line)
                // From the bottom end, up the left side.
                if (fill.first > 0f) drawArc(fill.second, start, sweep * fill.first, false, corner, box, style = line)
            }
        }
    }

    // After a call: the called card turns over and shows for a while, the
    // card it was compared with leaves, the called card takes its place, and
    // after a pause the next card to call arrives.
    private const val SHOW_MS = 600L
    private const val FOLLOW_MS = 100L
    private const val NEXT_MS = 300L

    /** The steps after a call, in order; [Ready] is waiting for the next one. */
    private enum class Step { Shown, Left, Moved, Ready }

    @Composable
    override fun Board(state: FortuneState, play: (FortuneState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val over = outcome(state) != null
        // The board as it was first shown is at rest; so is an undone one, which just runs back.
        val first = remember { state }
        val undoing = LocalUndoing.current
        var step by remember(state) { mutableStateOf(if (state === first || state.previous == null || undoing) Step.Ready else Step.Shown) }
        LaunchedEffect(state) {
            if (step != Step.Ready) {
                delay(CARD_FLIP_MS + SHOW_MS)
                step = Step.Left
                delay(FOLLOW_MS)
                step = Step.Moved
                delay(CARD_MOVE_MS + NEXT_MS)
                step = Step.Ready
            }
        }
        val ready = step == Step.Ready && !over
        val board = LocalBoardSize.current
        val ambient = LocalAmbient.current
        CardTable(
            modifier,
            overlay = {
                val places = Places(board)
                // The calls round the cards: colour above and below the left one, rank over and under the right.
                listOf(
                    Triple(Call.Red, places.left, 0.dp),
                    Triple(Call.Black, places.left, places.footY),
                    Triple(Call.Higher, places.right, 0.dp),
                    Triple(Call.Lower, places.right, places.footY),
                ).forEach { (call, x, y) ->
                    GameButton(
                        label = call.label,
                        width = places.cardWidth,
                        height = places.button,
                        shape = RoundedCornerShape(BUTTON_CORNER),
                        enabled = ready,
                        color = when (call) {
                            Call.Red -> AppColors.Bad
                            Call.Black -> AppColors.Track
                            else -> AppColors.ButtonBlue
                        },
                        textColor = AppColors.ListButtonText,
                        modifier = Modifier.offset(x, y),
                        icon = when (call) {
                            Call.Red -> ({ Letter("R") })
                            Call.Black -> ({ Letter("B") })
                            else -> ({ Arrow(up = call == Call.Higher) })
                        },
                        onClick = {
                            val next = call(state, call)
                            when {
                                next == null -> feedback.reject()
                                next.lastCorrect == true -> feedback.move().also { play(next) }
                                else -> feedback.reject().also { play(next) }
                            }
                        },
                    )
                }
                if (!ambient) Gauges(state, board)
            },
        ) {
            val places = Places(width)
            val cardWidth = places.cardWidth
            val left = places.left
            val right = places.right
            val y = places.cardY
            // The card just compared with stays on the right while the called card is shown.
            if (step == Step.Shown) state.previous?.let { card(it, it, right, y, cardWidth) }
            // The card to compare with: on the left, just turned over, until it moves across.
            // Green or red while it's shown, for a right or wrong call.
            val called = when {
                step >= Step.Moved -> null
                state.lastCorrect == true -> CardOverlay.Good
                else -> CardOverlay.Bad
            }
            card(state.anchor, state.anchor, if (step < Step.Moved) left else right, y, cardWidth, overlay = called)
            // The card to call, face down.
            if (step == Step.Ready && !over) state.deck.firstOrNull()?.let { card(it, it, left, y, cardWidth, faceDown = true) }
        }
    }
}
