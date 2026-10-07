package com.updownupdown.cards.watch.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.updownupdown.cards.watch.CardsWatchApp
import com.updownupdown.cards.watch.EXTRA_GAME
import com.updownupdown.cards.watch.MainActivity
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.data.titleOf
import com.updownupdown.cards.watch.data.withAppLanguage
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.gameById

/**
 * A watch face shortcut into the app, at the last game played: the logo,
 * in its own yellow where the face takes a picture and tinted by the face
 * where it takes an icon, or "Play" where it takes text.
 */
class ContinueComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, gameById((application as CardsWatchApp).saves.lastGameId))

    private fun data(type: ComplicationType, game: Game<*>?): ComplicationData? {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (game != null) intent.putExtra(EXTRA_GAME, game.id)
        val tap = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // In the language chosen in Settings, as it is now: the service may outlive a change.
        val text = withAppLanguage(this)
        val play = text.getString(R.string.play)
        val label = PlainComplicationText.Builder(if (game != null) text.getString(R.string.continue_game, titleOf(game, text)) else play).build()
        val name = PlainComplicationText.Builder(play).build()
        val spade = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_spade_mono)).build()
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(spade, label).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE -> {
                val logo = SmallImage.Builder(Icon.createWithResource(this, R.drawable.logo_spade), SmallImageType.ICON).build()
                SmallImageComplicationData.Builder(logo, label).setTapAction(tap).build()
            }
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(name, label).setMonochromaticImage(spade).setTapAction(tap).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(name, label).setMonochromaticImage(spade).setTapAction(tap).build()
            else -> null
        }
    }
}

/**
 * Asks the tile and the complication to redraw: after the last game played
 * changes, and whenever the app starts. The complication's tap is a
 * PendingIntent, which the system cancels when the app is reinstalled or
 * force-stopped; the watch face keeps the dead one until it's given another.
 */
fun refreshSurfaces(context: Context) {
    try {
        TileService.getUpdater(context).requestUpdate(GamesTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, ContinueComplicationService::class.java))
            .requestUpdateAll()
    } catch (e: Exception) {
        // Nothing is showing them, or the watch has no such service: there's nothing to redraw.
        Log.w("Surfaces", "Couldn't refresh the tile or complication", e)
    }
}
