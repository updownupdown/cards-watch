package com.updownupdown.cards.watch.games.lycan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.BoardText
import com.updownupdown.cards.watch.cards.CARD_ASPECT
import com.updownupdown.cards.watch.cards.CARD_FLIP_MS
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.LocalUndoing
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.Rating
import com.updownupdown.cards.watch.game.text
import com.updownupdown.cards.watch.game.tryMove
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.BottomActions
import com.updownupdown.cards.watch.ui.GameButton
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAsleep
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Lycan's deck has five copies of each rank, so cards carry an id. */
@Serializable
data class LycanCard(val id: Int, val card: Card) {
    val value get() = card.rank.value
}

/**
 * @param deck drawn from the end; its last card is the face-up next card.
 * @param pickedPlayer, pickedWolf the cards picked for a capture (not undo steps).
 * @param roundResult set once a round is over: the points it scored and who won.
 */
@Serializable
data class LycanState(
    val deck: List<LycanCard>,
    val player: List<LycanCard> = emptyList(),
    val wolf: List<LycanCard> = emptyList(),
    val playerScore: Int = 0,
    val wolfScore: Int = 0,
    val round: Int = 1,
    val roundResult: RoundResult? = null,
    val pickedPlayer: List<Int> = emptyList(),
    val pickedWolf: List<Int> = emptyList(),
)

@Serializable
data class RoundResult(val won: Boolean, val points: Int)

enum class Capture(@param:StringRes val label: Int) {
    Perfect(R.string.lycan_perfect),
    Sum(R.string.lycan_sum),
    Split(R.string.lycan_split),
    Over(R.string.lycan_over),
}

/** The phone's Medium deck: Aces to 9s, five of each. */
object Lycan : Game<LycanState> {
    private const val MAX_RANK = 9
    private const val HAND = 4
    const val TARGET = 100

    override val id = "lycan"
    override val thumbnail = R.drawable.thumb_lycan
    override val banner = R.drawable.banner_lycan
    override val art = R.drawable.art_lycan
    override val credit = ArtCredit("Lone Wolf", "Alfred Wierusz-Kowalski", null, "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:Alfred_Wierusz-Kowalski_-_Samotny_wilk.jpg")
    override val accent = R.color.game_lycan
    override val accentPale = R.color.game_lycan_pale
    override val title = R.string.lycan_title
    override val rating = Rating(difficulty = 85, complexity = 60, luck = 40)
    override val fourSuitColours = false
    override val stateSerializer: KSerializer<LycanState> = LycanState.serializer()

    override val rules = R.array.lycan_rules

    private fun buildDeck(random: Random): List<LycanCard> {
        val suits = Suit.entries
        var id = 0
        return (0 until 5).flatMap { copy ->
            (1..MAX_RANK).map { rank -> LycanCard(id++, Card(Rank.entries[rank - 1], suits[copy % suits.size])) }
        }.shuffled(random)
    }

    private fun deal(state: LycanState): LycanState {
        val deck = state.deck.toMutableList()
        val wolf = List(HAND) { deck.removeAt(deck.lastIndex) }
        val player = List(HAND) { deck.removeAt(deck.lastIndex) }
        return state.copy(
            deck = deck,
            wolf = wolf.sortedBy { it.value },
            player = player.sortedBy { it.value },
            roundResult = null,
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
    }

    override fun newGame(random: Random) = deal(LycanState(deck = buildDeck(random)))

    fun capture(player: List<LycanCard>, wolf: List<LycanCard>): Capture? {
        if (player.isEmpty() || wolf.isEmpty()) return null
        val mine = player.sumOf { it.value }
        val theirs = wolf.sumOf { it.value }
        return when {
            player.size == 1 && wolf.size == 1 -> when {
                mine == theirs -> Capture.Perfect
                mine > theirs -> Capture.Over
                else -> null
            }
            player.size >= 2 && wolf.size == 1 -> if (mine == theirs) Capture.Sum else null
            player.size == 1 && wolf.size >= 2 -> if (mine == theirs) Capture.Split else null
            else -> null
        }
    }

    private fun picked(state: LycanState) =
        state.player.filter { it.id in state.pickedPlayer } to state.wolf.filter { it.id in state.pickedWolf }

    fun pickedCapture(state: LycanState): Capture? {
        val (mine, theirs) = picked(state)
        return capture(mine, theirs)
    }

    private fun endRound(state: LycanState, won: Boolean): LycanState {
        val points = (if (won) state.player else state.wolf).sumOf { it.value }
        return state.copy(
            playerScore = state.playerScore + if (won) points else 0,
            wolfScore = state.wolfScore + if (won) 0 else points,
            roundResult = RoundResult(won, points),
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
    }

    fun doCapture(state: LycanState): LycanState? {
        if (state.roundResult != null) return null
        val (mine, theirs) = picked(state)
        val kind = capture(mine, theirs) ?: return null
        var player = state.player - mine.toSet()
        var wolf = state.wolf - theirs.toSet()
        val deck = state.deck.toMutableList()
        val (toPlayer, count) = when (kind) {
            Capture.Perfect, Capture.Sum -> true to 1
            Capture.Split -> false to 1
            Capture.Over -> false to mine.single().value - theirs.single().value
        }
        repeat(count) {
            val card = deck.removeLastOrNull() ?: return@repeat
            if (toPlayer) player = player + card else wolf = wolf + card
        }
        val next = state.copy(
            deck = deck,
            player = player.sortedBy { it.value },
            wolf = wolf.sortedBy { it.value },
            pickedPlayer = emptyList(),
            pickedWolf = emptyList(),
        )
        // An empty hand folds by itself: there's nothing left to capture with.
        return when {
            next.wolf.isEmpty() -> endRound(next, won = true)
            next.player.isEmpty() -> endRound(next, won = false)
            else -> next
        }
    }

    fun fold(state: LycanState): LycanState? = if (state.roundResult != null) null else endRound(state, won = false)

    fun nextRound(state: LycanState, random: Random = Random.Default): LycanState? {
        if (state.roundResult == null || outcome(state) != null) return null
        // Every card, captured ones included, is shuffled back for each round.
        return deal(state.copy(deck = buildDeck(random), player = emptyList(), wolf = emptyList(), round = state.round + 1))
    }

    fun togglePlayer(state: LycanState, id: Int) = state.copy(
        pickedPlayer = if (id in state.pickedPlayer) state.pickedPlayer - id else state.pickedPlayer + id,
    )

    fun toggleWolf(state: LycanState, id: Int) = state.copy(
        pickedWolf = if (id in state.pickedWolf) state.pickedWolf - id else state.pickedWolf + id,
    )

    override fun isUndoStep(previous: LycanState, next: LycanState) =
        previous.copy(pickedPlayer = emptyList(), pickedWolf = emptyList()) !=
            next.copy(pickedPlayer = emptyList(), pickedWolf = emptyList())

    override val scoreName = R.string.score_score

    override fun score(state: LycanState) = state.playerScore

    override fun outcome(state: LycanState) = when {
        state.playerScore >= TARGET -> Outcome(true, text(R.string.lycan_won, state.playerScore, state.wolfScore))
        state.wolfScore >= TARGET -> Outcome(false, text(R.string.lycan_lost, state.wolfScore, state.playerScore))
        else -> null
    }

    /** The one button under the board: the capture picked, or "Select" until there is one. */
    override fun actions(state: LycanState): List<GameAction<LycanState>> {
        // A finished round moves on by itself.
        if (state.roundResult != null) return emptyList()
        val (mine, theirs) = picked(state)
        return listOf(
            when (val kind = pickedCapture(state)) {
                null -> GameAction(text(R.string.lycan_select), enabled = false) { it }
                else -> GameAction(
                    label = if (kind == Capture.Over) text(R.string.lycan_over_by, mine.single().value - theirs.single().value) else text(kind.label),
                    color = when (kind) {
                        Capture.Perfect -> AppColors.ButtonGreen
                        Capture.Sum -> AppColors.ButtonPurple
                        Capture.Split -> AppColors.ButtonPink
                        Capture.Over -> AppColors.ButtonOrange
                    },
                ) { doCapture(it) ?: it }
            },
        )
    }

    @Composable
    override fun Bottom(state: LycanState, play: (LycanState) -> Unit, feedback: Feedback, size: DpSize) {
        BottomActions(actions(state), null, state, play, feedback, size)
    }

    /** The gap between the three rows, and the cards' height × the most that would fit. */
    private val ROW_GAP = 4.4.dp
    private const val CARD_SCALE = 0.93f
    /** The scores' size, × the names over them. */
    private const val SCORE_SCALE = 1.53f
    /** Each score's width, × the room beside the draw pile. */
    private const val SCORE_WIDTH = 0.3f
    /** The least gap between the Fold button and the cards above and below it. */
    private val FOLD_GAP = 5.dp
    /** How long after the top card is drawn the one under it turns up. */
    private const val DRAW_FLIP_MS = 250L
    /** How long that card takes to turn over. */
    private const val FLIP_MS = CARD_FLIP_MS * 2
    /** How long a round's result stays before the next round starts by itself. */
    private const val NEXT_ROUND_MS = 2000L

    private fun cardHeight(board: Dp) = (board - ROW_GAP * 2) / 3 * CARD_SCALE

    /**
     * The draw pile's left edge on a board of side [board]: past the board's
     * edge, to where the pile's corners just clear the round screen.
     */
    private fun drawPileX(board: Dp, cardWidth: Dp, cardHeight: Dp): Dp {
        val edge = pileEdge(cardWidth)
        val top = (board - cardHeight * 3 - ROW_GAP * 2) / 2
        val y = top + cardHeight + ROW_GAP - edge / 2
        return RoundScreen(board).left(y, y + cardHeight + edge)
    }

    /** The scores' text: larger than the names over them. */
    @Composable
    private fun scoreStyle(color: Color): TextStyle {
        val style = MaterialTheme.typography.labelSmall
        return style.copy(fontSize = style.fontSize * SCORE_SCALE, lineHeight = style.lineHeight * SCORE_SCALE, color = color, textAlign = TextAlign.Center)
    }

    @Composable
    private fun Score(name: String, score: Int, width: Dp) {
        Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
            // One line, shrinking for a long name (Serigala).
            val label = MaterialTheme.typography.labelSmall
            BasicText(
                text = name,
                style = label.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = label.fontSize * 0.6f, maxFontSize = label.fontSize),
            )
            val style = scoreStyle(Color.White)
            // Shrinks by up to 15%, so a long score keeps the cell's width.
            BasicText(
                text = "$score",
                style = style,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.85f, maxFontSize = style.fontSize),
            )
        }
    }

    @Composable
    override fun Board(state: LycanState, play: (LycanState) -> Unit, feedback: Feedback, modifier: Modifier) {
        val playing = state.roundResult == null && outcome(state) == null
        val result = state.roundResult
        val board = LocalBoardSize.current

        // A finished round moves on by itself, after a while with the watch awake (asleep, the
        // move would wait to be shown on waking, and seem to happen twice).
        val asleep = LocalAsleep.current
        LaunchedEffect(state.round, result, asleep) {
            if (result != null && !asleep && outcome(state) == null) {
                delay(NEXT_ROUND_MS)
                nextRound(state)?.let(play)
            }
        }

        // The draw pile's top card is face up. When it's drawn, the one under
        // it turns up a moment later (at once for an undo, which puts it back,
        // and for a new deal or round, whose deck is dealt with its top card up).
        val top = state.deck.lastOrNull()?.id
        val size = state.deck.size
        var turned by remember { mutableStateOf(top) }
        var turnedSize by remember { mutableIntStateOf(size) }
        val drawn = size < turnedSize
        LaunchedEffect(top, size) {
            if (turned != top && size < turnedSize) delay(DRAW_FLIP_MS)
            turned = top
            turnedSize = size
        }
        val faceUp = if (drawn && !LocalUndoing.current) turned else top

        CardTable(
            modifier,
            overlay = {
                // Beside the draw pile on the left: the scores, either side of Fold (or of what the round scored).
                val cardHeight = cardHeight(board)
                val cardWidth = cardHeight / CARD_ASPECT
                val left = drawPileX(board, cardWidth, cardHeight) + cardWidth + pileEdge(cardWidth) + 2.dp
                // Fold is as tall as the row and the gaps round it allow, short of the hands above and below.
                val foldHeight = cardHeight + (ROW_GAP - FOLD_GAP) * 2
                val width = board - left
                val scoreWidth = width * SCORE_WIDTH
                Row(
                    Modifier.offset(x = left, y = (board - foldHeight) / 2).size(width, foldHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Score(stringResource(R.string.lycan_wolf), state.wolfScore, scoreWidth)
                    // Fold and the round's result share one place of a fixed size, so neither moves the scores
                    // and the result stays still; one replaces the other at once.
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (result != null) {
                            // Set like a score: the name, then the points.
                            val color = if (result.won) AppColors.Good else AppColors.Bad
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BoardText(stringResource(if (result.won) R.string.lycan_you else R.string.lycan_wolf), color = color)
                                BasicText("+${result.points}", style = scoreStyle(color), maxLines = 1)
                            }
                        } else {
                            GameButton(stringResource(R.string.lycan_fold), { feedback.tryMove(fold(state), play) }, width - scoreWidth * 2 - FOLD_GAP * 2, foldHeight, enabled = outcome(state) == null)
                        }
                    }
                    Score(stringResource(R.string.lycan_you), state.playerScore, scoreWidth)
                }
            },
        ) {
            // Three rows of one card size: the Wolf's hand, the draw pile, yours.
            val cardHeight = cardHeight(height)
            val cardWidth = cardHeight / CARD_ASPECT
            val top = (height - cardHeight * 3 - ROW_GAP * 2) / 2
            val gap = 4.dp
            // The picked cards wear the colour of the capture's button, or blue while there is none.
            val picking = when (pickedCapture(state)) {
                Capture.Perfect -> Selection.Green
                Capture.Sum -> Selection.Purple
                Capture.Split -> Selection.Pink
                Capture.Over -> Selection.Orange
                null -> Selection.Neutral
            }
            fun hand(cards: List<LycanCard>, picked: List<Int>, y: Dp, toggle: (LycanState, Int) -> LycanState) {
                // One row, overlapping once it outgrows the width.
                val step = if (cards.size > 1) min(cardWidth + gap, (width - cardWidth) / (cards.size - 1)) else 0.dp
                val left = (width - step * (cards.size - 1) - cardWidth) / 2
                cards.forEachIndexed { i, c ->
                    val x = left + step * i
                    card(c.id, c.card, x, y, cardWidth, z = i.toFloat(), selection = if (c.id in picked) picking else null)
                    tap(x, y, if (i == cards.lastIndex) cardWidth else step, cardHeight) {
                        if (playing) feedback.tryMove(toggle(state, c.id), play) else feedback.reject()
                    }
                }
            }
            hand(state.wolf, state.pickedWolf, top, ::toggleWolf)
            // The draw pile, as far left as it goes.
            pile(
                cards = state.deck.map { it.card },
                x = drawPileX(width, cardWidth, cardHeight),
                y = top + cardHeight + ROW_GAP - pileEdge(cardWidth) / 2,
                cardWidth = cardWidth,
                keyOf = { state.deck[it].id },
                faceDown = { state.deck[it].id != faceUp },
                flipMs = FLIP_MS,
            )
            hand(state.player, state.pickedPlayer, top + (cardHeight + ROW_GAP) * 2, ::togglePlayer)
        }
    }
}

@WearGamePreview
@Composable
private fun LycanPreview() = GamePreview(Lycan)

