package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.data.titleOf
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.formatTime
import com.updownupdown.cards.watch.game.sortedByTitle
import com.updownupdown.cards.watch.game.upper
import com.updownupdown.cards.watch.games.ALL_GAMES
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.nestor.Nestor
import kotlin.math.roundToInt

/** A bar's thickness: the games completed, and the one under each game's line. */
private val BAR = 6.dp
private val GAME_BAR = 3.dp

/** The space between the stats' groups. */
private val GROUP_GAP = 28.dp

/** The space between the list's items, twice Wear's, so they stay apart as they scroll. */
private val ITEM_GAP = 8.dp

/** A big number's size, × the theme's large title. */
private const val NUMBER_SCALE = 1f

/** How much of the list's width the games' lines take, so their ends clear the round edge. */
private const val GAME_WIDTH = 0.86f

/** Stats across every game, for the stats screen. */
internal data class OverallStats(
    val won: Int,
    val lost: Int,
    /** Games won at least once. */
    val completed: Int,
    val bestStreak: Int,
    val mostPlayed: Game<*>?,
) {
    val played: Int get() = won + lost
    val winPercent: Int? get() = if (played == 0) null else (won * 100f / played).roundToInt()
}

internal fun overallStats(stats: Map<String, GameStats>): OverallStats {
    val all = stats.values
    val mostPlayed = stats.entries.filter { it.value.won + it.value.lost > 0 }
        .maxByOrNull { it.value.won + it.value.lost }
    return OverallStats(
        won = all.sumOf { it.won },
        lost = all.sumOf { it.lost },
        completed = all.count { it.won > 0 },
        bestStreak = all.maxOfOrNull { it.bestStreak } ?: 0,
        mostPlayed = mostPlayed?.let { entry -> ALL_GAMES.firstOrNull { it.id == entry.key } },
    )
}

/**
 * Stats across every game, from the home screen's Stats button: the games
 * completed (won at least once) as a bar, wins, losses and the share won side
 * by side over a bar split between them, deals played and the longest
 * streak, the game played most (tapping it opens it), then each game played,
 * by title, with its wins and losses over a bar of its share won and
 * its fastest win and best score; tapping one opens it.
 */
@Composable
fun StatsScreen(stats: Map<String, GameStats>, onOpen: (Game<*>) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val overall = overallStats(stats)
    val byGame = sortedByTitle(ALL_GAMES).mapNotNull { game -> stats[game.id]?.takeIf { it.won + it.lost > 0 }?.let { game to it } }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP, Alignment.Top),
        ) {
            item { ScreenTitle(R.drawable.ic_chart, stringResource(R.string.stats), transformed(spec)) }

            // Games completed, out of all of them.
            item {
                Column(transformed(spec).fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BigNumber("${overall.completed}/${ALL_GAMES.size}", LocalAccent.current)
                    StatLabel(stringResource(R.string.stat_completed))
                    Bar(overall.completed / ALL_GAMES.size.toFloat(), LocalAccent.current, BAR, Modifier.padding(top = 6.dp))
                }
            }

            // Won, lost and the share won, over a bar of the two.
            item {
                Column(transformed(spec).padding(top = GROUP_GAP).fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth()) {
                        Figure("${overall.won}", stringResource(R.string.stat_won), AppColors.Good)
                        Figure("${overall.lost}", stringResource(R.string.stat_lost), AppColors.Bad)
                        Figure(overall.winPercent?.let { stringResource(R.string.percent, it) } ?: "–", stringResource(R.string.stat_win_percent), Color.White)
                    }
                    WinBar(overall.won, overall.lost, Modifier.padding(top = 6.dp).fillMaxWidth(0.85f))
                }
            }

            item {
                Row(transformed(spec).padding(top = GROUP_GAP).fillMaxWidth(0.75f)) {
                    Figure("${overall.played}", stringResource(R.string.stat_played), Color.White)
                    Figure("${overall.bestStreak}", stringResource(R.string.stat_best_streak), Color.White)
                }
            }

            overall.mostPlayed?.let { game ->
                item { SectionHeading(stringResource(R.string.most_played), transformed(spec).padding(top = GROUP_GAP)) }
                item {
                    GameButton(
                        game,
                        false,
                        { onOpen(game) },
                        null,
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        SurfaceTransformation(spec)
                    )
                }
            }

            if (byGame.isNotEmpty()) {
                item { SectionHeading(stringResource(R.string.stats_by_game), transformed(spec).padding(top = GROUP_GAP)) }
                items(byGame, key = { "game:${it.first.id}" }) { (game, gameStats) ->
                    GameLine(game, gameStats, { onOpen(game) }, transformed(spec))
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = transformed(spec).padding(top = GROUP_GAP).fillMaxWidth(0.8f),
                    )
                }
            }

            // Room to bring the foot of the list up clear of the round screen's.
            item { Spacer(Modifier.height(LIST_END)) }
        }
    }
}

/** A number, large and bold, in [color]. */
@Composable
private fun BigNumber(text: String, color: Color) {
    val style = MaterialTheme.typography.titleLarge
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * NUMBER_SCALE, fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
    )
}

/** A stat's name, small, in capitals, under its number. */
@Composable
private fun StatLabel(text: String) {
    Text(
        text.upper(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = AppColors.TextMuted,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** A number over its name, sharing the row with the others. */
@Composable
private fun RowScope.Figure(value: String, name: String, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        BigNumber(value, color)
        StatLabel(name)
    }
}

/** A rounded bar [fraction] full of [color], the rest the track. */
@Composable
private fun Bar(fraction: Float, color: Color, thickness: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .clip(CircleShape)
            .background(AppColors.Track)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(thickness)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Wins in green and losses in red, side by side in one bar, as their shares; the track before any. */
@Composable
private fun WinBar(won: Int, lost: Int, modifier: Modifier) {
    Row(
        modifier
            .height(BAR)
            .clip(CircleShape)
            .background(AppColors.Track),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (won > 0) Box(Modifier.weight(won.toFloat()).height(BAR).background(AppColors.Good))
        if (lost > 0) Box(Modifier.weight(lost.toFloat()).height(BAR).background(AppColors.Bad))
    }
}

/**
 * A game played: its title in its pale colour, its wins and losses at the
 * right, over a thin bar of its share won in the same colour, then its
 * fastest win and best score (fewest moves, or highest score), when it has them. Tapping it opens the game.
 */
@Composable
private fun GameLine(game: Game<*>, stats: GameStats, onOpen: () -> Unit, modifier: Modifier) {
    val title = titleOf(game)
    val colour = colorResource(game.accentPale)
    val small = MaterialTheme.typography.bodySmall
    val bests = listOfNotNull(
        stats.bestTimeMs?.let { stringResource(R.string.stat_fastest_time, formatTime(it)) },
        game.scoreName?.let { name -> stats.bestScore?.let { stringResource(R.string.stat_best_score_value, stringResource(name), it) } },
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.play_game, title), onClick = onOpen)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth(GAME_WIDTH)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = small.copy(fontWeight = FontWeight.Medium),
                    color = colour,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${stats.won}–${stats.lost}",
                    style = small.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Text(
                    stats.winPercent?.let { stringResource(R.string.percent, it) } ?: "",
                    style = small,
                    color = AppColors.TextMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(38.dp),
                )
            }
            Bar((stats.winPercent ?: 0) / 100f, colour, GAME_BAR, Modifier.padding(top = 3.dp))
            if (bests.isNotEmpty()) {
                Text(
                    bests.joinToString(" · "),
                    style = small.copy(fontWeight = FontWeight.Light),
                    color = AppColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@WearGamePreview
@Composable
private fun StatsScreenPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black)
        ) {
            AppScaffold {
                StatsScreen(
                    mapOf(Nestor.id to GameStats(won = 3, lost = 2, bestStreak = 2), Lycan.id to GameStats(lost = 4)),
                    onOpen = {},
                )
            }
        }
    }
}
