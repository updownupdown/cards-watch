package com.updownupdown.cards.watch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS

/** A side gauge's thickness, and the gap between it and the screen's edge. */
private val GAUGE_WIDTH = 6.dp
private val GAUGE_EDGE = 9.dp

/**
 * A progress bar along the round screen's left edge, centred on the screen's
 * middle (level with it, whatever the board's lift), filling upwards to
 * [fraction] in [color]. Drawn from inside a board of side [board], the
 * game's [boardFraction] and [boardLift] place it on the screen, so it scales
 * with any screen. Asleep, it is grey.
 */
@Composable
fun SideGauge(
    fraction: Float,
    color: Color,
    board: Dp,
    boardFraction: Float = BOARD_FRACTION,
    boardLift: Float = BOARD_LIFT,
    sweep: Float = 50f,
) {
    val ambient = LocalAmbient.current
    val filled by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(CARD_MOVE_MS), label = "gauge")
    val diameter = board / boardFraction
    // The screen's middle, from the board's top left.
    val middleY = board / 2 + diameter * boardLift
    val radius = diameter / 2 - GAUGE_EDGE - GAUGE_WIDTH / 2
    Canvas(
        Modifier
            .offset((board - diameter) / 2, middleY - diameter / 2)
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(diameter),
    ) {
        val start = 180f - sweep / 2
        val r = radius.toPx()
        val corner = Offset(center.x - r, center.y - r)
        val box = Size(r * 2, r * 2)
        val line = Stroke(GAUGE_WIDTH.toPx(), cap = StrokeCap.Round)
        drawArc(if (ambient) AppColors.Ambient.copy(alpha = 0.3f) else AppColors.Track, start, sweep, false, corner, box, style = line)
        // From the bottom end, up the left side.
        if (filled > 0f) drawArc(if (ambient) AppColors.Ambient else color, start, sweep * filled, false, corner, box, style = line)
    }
}
