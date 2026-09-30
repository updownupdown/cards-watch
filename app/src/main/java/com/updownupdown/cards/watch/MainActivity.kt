package com.updownupdown.cards.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.updownupdown.cards.watch.data.SaveStore
import com.updownupdown.cards.watch.games.gameById
import com.updownupdown.cards.watch.ui.GameScreen
import com.updownupdown.cards.watch.ui.HomeScreen
import com.updownupdown.cards.watch.ui.RulesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saves = (application as CardsWatchApp).saves
        // A fresh launch goes straight back into the last game played.
        val resume = if (savedInstanceState == null) gameById(saves.lastGameId) else null
        setContent {
            MaterialTheme {
                AppScaffold {
                    App(saves, startRoute = resume?.let { gameRoute(it.id) } ?: HOME)
                }
            }
        }
    }
}

private const val HOME = "home"
private fun gameRoute(id: String) = "game/$id"
private fun rulesRoute(id: String) = "rules/$id"

@Composable
private fun App(saves: SaveStore, startRoute: String) {
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                lastGame = gameById(saves.lastGameId),
                onOpen = { nav.navigate(gameRoute(it.id)) },
            )
        }
        composable("game/{id}") { entry ->
            val game = gameById(entry.arguments?.getString("id")) ?: return@composable
            LaunchedEffect(game.id) { saves.lastGameId = game.id }
            // No clock over the game: the home button sits there.
            ScreenScaffold(timeText = {}) {
                GameScreen(
                    session = saves.session(game),
                    onHome = { nav.popBackStack(HOME, inclusive = false) },
                    onRules = { nav.navigate(rulesRoute(game.id)) },
                )
            }
        }
        composable("rules/{id}") { entry ->
            gameById(entry.arguments?.getString("id"))?.let { RulesScreen(it) }
        }
    }
    if (startRoute != HOME) {
        LaunchedEffect(Unit) { nav.navigate(startRoute) }
    }
}
