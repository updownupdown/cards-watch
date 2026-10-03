package com.updownupdown.cards.watch.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.LocalDeal
import com.updownupdown.cards.watch.cards.LocalUndoing
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.formatTime
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far towards the Reset and Undo buttons a drag must go to stay there, as a fraction of the way. */
private const val SNAP_DRAG = 0.6f
/** The same for a flick towards them, which needs less. */
private const val SNAP_FLICK = 0.4f
/** The slowest release that counts as a flick, in dp a second. */
private val FLICK_SPEED = 600.dp
/** How long the crown may rest between the board and the buttons before the view snaps. */
private const val SETTLE_MS = 400L

/** The Reset and Undo buttons: the gap above them, their height, and where their middle sits once scrolled to. */
private val ACTIONS_TOP = 14.dp
private val ACTIONS_HEIGHT = 40.dp
private const val ACTIONS_AT = 0.72f
/** How long the reset button's hint shows. */
private const val HINT_MS = 2500L
/** The gap between the result's message, its buttons and the score, and the buttons' height. */
private val RESULT_GAP = 16.dp
private val RESULT_BUTTON = 44.dp
/** The space between those buttons and the game's title, where the reset hint shows. */
private val RULES_TOP = 30.dp
/** How long after the last move the result comes up, so the move can be seen. */
private const val RESULT_DELAY_MS = CARD_MOVE_MS + 500L

/**
 * How far a board with more cards than it has room for (Accordion) is scrolled:
 * such a board scrolls by this, so the crown can turn it too. Null off the
 * game's screen (asleep), where it keeps its own.
 */
val LocalBoardScroll = compositionLocalOf<ScrollState?> { null }

/** Whether a board scrolls by this: until one is laid out with it, it would take any scroll, having no end. */
private val ScrollState.inUse: Boolean get() = viewportSize > 0

/**
 * How far down the view snaps to from the board, on a screen [page] pixels
 * high: enough to bring the Reset and Undo buttons up under the board.
 */
internal fun actionsSnap(page: Int, density: Density): Int =
    with(density) { (page * (1 - ACTIONS_AT) + (ACTIONS_TOP + ACTIONS_HEIGHT / 2).toPx()).toInt() }

/**
 * A game: the board fills the screen, with the time above it. A short scroll
 * down (by touch or the crown) brings up Reset and Undo, and the game's title
 * and rules follow. The view snaps to the board or to those buttons, never in
 * between, so a small stray drag on the board springs back. Swiping right
 * goes home.
 */
@Composable
fun <S : Any> GameScreen(
    session: GameSession<S>,
    onHome: () -> Unit,
    scroll: ScrollState = rememberScrollState(),
    stats: GameStats = GameStats(),
) {
    // The game's buttons and headings take its pale colour.
    CompositionLocalProvider(LocalGameAccent provides colorResource(session.game.accentPale)) {
        GameScreenContent(session, onHome, scroll, stats)
    }
}

@Composable
private fun <S : Any> GameScreenContent(session: GameSession<S>, onHome: () -> Unit, scroll: ScrollState, stats: GameStats) {
    val haptics = rememberHaptics()
    val game = session.game
    val state = session.state
    val outcome = session.outcome

    // Buzz when a move ends the game, but not when opening an ended game,
    // then show the result once the last move has played out.
    var wasOver by remember { mutableStateOf(outcome != null) }
    var showResult by remember { mutableStateOf(false) }
    // Coming back to a finished game (having left its result unanswered) starts a new one.
    val cameBackOver = remember { outcome != null }
    LaunchedEffect(Unit) {
        if (cameBackOver) session.newGame()
    }
    LaunchedEffect(outcome) {
        if (outcome == null) {
            showResult = false
        } else if (!wasOver) {
            if (outcome.won) haptics.win() else haptics.lose()
            delay(RESULT_DELAY_MS)
            showResult = true
        }
        wasOver = outcome != null
    }

    // The deal's clock runs while its screen is up.
    LifecycleResumeEffect(session) {
        session.resume()
        onPauseOrDispose { session.pause() }
    }

    val scope = rememberCoroutineScope()
    val boardScroll = rememberScrollState()
    val toBoard: () -> Unit = { scope.launch { scroll.animateScrollTo(0) } }

    // No time from the scaffold: the board carries its own, so it scrolls away with it.
    ScreenScaffold(scrollState = scroll, timeText = {}) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val stop = actionsSnap(constraints.maxHeight, density)
            val flick = with(density) { FLICK_SPEED.toPx() }
            val rulesFling = ScrollableDefaults.flingBehavior()
            val fling = remember(scroll, stop, flick, rulesFling) { BoardSnap(scroll, stop, flick, rulesFling) }
            // The crown turns a board that scrolls (Accordion) to its foot before it moves the
            // view, and brings the view back to the board before the board's cards.
            val crown = remember(scroll, boardScroll) {
                ScrollableState { delta ->
                    // Most boards don't scroll, and the board's state, never laid out, would take everything.
                    if (!boardScroll.inUse) return@ScrollableState scroll.dispatchRawDelta(delta)
                    val (first, then) = if (delta > 0) boardScroll to scroll else scroll to boardScroll
                    val used = first.dispatchRawDelta(delta)
                    used + then.dispatchRawDelta(delta - used)
                }
            }
            val crownFling = remember(scroll, boardScroll, fling, rulesFling) { CrownFling(scroll, boardScroll, fling, rulesFling) }
            val crownFocus = remember { FocusRequester() }

            // The crown has no release to snap on: settle once it rests between the two.
            LaunchedEffect(scroll, stop) {
                var settling = false
                var rest: Job? = null
                snapshotFlow { scroll.value }.collect { at ->
                    if (settling) return@collect
                    rest?.cancel()
                    if (at in 1 until stop) {
                        rest = launch {
                            delay(SETTLE_MS)
                            // A finger still on the screen snaps when it lifts.
                            if (!scroll.isScrollInProgress) {
                                settling = true
                                try {
                                    scroll.animateScrollTo(if (scroll.value > stop / 2) stop else 0)
                                } finally {
                                    settling = false
                                }
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    // The result has to be answered first: no scrolling away from it.
                    .verticalScroll(scroll, enabled = !showResult, flingBehavior = fling)
                    .then(
                        if (showResult) {
                            Modifier
                        } else {
                            Modifier
                                .requestFocusOnHierarchyActive()
                                .rotaryScrollable(RotaryScrollableDefaults.behavior(crown, flingBehavior = crownFling), crownFocus)
                        },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight)) {
                    GameLayout(
                        boardFraction = game.boardFraction,
                        boardLift = game.boardLift,
                        bottom = { size -> game.Bottom(state, session::play, haptics, size) },
                    ) {
                        CompositionLocalProvider(LocalUndoing provides session.undone, LocalDeal provides session.deal, LocalBoardScroll provides boardScroll) {
                            game.Board(state, session::play, haptics, Modifier.fillMaxSize())
                        }
                    }
                    if (outcome != null && showResult) {
                        ResultScreen(
                            outcome = outcome,
                            detail = listOfNotNull(
                                session.score?.let { "${game.scoreName}: $it" },
                                formatTime(session.timeMs),
                            ).joinToString("  -  "),
                            onNewGame = { haptics.move(); session.newGame() },
                            onHome = { haptics.press(); onHome() },
                        )
                    }
                }
                var hint by remember { mutableStateOf(false) }
                LaunchedEffect(hint) {
                    if (hint) {
                        delay(HINT_MS)
                        hint = false
                    }
                }
                Row(
                    // Narrow enough to clear the round screen where they come to rest.
                    Modifier.fillMaxWidth().padding(top = ACTIONS_TOP).padding(horizontal = this@BoxWithConstraints.maxWidth * 0.16f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ResetButton(
                        size = ACTIONS_HEIGHT,
                        onTap = { hint = true },
                        onDoubleTap = {
                            haptics.longPress()
                            hint = false
                            session.newGame()
                            toBoard()
                        },
                    )
                    ListButton("Undo", Modifier.weight(1f).height(ACTIONS_HEIGHT), enabled = session.canUndo) {
                        if (session.undo()) haptics.move() else haptics.reject()
                        toBoard()
                    }
                }
                // What the reset button wants, shown for a moment after a tap on it.
                Box(Modifier.fillMaxWidth().height(RULES_TOP), contentAlignment = Alignment.Center) {
                    if (hint) {
                        Text(
                            text = "Double-tap to reset game",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalGameAccent.current,
                        )
                    }
                }
                GameRules(game, stats, scroll, this@BoxWithConstraints.constraints.maxHeight, onTop = toBoard)
            }
        }
    }
}

/** The round button that deals a new game when it's tapped twice. One tap only says so ([onTap]). */
@Composable
private fun ResetButton(size: Dp, onTap: () -> Unit, onDoubleTap: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.ListButton)
            // A lone tap is only reported once the time for a second has passed.
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_reset), contentDescription = "Reset. Double-tap to deal a new game", tint = AppColors.ListButtonText, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * How the game ended, over the board: the message, what to do next, and
 * [detail] (the score and time). It stays until one of the two is chosen.
 */
@Composable
private fun ResultScreen(outcome: Outcome, detail: String, onNewGame: () -> Unit, onHome: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .background(Color.Black.copy(alpha = 0.86f))
            // Taps go no further: the board under it isn't to be played.
            .clickable(interactionSource = null, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        // Play Again has the longer name, so the larger share.
        val row = maxWidth * 0.86f - 6.dp
        Column(verticalArrangement = Arrangement.spacedBy(RESULT_GAP), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = outcome.message,
                style = MaterialTheme.typography.titleSmall,
                color = LocalGameAccent.current,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameButton("Home", onHome, width = row * 0.4f, height = RESULT_BUTTON, color = AppColors.ListButton, textColor = AppColors.ListButtonText)
                GameButton("Play Again", onNewGame, width = row * 0.6f, height = RESULT_BUTTON)
            }
            Text(detail, style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
        }
    }
}

/**
 * The crown's fling: while the view is at the board and the board's own cards
 * can still scroll that way, they coast ([coast]), going no further than
 * their end; otherwise the view does as it would for a finger ([snap]).
 */
private class CrownFling(
    private val scroll: ScrollState,
    private val boardScroll: ScrollState,
    private val snap: FlingBehavior,
    private val coast: FlingBehavior,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val onBoard = scroll.value == 0 && boardScroll.inUse &&
            if (initialVelocity > 0) boardScroll.canScrollForward else boardScroll.canScrollBackward
        if (!onBoard) return with(snap) { performFling(initialVelocity) }
        val cards = object : ScrollScope {
            override fun scrollBy(pixels: Float) = boardScroll.dispatchRawDelta(pixels)
        }
        with(coast) { cards.performFling(initialVelocity) }
        return 0f
    }
}

/**
 * Between the board (0) and the Reset and Undo buttons ([stop]), a release
 * snaps to one or the other; past them it flings as usual, stopping at them.
 */
private class BoardSnap(
    private val scroll: ScrollState,
    private val stop: Int,
    private val flick: Float,
    private val rulesFling: FlingBehavior,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (scroll.value >= stop) {
            val list = this
            val held = object : ScrollScope {
                override fun scrollBy(pixels: Float) =
                    list.scrollBy(if (pixels < 0) max(pixels, (stop - scroll.value).toFloat()) else pixels)
            }
            return with(rulesFling) { held.performFling(initialVelocity) }
        }
        val needed = when {
            initialVelocity > flick -> SNAP_FLICK
            initialVelocity < -flick -> 1 - SNAP_FLICK
            else -> SNAP_DRAG
        }
        val target = if (scroll.value > stop * needed) stop else 0
        var last = scroll.value.toFloat()
        animate(last, target.toFloat(), initialVelocity, spring()) { value, _ ->
            // By what was scrolled, so a spring passing the end doesn't come back short.
            last += scrollBy(value - last)
        }
        return 0f
    }
}
