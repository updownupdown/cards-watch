package com.updownupdown.cards.watch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.data.titleOf
import com.updownupdown.cards.watch.game.ArtCredit
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.formatTime
import com.updownupdown.cards.watch.game.upper
import kotlin.math.abs

/** The title's and the rules text's sizes, × the theme's title and body text. */
private const val TITLE_SCALE = 1.53f
private const val TEXT_SCALE = 1.2f
/** The rules text's line height, × what that size would have. */
private const val LINE_SCALE = 0.8f
/** The space between a heading's capital letters. */
private val HEADING_SPACING = 2.sp
/** How much of the width between the margins the text takes, so less of it meets the round edge. */
private const val RULES_WIDTH = 0.9f
/** The space over a paragraph. */
private val PARAGRAPH_TOP = 10.dp
/** The space under the Top button, so the round screen's foot cuts nothing off. */
private val RULES_BOTTOM = 64.dp
/** A rating's bar's thickness, and the gap between it and the rating's name. */
private val RATING_BAR = 4.dp
private val RATING_GAP = 8.dp
/** The space over a heading. */
private val HEADING_TOP = 24.dp
/** The ratings' and stats' boxes: their corners and the space inside them. */
private val BOX_CORNER = 14.dp
private val BOX_PADDING = 10.dp
/** The space over the ratings' heading and under their box. */
private val RATINGS_TOP = 36.dp
private val RATINGS_BOTTOM = 12.dp
/** The gap between the stats' boxes. */
private val STATS_GAP = 6.dp
/** A stat's number, × the theme's medium title. */
private const val STAT_SCALE = 1.2f
/** The game's whole picture, with its credit: its corners and the space under it. */
private val PICTURE_CORNER = 12.dp
private val PICTURE_GAP = 10.dp
/** The width of the button opening the picture's page on the phone, enough for its label. */
private val PHONE_BUTTON_WIDTH = 168.dp
/** The space between the artwork's title, its artist and its source. */
private val CREDIT_GAP = 2.dp

// Near the round screen's top and foot each line shrinks a little and
// fades. Paragraphs are split into their lines first, so it happens line by
// line. Set EDGE_SCALE to 1 for no shrinking.
/** How far from the middle (0) to the edge (1) a line starts to change. */
private const val EDGE_START = 0.6f
/** Its size and opacity at the very edge. */
private const val EDGE_SCALE = 0.85f
private const val EDGE_ALPHA = 0.45f

/**
 * A game's title and rules (its [Game.rules]), its ratings, its [stats] and
 * its whole picture with its credit, under the board on the game's screen,
 * ending in a button back to the board.
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
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = RULES_BOTTOM),
        contentAlignment = Alignment.TopCenter,
    ) {
        val width = (constraints.maxWidth * RULES_WIDTH).toInt()
        val body = MaterialTheme.typography.bodyMedium
        val text = body.copy(
            fontSize = body.fontSize * TEXT_SCALE,
            lineHeight = body.lineHeight * TEXT_SCALE * LINE_SCALE,
            fontWeight = FontWeight.Light,
        )
        val accent = colorResource(game.accentPale)
        val measurer = rememberTextMeasurer()
        // Each paragraph as the lines it wraps to at this width.
        val rules = stringArrayResource(game.rules)
        val paragraphs = remember(rules, width, text) {
            rules.map { line ->
                if (line.startsWith("## ")) null else lines(rich(line.removePrefix("- ")), text, width, measurer)
            }
        }
        Column(Modifier.fillMaxWidth(RULES_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
            val title = MaterialTheme.typography.titleMedium
            Text(
                text = titleOf(game),
                style = title.copy(fontSize = title.fontSize * TITLE_SCALE, lineHeight = title.lineHeight * TITLE_SCALE),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().then(edge),
            )
            rules.forEachIndexed { i, line ->
                val wrapped = paragraphs[i]
                if (wrapped == null) {
                    Heading(line.removePrefix("## "), accent, edge)
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

            Heading(stringResource(R.string.ratings), accent, edge, top = RATINGS_TOP)
            val rating = game.rating
            val ratingName = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, color = AppColors.TextMuted)
            val ratings = listOf(
                stringResource(R.string.rating_difficulty) to rating.difficulty,
                stringResource(R.string.rating_complexity) to rating.complexity,
                stringResource(R.string.rating_luck) to rating.luck,
            )
            // The names share the widest one's width, so the bars all start and end together.
            val nameWidth = with(LocalDensity.current) {
                ratings.maxOf { measurer.measure(it.first.upper(), ratingName).size.width }.toDp()
            }
            Column(
                Modifier
                    .padding(top = 6.dp, bottom = RATINGS_BOTTOM)
                    .then(edge)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BOX_CORNER))
                    .background(AppColors.Panel)
                    .padding(BOX_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ratings.forEach { (name, value) -> RatingMeter(name, value, ratingName, nameWidth, accent) }
            }

            Heading(stringResource(R.string.stats), accent, edge)
            val figures = listOfNotNull(
                stringResource(R.string.stat_won) to "${stats.won}",
                stringResource(R.string.stat_lost) to "${stats.lost}",
                stringResource(R.string.stat_win_percent) to (stats.winPercent?.let { stringResource(R.string.percent, it) } ?: "–"),
                stringResource(R.string.stat_best_streak) to "${stats.bestStreak}",
                game.scoreName?.let { name ->
                    stringResource(if (game.lowScoreWins) R.string.stat_fewest_score else R.string.stat_best_score, stringResource(name)) to
                        (stats.bestScore?.toString() ?: "–")
                },
                stringResource(R.string.stat_fastest) to (stats.bestTimeMs?.let(::formatTime) ?: "–"),
            )
            // Two to a row, each its own box; an odd one out alone in the middle.
            figures.chunked(2).forEach { row ->
                Row(
                    Modifier.padding(top = STATS_GAP).then(edge).fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(STATS_GAP, Alignment.CenterHorizontally),
                ) {
                    row.forEach { (name, value) -> Stat(name, value, if (row.size == 1) Modifier.fillMaxWidth(0.5f) else Modifier.weight(1f)) }
                }
            }

            Heading(stringResource(R.string.artwork), accent, edge)
            Image(
                painterResource(game.art),
                contentDescription = game.credit.title,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(top = 6.dp, bottom = PICTURE_GAP)
                    .then(edge)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PICTURE_CORNER)),
            )
            Credit(game.credit, accent, width, measurer, edge)
            OpenOnPhone(game.credit.link, edge)

            ListButton(
                stringResource(R.string.top),
                Modifier.padding(top = 20.dp).then(edge).width(104.dp).height(40.dp),
                icon = R.drawable.ic_chevron_up,
                onClick = onTop,
            )
        }
    }
}

@Composable
private fun Heading(text: String, color: Color, edge: Modifier, top: Dp = HEADING_TOP) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = HEADING_SPACING),
        color = color,
        modifier = Modifier.fillMaxWidth().padding(top = top, bottom = 2.dp).then(edge),
    )
}

/**
 * A rating's name in small capitals ([style], [nameWidth] wide), and beside
 * it a thin bar filled to [value] out of 100, in [color].
 */
@Composable
private fun RatingMeter(name: String, value: Int, style: TextStyle, nameWidth: Dp, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name.uppercase(), style = style, maxLines = 1, softWrap = false, modifier = Modifier.width(nameWidth))
        Box(
            Modifier
                .padding(start = RATING_GAP)
                .weight(1f)
                .height(RATING_BAR)
                .clip(CircleShape)
                .background(AppColors.Track),
        ) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).height(RATING_BAR).clip(CircleShape).background(color))
        }
    }
}

/**
 * The game's picture's credit: what it is, in [color], then who made it and when,
 * then where it's from, each line in the middle and each wrapped line its own,
 * to fit the round screen as it scrolls.
 */
@Composable
private fun Credit(credit: ArtCredit, color: Color, width: Int, measurer: TextMeasurer, edge: Modifier) {
    val small = MaterialTheme.typography.bodySmall
    val title = small.copy(fontWeight = FontWeight.Medium, color = color, textAlign = TextAlign.Center)
    val rest = small.copy(fontWeight = FontWeight.Light, color = AppColors.TextMuted, textAlign = TextAlign.Center)
    val parts = remember(credit, width, small) {
        listOf(
            credit.title to title,
            listOfNotNull(credit.artist, credit.date).joinToString(", ") to rest,
            credit.source to rest,
        ).map { (text, style) -> lines(AnnotatedString(text), style, width, measurer) to style }
    }
    parts.forEachIndexed { i, (wrapped, style) ->
        wrapped.forEachIndexed { n, line ->
            Text(
                line,
                style = style,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(top = if (n == 0 && i > 0) CREDIT_GAP else 0.dp).then(edge),
            )
        }
    }
}

/**
 * A button that opens [link] (the picture's page) on the paired phone, and
 * says so in Wear's open-on-phone dialog. Without a phone it only says so.
 */
@Composable
private fun OpenOnPhone(link: String, edge: Modifier) {
    val context = LocalContext.current
    var opened by remember { mutableStateOf(false) }
    ListButton(
        stringResource(R.string.view_on_phone),
        Modifier.padding(top = 12.dp).wrapContentWidth(unbounded = true).then(edge).width(PHONE_BUTTON_WIDTH).height(36.dp),
        icon = R.drawable.ic_phone,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching { RemoteActivityHelper(context).startRemoteActivity(intent) }
        opened = true
    }
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = opened,
        onDismissRequest = { opened = false },
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) },
    ) {
        OpenOnPhoneDialogDefaults.Icon()
    }
}

/** A stat in its own box, as tall as its row: its number, large, over its name, small and muted. */
@Composable
private fun Stat(name: String, value: String, modifier: Modifier) {
    val number = MaterialTheme.typography.titleMedium
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(BOX_CORNER))
            .background(AppColors.Panel)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            style = number.copy(fontSize = number.fontSize * STAT_SCALE, fontWeight = FontWeight.Bold),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
            textAlign = TextAlign.Center,
        )
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
