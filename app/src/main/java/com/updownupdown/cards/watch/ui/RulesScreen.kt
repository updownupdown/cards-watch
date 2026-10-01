package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.formatTime
import kotlin.math.abs

/** The title's and the rules text's sizes, × the theme's title and body text. */
private const val TITLE_SCALE = 1.53f
private const val TEXT_SCALE = 1.2f
/** The space between a heading's capital letters. */
private val HEADING_SPACING = 2.sp
/** The space over a paragraph. */
private val PARAGRAPH_TOP = 4.dp
/** The space under the Top button, so the round screen's foot cuts nothing off. */
private val RULES_BOTTOM = 64.dp
/** The pale line between the rules and the stats. */
private val STATS_LINE = 1.dp
/** A stat's name and its number: the width each gets, and the gap between them. */
private val STATS_NAMES = 108.dp
private val STATS_NUMBERS = 44.dp
private val STATS_GAP = 12.dp

// Near the round screen's top and foot each line shrinks a little and
// fades. Paragraphs are split into their lines first, so it happens line by
// line. Set EDGE_SCALE to 1 for no shrinking.
/** How far from the middle (0) to the edge (1) a line starts to change. */
private const val EDGE_START = 0.6f
/** Its size and opacity at the very edge. */
private const val EDGE_SCALE = 0.88f
private const val EDGE_ALPHA = 0.45f

/**
 * A game's title and rules (its [Game.rules]) and its [stats], under the
 * board on the game's screen, ending in a button back to the board.
 *
 * @param scroll the screen's scroll, and [viewport] its height in pixels, to
 *   fit each line to the round screen as it passes the top and foot.
 */
@Composable
fun GameRules(
    game: Game<*>,
    stats: GameStats,
    scroll: ScrollState,
    viewport: Int,
    onTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edge = Modifier.edgeScaled(scroll, viewport)
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = RULES_BOTTOM)) {
        val width = constraints.maxWidth
        val body = MaterialTheme.typography.bodyMedium
        val text = body.copy(fontSize = body.fontSize * TEXT_SCALE, lineHeight = body.lineHeight * TEXT_SCALE)
        val measurer = rememberTextMeasurer()
        // Each paragraph as the lines it wraps to at this width.
        val paragraphs = remember(game, width, text) {
            game.rules.map { line ->
                if (line.startsWith("## ")) null else lines(rich(line.replaceFirst(Regex("^- "), "• ")), text, width, measurer)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val title = MaterialTheme.typography.titleMedium
            Text(
                text = game.title,
                style = title.copy(fontSize = title.fontSize * TITLE_SCALE, lineHeight = title.lineHeight * TITLE_SCALE),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().then(edge),
            )
            game.rules.forEachIndexed { i, line ->
                val wrapped = paragraphs[i]
                if (wrapped == null) {
                    Heading(line.removePrefix("## "), edge)
                } else {
                    wrapped.forEachIndexed { n, part ->
                        Text(
                            text = part,
                            style = text,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().padding(top = if (n == 0) PARAGRAPH_TOP else 0.dp).then(edge),
                        )
                    }
                }
            }

            Box(
                Modifier
                    .padding(top = 18.dp, bottom = 6.dp)
                    .then(edge)
                    .fillMaxWidth()
                    .height(STATS_LINE)
                    .background(AppColors.TextMuted.copy(alpha = 0.5f)),
            )
            Heading("Stats", edge, TextAlign.Center)
            val small = MaterialTheme.typography.bodyMedium
            Stat("Won", "${stats.won}", small, edge)
            Stat("Lost", "${stats.lost}", small, edge)
            Stat("Win %", stats.winPercent?.let { "$it%" } ?: "–", small, edge)
            Stat("Longest Streak", "${stats.bestStreak}", small, edge)
            game.scoreName?.let { name ->
                Stat(if (game.lowScoreWins) "Fewest $name" else "Best $name", stats.bestScore?.toString() ?: "–", small, edge)
            }
            Stat("Fastest Time", stats.bestTimeMs?.let(::formatTime) ?: "–", small, edge)

            ListButton(
                "Top",
                Modifier.padding(top = 20.dp).then(edge).width(104.dp).height(40.dp),
                icon = R.drawable.ic_chevron_up,
                onClick = onTop,
            )
        }
    }
}

@Composable
private fun Heading(text: String, edge: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text.uppercase(),
        textAlign = align,
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = HEADING_SPACING),
        color = AppColors.PaleYellow,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).then(edge),
    )
}

/** A stat in the middle of the screen: its name ending where its number starts. */
@Composable
private fun Stat(name: String, value: String, style: TextStyle, edge: Modifier) {
    Row(
        Modifier.fillMaxWidth().padding(top = PARAGRAPH_TOP).then(edge),
        horizontalArrangement = Arrangement.spacedBy(STATS_GAP, Alignment.CenterHorizontally),
    ) {
        Text(name, style = style, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(STATS_NAMES))
        Text(value, style = style.copy(fontWeight = FontWeight.Bold), maxLines = 1, modifier = Modifier.width(STATS_NUMBERS))
    }
}

/** [text] as the lines it wraps to in [width] pixels. */
private fun lines(text: AnnotatedString, style: TextStyle, width: Int, measurer: TextMeasurer): List<AnnotatedString> {
    val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
    return List(layout.lineCount) { text.subSequence(layout.getLineStart(it), layout.getLineEnd(it, visibleEnd = true)) }
}

/**
 * Shrinks a line a little and fades it as it nears the round screen's top or
 * foot, towards the middle. Only redraws as the screen scrolls.
 */
private fun Modifier.edgeScaled(scroll: ScrollState, viewport: Int): Modifier = composed {
    // Where the line's top sits in everything that scrolls.
    val top = remember { mutableFloatStateOf(0f) }
    onPlaced { top.floatValue = it.positionInRoot().y + scroll.value }
        .graphicsLayer {
            val middle = viewport / 2f
            val centre = top.floatValue - scroll.value + size.height / 2
            // How far out it is, from the middle (0) to the screen's edge (1), eased in.
            val out = ((abs(centre - middle) / middle - EDGE_START) / (1 - EDGE_START)).coerceIn(0f, 1f)
            val eased = out * out
            val scale = 1 - (1 - EDGE_SCALE) * eased
            scaleX = scale
            scaleY = scale
            alpha = 1 - (1 - EDGE_ALPHA) * eased
            transformOrigin = TransformOrigin(0.5f, if (centre < middle) 1f else 0f)
        }
}

/** Makes **text** bold. */
private fun rich(line: String): AnnotatedString = buildAnnotatedString {
    line.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
