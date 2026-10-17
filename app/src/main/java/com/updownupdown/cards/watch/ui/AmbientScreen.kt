package com.updownupdown.cards.watch.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.game.GameSession
import java.util.Date

/**
 * Whether the watch is asleep with the app still showing. Cards are then
 * drawn as grey outlines and buttons not at all, to keep the screen dark.
 */
val LocalAmbient = compositionLocalOf { false }

/**
 * Whether the watch is asleep, for the game screen under the sleep screen: a
 * game that moves on by itself after a wait (Lycan's rounds) waits for it to
 * wake, so the player sees it happen.
 */
val LocalAsleep = compositionLocalOf { false }

private object NoFeedback : Feedback {
    override fun move() {}
    override fun reject() {}
}

/**
 * The sleep screen: the time, and the board of the game being played in
 * grey outline with no buttons, or the logo when no game is up. [tick]
 * changes each minute, to redraw the time.
 */
@Composable
fun AmbientScreen(session: GameSession<*>?, tick: Int = 0) {
    val context = LocalContext.current
    val time = remember(tick) { DateFormat.getTimeFormat(context).format(Date()) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (session != null) AmbientBoard(session) else Logo(R.drawable.logo_spade_outline, AppColors.Ambient, AppColors.Ambient)
        Text(
            text = time,
            style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = AppColors.Ambient,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )
    }
}

/**
 * The app's logo in the middle of the screen, shown as the app starts: the
 * spade in the colour theme's [accent] (Nocturne's yellow by default, as on
 * the launcher icon) on Nocturne's night. The system's own splash before it
 * is blank, on the same night (themes.xml).
 */
@Composable
fun SplashScreen(accent: Color) {
    Box(Modifier.fillMaxSize().background(AppColors.Night)) {
        Logo(R.drawable.logo_spade, accent, Color.White)
    }
}

/** The spade's height in the logo; the name is [LOGO_NAME] of it, [LOGO_GAP] of the name's size below it. */
private val LOGO_SPADE = 52.dp
private const val LOGO_NAME = 1 / 3.4f
private const val LOGO_GAP = 0.75f

/** The spade's height in its drawables, × their size (the spade spans 113 to 911 of their 1024). */
private const val SPADE_IN_DRAWABLE = 798f / 1024f

/** The spade's width, × its height. */
private const val SPADE_ASPECT = 702.7f / 798f

/**
 * The spade ([spade], drawn in [spadeColour]) over "Solitarius" in
 * [textColour]: the two together in the middle of the screen.
 */
@Composable
private fun Logo(@DrawableRes spade: Int, spadeColour: Color, textColour: Color) {
    val nameSize = (LOGO_SPADE.value * LOGO_NAME).sp
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Laid out at the spade's own bounds, so the two centre together; the drawable's margins overhang.
        Box(Modifier.size(LOGO_SPADE * SPADE_ASPECT, LOGO_SPADE), contentAlignment = Alignment.Center) {
            Image(
                painterResource(spade),
                contentDescription = null,
                colorFilter = ColorFilter.tint(spadeColour),
                modifier = Modifier.requiredSize(LOGO_SPADE / SPADE_IN_DRAWABLE),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = nameSize,
                lineHeight = nameSize,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                textAlign = TextAlign.Center,
            ),
            color = textColour,
            modifier = Modifier.padding(top = LOGO_SPADE * (LOGO_NAME * LOGO_GAP), start = 24.dp, end = 24.dp),
        )
    }
}

/** The game's board where it sits on the game's screen, as outlines, in grey, and not to be played. */
@Composable
private fun <S : Any> AmbientBoard(session: GameSession<S>) {
    val game = session.game
    val grey = MaterialTheme.colorScheme.copy(onSurface = AppColors.Ambient, onSurfaceVariant = AppColors.Ambient)
    CompositionLocalProvider(LocalAmbient provides true) {
        MaterialTheme(colorScheme = grey, typography = MaterialTheme.typography) {
            GameLayout(boardFraction = game.boardFraction, boardLift = game.boardLift, showTime = false, bottom = {}) {
                game.Board(session.state, {}, NoFeedback, Modifier.fillMaxSize())
            }
        }
    }
}
