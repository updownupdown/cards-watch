package com.updownupdown.cards.watch.games.jailbreak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.CardColors
import com.updownupdown.cards.watch.cards.CardIcon
import com.updownupdown.cards.watch.cards.CardIcons
import com.updownupdown.cards.watch.cards.CardLayout
import com.updownupdown.cards.watch.cards.CardStyle
import com.updownupdown.cards.watch.cards.CardTable
import com.updownupdown.cards.watch.cards.RoundScreen
import com.updownupdown.cards.watch.cards.Selection
import com.updownupdown.cards.watch.cards.pileEdge
import com.updownupdown.cards.watch.cards.isRed
import com.updownupdown.cards.watch.cards.shuffledDeck
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.ui.AppColors
import com.updownupdown.cards.watch.ui.GamePreview
import com.updownupdown.cards.watch.ui.LocalAmbient
import com.updownupdown.cards.watch.ui.LocalBoardSize
import com.updownupdown.cards.watch.ui.WearGamePreview
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Piles list bottom to top; only a pile's top card is face up.
 *
 * @param piles the jail, clockwise from the top: walls at the even places, corners at the odd.
 * @param stock drawn from the end, a card at a time, onto the [joker]'s pile in the middle.
 */
@Serializable
data class JailbreakState(
    val piles: List<List<Card>>,
    val stock: List<Card>,
    val joker: List<Card> = emptyList(),
)

/** The phone's Normal deal: four walls of six cards round the Joker's cell, and two cards in each corner. */
object Jailbreak : Game<JailbreakState> {
    const val PILES = 8
    private const val WALL_SIZE = 6
    private const val CORNER_SIZE = 2
    const val MAX_JOKER_CARDS = 3

    /** The Joker's pile as a card's source; the jail's piles are 0 until [PILES]. */
    const val JOKER = PILES

    private fun isWall(pile: Int) = pile % 2 == 0

    override val id = "jailbreak"
    override val thumbnail = R.drawable.thumb_jailbreak
    override val banner = R.drawable.banner_jailbreak
    override val accent = R.color.game_jailbreak
    override val accentPale = R.color.game_jailbreak_pale
    override val title = "Jailbreak"
    override val stateSerializer: KSerializer<JailbreakState> = JailbreakState.serializer()

    override val rules = listOf(
        "## Objective",
        "Free the Joker from the middle: empty one of the four **walls** (the piles above, below, left and right of the middle) while the middle is empty too.",
        "**Lose** when no cards can be removed and none can be drawn.",
        "## Gameplay",
        "Tap cards to select them. Selected cards are discarded when the **total value of red and black cards** is the same.",
        "When stuck, tap **Draw** to put a card in the middle.",
        "The middle holds at most **three** cards.",
    )

    override fun newGame(random: Random): JailbreakState {
        val deck = shuffledDeck(random).toMutableList()
        val piles = List(PILES) { pile -> List(if (isWall(pile)) WALL_SIZE else CORNER_SIZE) { deck.removeAt(deck.lastIndex) } }
        return JailbreakState(piles, deck)
    }

    /** The card [source] offers: the Joker's top card, or a pile's. */
    fun top(state: JailbreakState, source: Int): Card? =
        if (source == JOKER) state.joker.lastOrNull() else state.piles[source].lastOrNull()

    /** What the cards picked add up to: the red ones, then the black. */
    fun totals(state: JailbreakState, picked: Collection<Int>): Pair<Int, Int> {
        val (red, black) = picked.mapNotNull { top(state, it) }.partition { it.isRed }
        return red.sumOf { it.rank.value } to black.sumOf { it.rank.value }
    }

    fun isBalanced(state: JailbreakState, picked: Collection<Int>): Boolean {
        val (red, black) = totals(state, picked)
        return red > 0 && red == black
    }

    /** Discards the top cards of [picked], if their reds and blacks add up to the same. */
    fun remove(state: JailbreakState, picked: Set<Int>): JailbreakState? {
        if (outcome(state) != null || !isBalanced(state, picked)) return null
        return state.copy(
            piles = state.piles.mapIndexed { i, pile -> if (i in picked) pile.dropLast(1) else pile },
            joker = if (JOKER in picked) state.joker.dropLast(1) else state.joker,
        )
    }

    private fun canDraw(state: JailbreakState) = state.stock.isNotEmpty() && state.joker.size < MAX_JOKER_CARDS

    fun draw(state: JailbreakState): JailbreakState? {
        if (outcome(state) != null || !canDraw(state)) return null
        return state.copy(stock = state.stock.dropLast(1), joker = state.joker + state.stock.last())
    }

    /** Every total some of [cards] add up to. */
    private fun subsetSums(cards: List<Card>): Set<Int> =
        cards.fold(setOf(0)) { sums, card -> sums + sums.map { it + card.rank.value } } - 0

    private fun hasMatch(state: JailbreakState): Boolean {
        val (red, black) = (0..JOKER).mapNotNull { top(state, it) }.partition { it.isRed }
        return subsetSums(red).any { it in subsetSums(black) }
    }

    override fun outcome(state: JailbreakState) = when {
        state.joker.isEmpty() && state.piles.withIndex().any { (i, pile) -> isWall(i) && pile.isEmpty() } -> Outcome(true, "The Joker escaped!")
        !canDraw(state) && !hasMatch(state) -> Outcome(false, "No moves remain")
        else -> null
    }

    override fun actions(state: JailbreakState) = listOf(
        GameAction<JailbreakState>("Draw (${state.stock.size})", enabled = canDraw(state)) { draw(it) ?: it },
    )

    private const val SIDE = 3
    private const val GAP = 0.12f
    /** The cards' height, × the board's: short of its foot, to stand clear of the Draw button. */
    private const val BOARD_HEIGHT = 0.9f

    // Board cells on a 3×3 grid, clockwise from the top; the Joker's is the middle.
    private val PILE_CELLS = listOf(1, 2, 5, 8, 7, 6, 3, 0)
    private const val JOKER_CELL = 4

    private fun grid(board: Dp) = CardLayout(SIDE, SIDE, board, board * BOARD_HEIGHT, gap = GAP, extra = CardStyle.PILE_OFFSET)

    /** The sums' size, × the usual label's, and how far right of the middle of their room they sit, × a card's width. */
    private const val SUM_SCALE = 1.687f
    private const val SUM_SHIFT = 0.2f

    /** The Joker in the empty middle, × a card's width. */
    private const val JOKER_SIZE = 0.7f

    @Composable
    private fun Sum(value: Int, color: Color) {
        val style = MaterialTheme.typography.labelMedium
        BasicText(
            text = "$value",
            style = style.copy(
                color = color.copy(alpha = if (value == 0) 0.3f else 1f),
                fontSize = style.fontSize * SUM_SCALE,
                lineHeight = style.fontSize * SUM_SCALE,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }

    /** What the picked cards add up to, red over black, between the screen's left edge and the jail. */
    @Composable
    private fun Sums(red: Int, black: Int, board: Dp) {
        val grid = grid(board)
        val y = grid.y(1)
        val left = RoundScreen(board).left(y, y + grid.cellHeight)
        Box(
            Modifier.offset(left + grid.cardWidth * SUM_SHIFT, y).wrapContentSize(Alignment.TopStart, unbounded = true).size(grid.x(0) - grid.gap - left, grid.cellHeight),
            contentAlignment = Alignment.Center,
        ) {
            // As tall as its two lines, even where that's more than a card.
            Column(Modifier.wrapContentHeight(unbounded = true), horizontalAlignment = Alignment.CenterHorizontally) {
                Sum(red, AppColors.LabelRed)
                Sum(black, AppColors.LabelBlack)
            }
        }
    }

    /** The Joker in [color], centred on its slot. */
    private fun jokerMark(icon: CardIcon, color: Color): DrawScope.() -> Unit = {
        val scale = size.width * JOKER_SIZE / icon.viewBox
        withTransform({
            translate(center.x - icon.bounds.center.x * scale, center.y - icon.bounds.center.y * scale)
            scale(scale, scale, Offset.Zero)
        }) {
            drawPath(icon.path, color)
        }
    }

    @Composable
    override fun Board(state: JailbreakState, play: (JailbreakState) -> Unit, feedback: Feedback, modifier: Modifier) {
        var picked by remember(state) { mutableStateOf(emptySet<Int>()) }
        val playing = outcome(state) == null
        val board = LocalBoardSize.current
        val ambient = LocalAmbient.current

        fun tap(source: Int) {
            if (!playing || top(state, source) == null) return feedback.reject()
            val next = if (source in picked) picked - source else picked + source
            feedback.move()
            // Once the reds and blacks picked add up to the same, they go.
            remove(state, next)?.let(play) ?: run { picked = next }
        }

        // The Joker, in its prison orange, on the middle's slot: under the cards drawn there, so it
        // stays put while they come and go.
        val joker = jokerMark(
            CardIcons.get(LocalContext.current.assets).joker,
            if (ambient) CardColors.Ambient.copy(alpha = 0.3f) else AppColors.Joker,
        )
        CardTable(
            modifier,
            overlay = {
                if (!ambient) {
                    val (red, black) = totals(state, picked)
                    Sums(red, black, board)
                }
            },
        ) {
            val grid = grid(width)
            fun place(cards: List<Card>, source: Int, cell: Int, faceDown: (Int) -> Boolean = { false }) = pile(
                cards = cards,
                x = grid.xOf(cell),
                y = grid.yOf(cell),
                cardWidth = grid.cardWidth,
                faceDown = faceDown,
                selection = if (source in picked) Selection.Neutral else null,
            ) { tap(source) }
            state.piles.forEachIndexed { i, pile -> place(pile, i, PILE_CELLS[i]) { it != pile.lastIndex } }
            place(state.joker, JOKER, JOKER_CELL)
            slot(grid.xOf(JOKER_CELL), grid.yOf(JOKER_CELL) + pileEdge(grid.cardWidth), grid.cardWidth, mark = joker)
        }
    }
}

@WearGamePreview
@Composable
private fun JailbreakPreview() = GamePreview(Jailbreak)

