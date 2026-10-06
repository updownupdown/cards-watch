package com.updownupdown.cards.watch.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.updownupdown.cards.watch.CardsWatchApp
import com.updownupdown.cards.watch.EXTRA_GAME
import com.updownupdown.cards.watch.MainActivity
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.ALL_GAMES
import com.updownupdown.cards.watch.games.gameById
import com.updownupdown.cards.watch.ui.AppColors

/** Bump when the tile's pictures change (a game added, a thumbnail replaced). */
private const val RESOURCES_VERSION = "2"

/** The button to continue the last game: its height and its picture's size. */
private const val CONTINUE_HEIGHT = 56f
private const val CONTINUE_THUMBNAIL = 44f
/** Its width, × the screen's. */
private const val CONTINUE_WIDTH = 0.8f
/** The two games before it, as named buttons side by side: their height, the gap between them and the gap above. */
private const val RECENT_HEIGHT = 40f
private const val RECENT_GAP = 6f
private const val ROW_GAP = 8f
private const val RECENT_SHOWN = 2

/**
 * The app's tile: the last game played as a wide Continue button, and the
 * two played before it as smaller named buttons under it. Each opens its game.
 * The app asks for a new one whenever a game is opened ([refreshSurfaces]).
 */
class GamesTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val recent = (application as CardsWatchApp).saves.recentGameIds.mapNotNull(::gameById)
        return done(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(this, recent, requestParams.deviceConfiguration)))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        ALL_GAMES.forEach { game ->
            resources.addIdToImageMapping(
                imageId(game),
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(game.thumbnail).build())
                    .build(),
            )
        }
        return done(resources.build())
    }
}

private fun <T> done(value: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { it.set(value) }

private fun imageId(game: Game<*>) = "thumb:${game.id}"

private fun color(color: Color) = argb(color.toArgb())

/** A tap that opens the app, at [game] or else where it was. */
private fun open(context: Context, game: Game<*>?): ModifiersBuilders.Clickable {
    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(MainActivity::class.java.name)
    if (game != null) activity.addKeyToExtraMapping(EXTRA_GAME, ActionBuilders.stringExtra(game.id))
    return ModifiersBuilders.Clickable.Builder()
        .setId(game?.id ?: "home")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()
}

private fun rounded(radius: Float, fill: Color? = null): ModifiersBuilders.Background {
    val background = ModifiersBuilders.Background.Builder()
        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build())
    if (fill != null) background.setColor(color(fill))
    return background.build()
}

private fun text(text: String, size: Float, color: Color): LayoutElement =
    LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setMaxLines(1)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                .setColor(color(color))
                .build(),
        )
        .build()

/** A game's picture as a circle of [size]. */
private fun thumbnail(game: Game<*>, size: Float): LayoutElement {
    val modifiers = ModifiersBuilders.Modifiers.Builder().setBackground(rounded(size / 2))
    return LayoutElementBuilders.Image.Builder()
        .setResourceId(imageId(game))
        .setWidth(dp(size))
        .setHeight(dp(size))
        .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
        .setModifiers(modifiers.build())
        .build()
}

private fun space(width: Float = 0f, height: Float = 0f): LayoutElement =
    LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).setHeight(dp(height)).build()

/** A wide pill: an optional picture, then [title] over [subtitle]. */
private fun pill(width: Float, tap: ModifiersBuilders.Clickable, picture: LayoutElement?, title: String, subtitle: String?): LayoutElement {
    val labels = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(if (picture == null) LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER else LayoutElementBuilders.HORIZONTAL_ALIGN_START)
        .addContent(text(title, 15f, AppColors.ListButtonText))
    if (subtitle != null) labels.addContent(text(subtitle, 12f, AppColors.PaleYellow))
    val row = LayoutElementBuilders.Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
    if (picture != null) row.addContent(picture).addContent(space(width = 10f))
    row.addContent(labels.build())
    val edge = (CONTINUE_HEIGHT - CONTINUE_THUMBNAIL) / 2
    return LayoutElementBuilders.Box.Builder()
        .setWidth(dp(width))
        .setHeight(dp(CONTINUE_HEIGHT))
        .setHorizontalAlignment(if (picture == null) LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER else LayoutElementBuilders.HORIZONTAL_ALIGN_START)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(rounded(CONTINUE_HEIGHT / 2, AppColors.ListButton))
                .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(edge)).setEnd(dp(14f)).build())
                .setClickable(tap)
                .build(),
        )
        .addContent(row.build())
        .build()
}

/** A small pill naming [game], which opens it. A long name is cut short. */
private fun nameButton(context: Context, game: Game<*>, width: Float): LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(dp(width))
        .setHeight(dp(RECENT_HEIGHT))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(rounded(RECENT_HEIGHT / 2, AppColors.ListButton))
                .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(8f)).setEnd(dp(8f)).build())
                .setClickable(open(context, game))
                .build(),
        )
        .addContent(text(game.title, 12f, AppColors.ListButtonText))
        .build()

/** The recent games shown under the last one. */
internal fun othersOnTile(recent: List<Game<*>>): List<Game<*>> = recent.drop(1).take(RECENT_SHOWN)

/**
 * The tile's layout for the games in [recent], the last played first. With
 * none played yet, a button that opens the app.
 */
internal fun tileLayout(context: Context, recent: List<Game<*>>, device: DeviceParameters): LayoutElement {
    val width = device.screenWidthDp * CONTINUE_WIDTH
    val column = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    val last = recent.firstOrNull()
    if (last == null) {
        column.addContent(pill(width, open(context, null), null, "Solitarius", "Play"))
    } else {
        column.addContent(pill(width, open(context, last), thumbnail(last, CONTINUE_THUMBNAIL), last.title, "Continue"))
        val others = othersOnTile(recent)
        if (others.isNotEmpty()) {
            // Narrower than Continue, as the round screen is by then.
            val each = (width * 0.9f - RECENT_GAP) / RECENT_SHOWN
            val row = LayoutElementBuilders.Row.Builder().setWidth(wrap())
            others.forEachIndexed { i, game ->
                if (i > 0) row.addContent(space(width = RECENT_GAP))
                row.addContent(nameButton(context, game, each))
            }
            column.addContent(space(height = ROW_GAP)).addContent(row.build())
        }
    }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(column.build())
        .build()
}
