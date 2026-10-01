package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.ALL_GAMES
import kotlin.math.max
import kotlin.math.roundToInt

/** The space under the logo, enough that the first button doesn't slide up over it. */
private val LOGO_GAP = 19.dp
/** The space between the game to continue and the full list. */
private val LIST_GAP = 12.dp
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

/** A game's title on its button, × the usual button text. */
private const val TITLE_SCALE = 1.1f
/** How much of a button's picture its accent colour covers. */
private const val OVERLAY = 0.85f
/** The space after the last game. */
private val LIST_END = 40.dp

@Composable
fun HomeScreen(lastGame: Game<*>?, onOpen: (Game<*>) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val games = ALL_GAMES.sortedBy { it.title }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Column(transformed(spec).fillMaxWidth().padding(bottom = LOGO_GAP), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo_spade), contentDescription = null, modifier = Modifier.size(34.dp))
                    Text("Solitarius", style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color.White))
                }
            }
            if (lastGame != null) {
                item {
                    GameButton(lastGame, "Continue", { onOpen(lastGame) }, Modifier.fillMaxWidth().transformedHeight(this, spec), SurfaceTransformation(spec))
                }
                item { Spacer(Modifier.height(LIST_GAP)) }
            }
            items(games, key = { it.id }) { game ->
                GameButton(game, null, { onOpen(game) }, Modifier.fillMaxWidth().transformedHeight(this, spec), SurfaceTransformation(spec))
            }
            // Room to bring the last game up clear of the round screen's foot.
            item { Spacer(Modifier.height(LIST_END)) }
        }
    }
}

/**
 * A game's button: its picture filling the whole button (cut to fit, never
 * stretched) under [OVERLAY] of its accent colour, with its title (and [note], such as "Continue") in the
 * middle.
 */
@Composable
private fun GameButton(game: Game<*>, note: String?, onClick: () -> Unit, modifier: Modifier, transformation: SurfaceTransformation) {
    Button(
        onClick = onClick,
        containerPainter = CoverPainter(ImageBitmap.imageResource(game.banner), game.accent.copy(alpha = OVERLAY)),
        shape = CircleShape,
        colors = ButtonDefaults.buttonWithContainerPainterColors(
            contentColor = AppColors.ListButtonText,
            secondaryContentColor = AppColors.PaleYellow,
        ),
        secondaryLabel = note?.let { { Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } },
        label = {
            val style = LocalTextStyle.current
            Text(
                text = game.title,
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
