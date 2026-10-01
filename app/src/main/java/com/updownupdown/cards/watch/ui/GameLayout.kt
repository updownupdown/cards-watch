package com.updownupdown.cards.watch.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.GameAction
import kotlin.math.sqrt

/** The board's side as a fraction of the screen's diameter. */
const val BOARD_FRACTION = 0.64f

/** The space between the board and the top of the bottom buttons. */
private val BOTTOM_PAD = 3.dp

/** The board's side, for games laying out the bottom crescent to match their cards. */
val LocalBoardSize = staticCompositionLocalOf { 0.dp }

/**
 * Every game's screen: a square board in the middle, with the home icon
 * above it, undo to its left, rules to its right, and [bottom] (a status or
 * buttons) in the crescent below. [bottom] gets the width that fits inside
 * the round screen at the foot of a button, and the button height.
 */
@Composable
fun GameLayout(
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onUndoHold: () -> Unit,
    canUndo: Boolean,
    onRules: () -> Unit,
    bottom: @Composable (size: DpSize) -> Unit,
    board: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameter = min(maxWidth, maxHeight)
        val boardSize = diameter * BOARD_FRACTION
        // The depth of the space between the board and the screen's edge.
        val band = (diameter - boardSize) / 2
        val icon = min(band * 0.8f, 36.dp)
        val inset = (band - icon) / 2

        // Above the rest, so a card can sit over the bottom buttons (a drawn card).
        Box(Modifier.align(Alignment.Center).size(boardSize).zIndex(1f), content = board)

        PlainIcon(R.drawable.ic_home, "Home", onHome, icon, Modifier.align(Alignment.TopCenter).offset(y = inset))
        PlainIcon(
            R.drawable.ic_undo,
            "Undo. Hold for a new game",
            onUndo,
            icon,
            Modifier.align(Alignment.CenterStart).offset(x = inset),
            onLongClick = onUndoHold,
            dimmed = !canUndo,
        )
        PlainIcon(R.drawable.ic_rules, "Rules", onRules, icon, Modifier.align(Alignment.CenterEnd).offset(x = -inset))

        // The widest a button can be and still clear the round edge at its foot.
        val buttonHeight = min(band * 0.58f, 28.dp)
        val radius = diameter / 2
        val foot = boardSize / 2 + BOTTOM_PAD + buttonHeight
        val halfWidth = sqrt((radius.value * radius.value - foot.value * foot.value).coerceAtLeast(0f)).dp - 6.dp
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = radius + boardSize / 2 + BOTTOM_PAD)
                .size(halfWidth * 2, band),
            contentAlignment = Alignment.TopCenter,
        ) {
            CompositionLocalProvider(LocalBoardSize provides boardSize) {
                bottom(DpSize(halfWidth * 2, buttonHeight))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlainIcon(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = AppColors.Icon,
            modifier = Modifier.size(size * 0.66f).alpha(if (dimmed) 0.35f else 1f),
        )
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

/** A pill-shaped button inside a game, such as "Deal". */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) AppColors.GameButton else AppColors.GameButtonDisabled)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = AppColors.DeepBlue, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
        )
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
            GameButton(
                label = action.label,
                onClick = { feedback.move(); play(action.perform(state)) },
                width = size.width,
                height = size.height,
                enabled = action.enabled,
            )
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.take(2).forEach { action ->
                GameButton(
                    label = action.label,
                    onClick = { feedback.move(); play(action.perform(state)) },
                    width = (size.width - 4.dp) / 2,
                    height = size.height,
                    enabled = action.enabled,
                )
            }
        }
    }
}

/** A message across the middle of the board, such as when the game is won. */
@Composable
fun BoxScope.BoardBanner(text: String) {
    Box(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.PaleYellow,
            textAlign = TextAlign.Center,
        )
    }
}
