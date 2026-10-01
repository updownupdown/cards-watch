package com.updownupdown.cards.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.LocalUndoing
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.Outcome
import com.updownupdown.cards.watch.game.formatTime
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
/** How long the reset button must be held, and how long its hint shows. */
private const val RESET_HOLD_MS = 2000
private const val HINT_MS = 2500L
/** The Home and Play Again buttons' height. */
private val RESULT_BUTTON = 44.dp
/** The space between those buttons and the game's title, where the reset hint shows. */
private val RULES_TOP = 30.dp
/** How long after the last move the result comes up, so the move can be seen. */
private const val RESULT_DELAY_MS = CARD_MOVE_MS + 500L

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
    val haptics = rememberHaptics()
    val game = session.game
    val state = session.state
    val outcome = session.outcome

    // Buzz when a move ends the game, but not when opening an ended game,
    // then show the result once the last move has played out.
    var wasOver by remember { mutableStateOf(outcome != null) }
    var showResult by remember { mutableStateOf(outcome != null) }
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
    val toBoard: () -> Unit = { scope.launch { scroll.animateScrollTo(0) } }

    // No time from the scaffold: the board carries its own, so it scrolls away with it.
    ScreenScaffold(scrollState = scroll, timeText = {}) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val stop = actionsSnap(constraints.maxHeight, density)
            val flick = with(density) { FLICK_SPEED.toPx() }
            val rulesFling = ScrollableDefaults.flingBehavior()
            val fling = remember(scroll, stop, flick, rulesFling) { BoardSnap(scroll, stop, flick, rulesFling) }

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
                    .verticalScroll(scroll, flingBehavior = fling)
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(scroll, flingBehavior = fling), rememberActiveFocusRequester()),
            ) {
                Box(Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight)) {
                    GameLayout(
                        boardFraction = game.boardFraction,
                        boardLift = game.boardLift,
                        bottom = { size -> game.Bottom(state, session::play, haptics, size) },
                    ) {
                        CompositionLocalProvider(LocalUndoing provides session.undone) {
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
                            onDismiss = { showResult = false },
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
                        onHeld = {
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
                            text = "Hold ${RESET_HOLD_MS / 1000}s to reset game",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.PaleYellow,
                        )
                    }
                }
                GameRules(game, stats, scroll, this@BoxWithConstraints.constraints.maxHeight, onTop = toBoard)
            }
        }
    }
}

/**
 * The round button that deals a new game once it's been held for
 * [RESET_HOLD_MS], filling as it's held. A tap only says so ([onTap]).
 */
@Composable
private fun ResetButton(size: Dp, onTap: () -> Unit, onHeld: () -> Unit) {
    val held = remember { Animatable(0f) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.ListButton)
            .drawBehind { drawRect(AppColors.Bad.copy(alpha = 0.6f), size = this.size.copy(height = this.size.height * held.value), topLeft = Offset(0f, this.size.height * (1 - held.value))) }
            .pointerInput(Unit) {
                coroutineScope {
                    detectTapGestures(
                        onPress = {
                            val filling = launch { held.animateTo(1f, tween(RESET_HOLD_MS, easing = LinearEasing)) }
                            val released = withTimeoutOrNull(RESET_HOLD_MS.toLong()) { tryAwaitRelease() }
                            filling.cancel()
                            held.snapTo(0f)
                            when (released) {
                                null -> onHeld()
                                true -> onTap()
                                else -> {}
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_reset), contentDescription = "Reset. Hold to deal a new game", tint = AppColors.ListButtonText, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * How the game ended, over the board: the message, what to do next, and
 * [detail] (the score and time). Tapping anywhere else puts it away, to look
 * at the final board.
 */
@Composable
private fun ResultScreen(outcome: Outcome, detail: String, onNewGame: () -> Unit, onHome: () -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .background(Color.Black.copy(alpha = 0.86f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Play Again has the longer name, so the larger share.
        val row = maxWidth * 0.86f - 6.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = outcome.message,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.PaleYellow,
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
