package com.updownupdown.cards.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.updownupdown.cards.watch.cards.LocalCardText
import com.updownupdown.cards.watch.data.SaveStore
import com.updownupdown.cards.watch.games.gameById
import com.updownupdown.cards.watch.ui.AmbientScreen
import com.updownupdown.cards.watch.ui.AppTheme
import com.updownupdown.cards.watch.ui.GameScreen
import com.updownupdown.cards.watch.ui.HomeScreen
import com.updownupdown.cards.watch.ui.RulesScreen

class MainActivity : ComponentActivity() {
    private var ambient by mutableStateOf(false)
    /** Bumped once a minute in ambient mode, to redraw the time. */
    private var ambientTick by mutableIntStateOf(0)
    /** The game on screen, named on the sleep screen. */
    private var playing by mutableStateOf<String?>(null)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient = true
            }

            override fun onUpdateAmbient() {
                ambientTick++
            }

            override fun onExitAmbient() {
                ambient = false
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        val saves = (application as CardsWatchApp).saves
        // A fresh launch goes straight back into the last game played.
        val resume = if (savedInstanceState == null) gameById(saves.lastGameId) else null
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalCardText provides rememberTextMeasurer(cacheSize = 64)) {
                    Box(Modifier.fillMaxSize()) {
                        AppScaffold {
                            App(saves, startRoute = resume?.let { gameRoute(it.id) } ?: HOME) { playing = it }
                        }
                        // Over the app, so the game underneath keeps its place.
                        if (ambient) AmbientScreen(playing, ambientTick)
                    }
                }
            }
        }
    }
}

private const val HOME = "home"
private fun gameRoute(id: String) = "game/$id"
private fun rulesRoute(id: String) = "rules/$id"

@Composable
private fun App(saves: SaveStore, startRoute: String, onPlaying: (String?) -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            LaunchedEffect(Unit) { onPlaying(null) }
            HomeScreen(
                lastGame = gameById(saves.lastGameId),
                onOpen = { nav.navigate(gameRoute(it.id)) },
            )
        }
        composable("game/{id}") { entry ->
            val game = gameById(entry.arguments?.getString("id")) ?: return@composable
            LaunchedEffect(game.id) {
                saves.lastGameId = game.id
                onPlaying(game.title)
            }
            // No clock over the game: the home icon sits there.
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
