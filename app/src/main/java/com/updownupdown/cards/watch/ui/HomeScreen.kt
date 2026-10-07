package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.sortedByTitle
import com.updownupdown.cards.watch.games.ALL_GAMES
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.lycan.Lycan
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

/** The space under the logo, enough that the first button doesn't slide up over it. */
private val LOGO_GAP = 28.5.dp

/** The space over a section's heading (after the section before it). */
private val SECTION_GAP = 12.dp

/** The space between the Stats and Settings buttons. */
private val BUTTON_GAP = 12.dp

/** A game's title on its button, × the usual button text. */
private const val TITLE_SCALE = 1.1f

/** How much of a button's picture its accent colour covers. */
private const val OVERLAY = 0.85f

/** The logo's size. */
private val LOGO_SIZE = 34.dp

/** The fact's icon, × the logo's size, and its text, × the theme's small body text. */
private const val FACT_ICON_SCALE = 1.3f
private const val FACT_TEXT_SCALE = 1.34f

/** The space over and under the card fact. */
private val FACT_GAP = 30.dp

/** Draws [image] to cover the whole area, cut evenly at the sides or top and foot, under [scrim]. */
private class CoverPainter(private val image: ImageBitmap, private val scrim: Color) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        val scale = max(size.width / image.width, size.height / image.height)
        val cut = IntSize((size.width / scale).roundToInt(), (size.height / scale).roundToInt())
        drawImage(
            image = image,
            srcOffset = IntOffset((image.width - cut.width) / 2, (image.height - cut.height) / 2),
            srcSize = cut,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        drawRect(scrim)
    }
}

/**
 * The home list: the games played last under "Continue Playing", then every
 * game, those pinned first, then the Stats and Settings buttons side by side,
 * each to its own screen, a card fact and a button back to the top. Holding a game in the full list pins it there,
 * or unpins it.
 *
 * @param recent the games played, the last first.
 * @param pinned the ids of the pinned games, in the order they were pinned.
 */
@Composable
fun HomeScreen(
    recent: List<Game<*>>,
    pinned: List<String>,
    onOpen: (Game<*>) -> Unit,
    onPin: (Game<*>) -> Unit,
    onStats: () -> Unit = {},
    onSettings: () -> Unit = {},
    continueShown: Int = 3,
    /** The item the list starts at, for screenshots of its foot. */
    startAt: Int = 0,
) {
    val listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = startAt)
    val spec = rememberTransformationSpec()
    val buzz = rememberHaptics()
    val scope = rememberCoroutineScope()
    val games = sortedByTitle(ALL_GAMES)
        .sortedBy { game -> pinned.indexOf(game.id).let { if (it < 0) Int.MAX_VALUE else it } }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Column(
                    transformed(spec)
                        .fillMaxWidth()
                        .padding(bottom = LOGO_GAP),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painterResource(R.drawable.logo_spade),
                        contentDescription = null,
                        modifier = Modifier.size(LOGO_SIZE)
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = TextStyle(
                            fontFamily = Outfit,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    )
                }
            }
            if (recent.isNotEmpty() && continueShown > 0) {
                item { SectionHeading(stringResource(R.string.continue_playing), transformed(spec)) }
                items(recent.take(continueShown), key = { "recent:${it.id}" }) { game ->
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
                item { Spacer(Modifier.height(SECTION_GAP)) }
            }
            item { SectionHeading(stringResource(R.string.all_games), transformed(spec)) }
            items(games, key = { it.id }) { game ->
                GameButton(
                    game = game,
                    pinned = game.id in pinned,
                    onClick = { onOpen(game) },
                    onHold = {
                        buzz.longPress()
                        onPin(game)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            // Stats and settings, each on its own screen, side by side.
            item {
                Row(
                    transformed(spec).padding(top = SECTION_GAP * 2).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.CenterHorizontally),
                ) {
                    IconListButton(R.drawable.ic_chart, stringResource(R.string.stats), onClick = onStats)
                    IconListButton(R.drawable.ic_settings, stringResource(R.string.settings), onClick = onSettings)
                }
            }
            // A card fact; tapping it brings another.
            item {
                val facts = stringArrayResource(R.array.card_facts)
                var fact by rememberSaveable { mutableIntStateOf(Random.nextInt(facts.size)) }
                Column(
                    transformed(spec)
                        .fillMaxWidth()
                        .clickable(interactionSource = null, indication = null) {
                            // Any fact but the one showing.
                            fact =
                                (fact + 1 + Random.nextInt(facts.size - 1)) % facts.size
                        }
                        .padding(horizontal = 14.dp, vertical = FACT_GAP),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_fact),
                        contentDescription = stringResource(R.string.another_fact),
                        tint = AppColors.PaleYellow,
                        modifier = Modifier.size(LOGO_SIZE * FACT_ICON_SCALE)
                    )
                    val small = MaterialTheme.typography.bodySmall
                    Text(
                        text = facts[fact],
                        style = small.copy(
                            fontSize = small.fontSize * FACT_TEXT_SCALE,
                            lineHeight = small.lineHeight * FACT_TEXT_SCALE
                        ),
                        color = AppColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            // Back up to the logo.
            item {
                Box(transformed(spec).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ListButton(
                        stringResource(R.string.top),
                        Modifier.width(104.dp).height(40.dp),
                        icon = R.drawable.ic_chevron_up,
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    )
                }
            }
            // Room to bring the foot of the list up clear of the round screen's.
            item { Spacer(Modifier.height(LIST_END)) }
        }
    }
}

/**
 * A game's button: its picture filling the whole button (cut to fit, never
 * stretched) under [OVERLAY] of its accent colour, with its title in the
 * middle, and a pin before it when [pinned]. [onHold] is what holding it does.
 */
@Composable
internal fun GameButton(
    game: Game<*>,
    pinned: Boolean,
    onClick: () -> Unit,
    onHold: (() -> Unit)?,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = onClick,
        onLongClick = onHold,
        onLongClickLabel = stringResource(if (pinned) R.string.unpin else R.string.pin),
        containerPainter = CoverPainter(
            ImageBitmap.imageResource(game.banner),
            colorResource(game.accent).copy(alpha = OVERLAY)
        ),
        shape = CircleShape,
        colors = ButtonDefaults.buttonWithContainerPainterColors(
            contentColor = AppColors.ListButtonText,
            iconColor = colorResource(game.accentPale),
        ),
        icon = if (pinned) {
            {
                Icon(
                    painterResource(R.drawable.ic_pin),
                    contentDescription = stringResource(R.string.pinned),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        label = {
            val style = LocalTextStyle.current
            Text(
                text = stringResource(game.title),
                style = style.copy(fontSize = style.fontSize * TITLE_SCALE),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier,
        transformation = transformation,
    )
}

@WearGamePreview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black)
        ) {
            AppScaffold {
                HomeScreen(
                    recent = listOf(FifteenPuzzle, Clock),
                    pinned = listOf(Lycan.id),
                    onOpen = {},
                    onPin = {},
                )
            }
        }
    }
}




