package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.R
import java.util.Date

/**
 * The sleep screen: the time, the logo in grey outline and the game being
 * played. [tick] changes each minute, to redraw the time.
 */
@Composable
fun AmbientScreen(game: String?, tick: Int = 0) {
    val context = LocalContext.current
    val time = remember(tick) { DateFormat.getTimeFormat(context).format(Date()) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            text = time,
            style = TextStyle(fontFamily = Outfit, fontSize = 14.sp),
            color = AppColors.Ambient,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.logo_spade_outline), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(
                text = game ?: "Solitarius",
                style = TextStyle(fontFamily = Outfit, fontSize = 15.sp, textAlign = TextAlign.Center),
                color = AppColors.Ambient,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}
