package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.ALL_GAMES

@Composable
fun HomeScreen(lastGame: Game<*>?, onOpen: (Game<*>) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val colors = ButtonDefaults.buttonColors(
        containerColor = AppColors.DeepBlue,
        contentColor = Color.White,
        secondaryContentColor = AppColors.PaleYellow,
    )
    val games = ALL_GAMES.sortedBy { it.title }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Column(transformed(spec).fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo_spade), contentDescription = null, modifier = Modifier.size(34.dp))
                    Text("Solitarius", style = TextStyle(fontFamily = Outfit, fontSize = 18.sp, color = Color.White))
                }
            }
            if (lastGame != null) {
                item {
                    Button(
                        onClick = { onOpen(lastGame) },
                        label = { Text(lastGame.title) },
                        secondaryLabel = { Text("Continue") },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
            items(games, key = { it.id }) { game ->
                Button(
                    onClick = { onOpen(game) },
                    label = { Text(game.title) },
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}
