package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedBox
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.timeTextCurvedText
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.GameAction
import com.updownupdown.cards.watch.game.resolve
import kotlin.math.sqrt

/** The board's side as a fraction of the screen's diameter. */
const val BOARD_FRACTION = 0.64f

/** The space between the board and the top of the bottom buttons. */
private val BOTTOM_PAD = 5.dp

/**
 * How far the board sits above the middle, as a fraction of the screen's
 * diameter: room below for Wear's edge button, which needs about 44dp. Past
 * 0.06 the board's top corners leave the round screen.
 */
const val BOARD_LIFT = 0.05f

/** The least room under the board that holds a button, and the height the edge button gets without it. */
private val MIN_CRESCENT = 36.dp
private val EDGE_BUTTON = 44.dp

/** The board's side, for games laying out text or the bottom crescent to match their cards. */
val LocalBoardSize = staticCompositionLocalOf { 0.dp }

/** The colour of the buttons of the game on screen: its pale accent. */
val LocalGameAccent = staticCompositionLocalOf { AppColors.GameButton }

/** The depth the time takes at the screen's top; a board reaching into it has the time over it. */
private val TIME_ROOM = 20.dp

/** How thick the black outline round the time is, over such a board. */
private val TIME_OUTLINE = 1.5.dp

/** The outline's copies of the time: each one shifted by [TIME_OUTLINE] one way, as padding of twice that on the other side. */
private val TIME_OUTLINE_SHIFTS = listOf(-1, 0, 1).flatMap { r -> listOf(-1, 0, 1).map { a -> r to a } } - (0 to 0)

/** The time with a thin black outline, and no backing: black copies of it shifted every way round it, under it. */
@Composable
private fun OutlinedTimeText() {
    val outline = CurvedTextStyle(color = Color.Black)
    TimeText(backgroundColor = Color.Transparent) { time ->
        curvedBox(radialAlignment = CurvedAlignment.Radial.Center, angularAlignment = CurvedAlignment.Angular.Center) {
            for ((radial, angular) in TIME_OUTLINE_SHIFTS) {
                val pad = TIME_OUTLINE * 2
                curvedBox(
                    CurvedModifier.padding(
                        outer = if (radial < 0) pad else 0.dp,
                        inner = if (radial > 0) pad else 0.dp,
                        before = if (angular > 0) pad else 0.dp,
                        after = if (angular < 0) pad else 0.dp,
                    ),
                ) { timeTextCurvedText(time, outline) }
            }
            timeTextCurvedText(time)
        }
    }
}

/**
 * Every game's screen: a square board in the middle, with the time above it
 * and [bottom] (a status or buttons) in the crescent below. [bottom] gets the width that fits inside
 * the round screen at the foot of a button, and the button height.
 */
@Composable
fun GameLayout(
    boardFraction: Float = BOARD_FRACTION,
    boardLift: Float = BOARD_LIFT,
    showTime: Boolean = true,
    bottom: @Composable (size: DpSize) -> Unit,
    board: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameter = min(maxWidth, maxHeight)
        val boardSize = diameter * boardFraction
        // The depth of the space between the board and the screen's edge.
        val band = (diameter - boardSize) / 2

        // Above the rest, so a card can sit over the bottom buttons (a drawn card).
        val lift = diameter * boardLift
        // A board reaching up into the time's room (Ouroboros) has the time over its cards, outlined in black.
        val underTime = band - lift < TIME_ROOM
        if (showTime) {
            Box(Modifier.fillMaxSize().zIndex(if (underTime) 2f else 0f)) {
                if (underTime) OutlinedTimeText() else TimeText()
            }
        }
        CompositionLocalProvider(LocalBoardSize provides boardSize) {
            Box(Modifier.align(Alignment.Center).offset(y = -lift).size(boardSize).zIndex(1f), content = board)
        }

        val crescent = band + lift - BOTTOM_PAD
        // The widest a button can be and still clear the round edge at its foot.
        val buttonHeight = min(band * 0.58f, 28.dp)
        val radius = diameter / 2
        val foot = boardSize / 2 - lift + BOTTOM_PAD + buttonHeight
        val halfWidth = sqrt((radius.value * radius.value - foot.value * foot.value).coerceAtLeast(0f)).dp - 6.dp
        Box(
            Modifier
                // The crescent from under the board to the screen's foot, full width. A board
                // that leaves no room for a button (Clock) has it over its foot instead.
                .align(Alignment.BottomCenter)
                .zIndex(if (crescent < MIN_CRESCENT) 2f else 0f)
                .size(diameter, if (crescent < MIN_CRESCENT) EDGE_BUTTON else crescent),
            contentAlignment = Alignment.TopCenter,
        ) {
            CompositionLocalProvider(LocalBoardSize provides boardSize) {
                bottom(DpSize(halfWidth * 2, buttonHeight))
            }
        }
    }
}

/** A short line of text for the bottom crescent, shrinking to fit. */
@Composable
fun StatusText(text: String, width: Dp) {
    val style = MaterialTheme.typography.labelMedium
    BasicText(
        text = text,
        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = style.fontSize),
        modifier = Modifier.padding(top = 4.dp).size(width, 20.dp),
    )
}

/**
 * The one action under the board, such as "Deal": Wear's edge button, hugging
 * the foot of the round screen.
 */
@Composable
fun BottomButton(label: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = LocalGameAccent.current) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        EdgeButton(
            onClick = onClick,
            enabled = enabled,
            // Up to the board: under 44dp the edge button starts to fade its label.
            modifier = Modifier.requiredHeight(maxHeight + BOTTOM_PAD).offset(y = -BOTTOM_PAD / 2),
            buttonSize = EdgeButtonSize.ExtraSmall,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = AppColors.GameButtonText,
                disabledContainerColor = color.copy(alpha = 0.4f),
                disabledContentColor = AppColors.GameButtonText,
            ),
        ) {
            // Shrinks to fit, so a longer label ("Draw (45)") isn't cut short.
            val style = LocalTextStyle.current
            BasicText(
                text = label,
                style = style.copy(color = AppColors.GameButtonText, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

/** A pill-shaped button inside a game, where there are several, such as "Reveal" and "Return". */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color = LocalGameAccent.current,
    textColor: Color = AppColors.GameButtonText,
    /** Drawn in place of the [label], which still names the button to a screen reader. */
    icon: (@Composable BoxScope.() -> Unit)? = null,
    /** Fully rounded, but where a game asks for another shape (Fortune's Call). */
    shape: Shape = CircleShape,
    /** A line round the button, to stand it apart from what it lies over. */
    border: BorderStroke? = null,
) {
    // Asleep, a button keeps its place but isn't drawn.
    if (LocalAmbient.current) {
        Box(modifier.size(width, height))
        return
    }
    Box(
        modifier
            .size(width, height)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .then(if (icon != null) Modifier.semantics { contentDescription = label } else Modifier)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            icon()
        } else {
            BasicText(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(color = textColor, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            )
        }
    }
}

/** One or two game buttons side by side, or else a status line. */
@Composable
fun <S> BottomActions(
    actions: List<GameAction<S>>,
    status: String?,
    state: S,
    play: (S) -> Unit,
    feedback: Feedback,
    size: DpSize,
) {
    when (actions.size) {
        0 -> status?.let { StatusText(it, size.width) }
        1 -> {
            val action = actions[0]
            BottomButton(action.label.resolve(), { feedback.move(); play(action.perform(state)) }, action.enabled, action.color ?: LocalGameAccent.current)
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.take(2).forEach { action ->
                GameButton(
                    label = action.label.resolve(),
                    onClick = { feedback.move(); play(action.perform(state)) },
                    width = (size.width - 4.dp) / 2,
                    height = size.height,
                    enabled = action.enabled,
                    color = action.color ?: LocalGameAccent.current,
                )
            }
        }
    }
}
