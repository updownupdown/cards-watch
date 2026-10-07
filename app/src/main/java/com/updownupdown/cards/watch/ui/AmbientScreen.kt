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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
        if (session != null) AmbientBoard(session) else Logo(R.drawable.logo_spade_outline, AppColors.Ambient)
        Text(
            text = time,
            style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = AppColors.Ambient,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )
    }
}

/** The app's logo in the middle of the screen, shown as the app starts. */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Logo(R.drawable.logo_spade, Color.White)
    }
}

/** The spade ([spade]) over "Solitarius" in [textColour], in the middle of the screen. */
@Composable
private fun Logo(@DrawableRes spade: Int, textColour: Color) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(spade), contentDescription = null, modifier = Modifier.size(72.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp, textAlign = TextAlign.Center),
            color = textColour,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
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
