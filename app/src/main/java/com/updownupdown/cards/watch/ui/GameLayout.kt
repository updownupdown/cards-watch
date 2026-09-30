package com.updownupdown.cards.watch.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R

/** The board's side as a fraction of the screen's diameter. */
const val BOARD_FRACTION = 0.64f

/**
 * Every game's screen: a square board in the middle, with the home button
 * above it, undo to its left, rules to its right, and [bottom] (a status or
 * an action button) in the crescent below.
 */
@Composable
fun GameLayout(
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onUndoHold: () -> Unit,
    canUndo: Boolean,
    onRules: () -> Unit,
    bottom: @Composable (width: Dp, height: Dp) -> Unit,
    board: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameter = min(maxWidth, maxHeight)
        val boardSize = diameter * BOARD_FRACTION
        // The depth of the space between the board and the screen's edge.
        val band = (diameter - boardSize) / 2
        val button = min(band * 0.86f, 40.dp)
        val inset = (band - button) / 2

        Box(Modifier.align(Alignment.Center).size(boardSize), content = board)

        RoundButton(
            icon = R.drawable.ic_home,
            label = "Home",
            onClick = onHome,
            size = button,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = inset),
        )
        RoundButton(
            icon = R.drawable.ic_undo,
            label = "Undo. Hold for a new game",
            onClick = onUndo,
            onLongClick = onUndoHold,
            size = button,
            dimmed = !canUndo,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = inset),
        )
        RoundButton(
            icon = R.drawable.ic_rules,
            label = "Rules",
            onClick = onRules,
            size = button,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = -inset),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).size(boardSize, band),
            contentAlignment = Alignment.TopCenter,
        ) {
            bottom(boardSize, band)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundButton(
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
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.55f).alpha(if (dimmed) 0.35f else 1f),
        )
    }
}

/** A short line of text for the bottom crescent, such as a move count. */
@Composable
fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** A pill-shaped button for the bottom crescent, such as "Deal". */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .padding(top = 4.dp)
            .size(width * 0.8f, min(height * 0.7f, 32.dp))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .alpha(if (enabled) 1f else 0.4f)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

/** A message across the middle of the board, such as when the game is won. */
@Composable
fun BoxScope.BoardBanner(text: String) {
    Box(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
