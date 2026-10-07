package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import com.updownupdown.cards.watch.data.titleOf
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.ALL_GAMES
import kotlin.random.Random

/**
 * Custom preview annotation optimized for Wear OS round watch screens.
 */
@Preview(device = "id:wearos_large_round", name = "Watch Preview")
annotation class WearGamePreview

/**
 * Convenience helper to render the full app screen for a given [game] inside an IDE Compose Preview.
 */
@Composable
fun <S : Any> GamePreview(game: Game<S>, state: S = game.newGame(Random(3))) {
    AppTheme {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
            AppScaffold {
                val session = GameSession(game, SavedGame(game.saveVersion, state)) {}
                GameScreen(session = session, onHome = {})
            }
        }
    }
}

/**
 * Parameter provider that generates a sample [GameSession] for every registered game in [ALL_GAMES].
 *
 * Android Studio's Compose Preview window detects [PreviewParameter] and automatically
 * generates individual preview instances for each game in the list.
 */
class GamePreviewProvider : PreviewParameterProvider<GameSession<*>> {
    override val values: Sequence<GameSession<*>> = ALL_GAMES.asSequence().map { game ->
        createSampleSession(game)
    }
}

private fun <S : Any> createSampleSession(game: Game<S>): GameSession<S> {
    val state = game.newGame(Random(3))
    return GameSession(game, SavedGame(game.saveVersion, state)) {}
}

/**
 * Previews every game in the app individually using [GamePreviewProvider].
 */
@Preview(device = "id:wearos_large_round", name = "Single Game Preview")
@Composable
fun AllGamesPreview(
    @PreviewParameter(GamePreviewProvider::class) session: GameSession<*>
) {
    AppTheme {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
            AppScaffold {
                GameScreen(session = session, onHome = {})
            }
        }
    }
}

/**
 * Previews all game layouts together in a single grid canvas.
 */
@Preview(widthDp = 1000, heightDp = 2000, name = "All Games Grid")
@Composable
fun AllGamesGridPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ALL_GAMES.chunked(4).forEach { rowGames ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    rowGames.forEach { game ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = titleOf(game),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            ) {
                                AppScaffold {
                                    val session = createSampleSession(game)
                                    GameScreen(session = session, onHome = {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
