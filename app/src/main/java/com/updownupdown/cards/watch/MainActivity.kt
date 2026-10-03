package com.updownupdown.cards.watch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.updownupdown.cards.watch.data.SaveStore
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.gameById
import com.updownupdown.cards.watch.ui.AmbientScreen
import com.updownupdown.cards.watch.ui.AppTheme
import com.updownupdown.cards.watch.ui.GameScreen
import com.updownupdown.cards.watch.ui.HomeScreen
import com.updownupdown.cards.watch.ui.LocalAsleep
import com.updownupdown.cards.watch.ui.LocalHapticsOn
import com.updownupdown.cards.watch.widget.refreshSurfaces
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var ambient by mutableStateOf(false)
    /** Bumped once a minute in ambient mode, to redraw the time. */
    private var ambientTick by mutableIntStateOf(0)
    /** The game asked for from outside (the tile or a complication), until it's opened. */
    private var requested by mutableStateOf<String?>(null)
    /** The game on screen, shown on the sleep screen. */
    private var playing by mutableStateOf<Game<*>?>(null)
    private val saves get() = (application as CardsWatchApp).saves

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient = true
                playing?.let { saves.session(it).pause() }
            }

            override fun onUpdateAmbient() {
                ambientTick++
            }

            override fun onExitAmbient() {
                ambient = false
                playing?.let { saves.session(it).resume() }
            }
        },
    )

    private val handler = Handler(Looper.getMainLooper())
    private val letSleep = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    /**
     * In a game, keeps the screen on for [GAME_SCREEN_ON_MS] after each touch
     * or turn of the crown (the watch's own timeout still applies if longer).
     */
    private fun stayAwake() {
        handler.removeCallbacks(letSleep)
        if (playing != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.postDelayed(letSleep, GAME_SCREEN_ON_MS)
        } else {
            letSleep.run()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        stayAwake()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        stayAwake()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requested = intent.getStringExtra(EXTRA_GAME)
    }

    override fun onDestroy() {
        handler.removeCallbacks(letSleep)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        // A fresh tap for the complication, in case the app was reinstalled since the last one.
        refreshSurfaces(this)
        // A fresh launch goes straight back into the last game played.
        val resume = if (savedInstanceState == null) gameById(intent.getStringExtra(EXTRA_GAME) ?: saves.lastGameId) else null
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalHapticsOn provides saves.hapticsOn, LocalAsleep provides ambient) {
                    Box(Modifier.fillMaxSize()) {
                        AppScaffold {
                            App(saves, startRoute = resume?.let { gameRoute(it.id) } ?: HOME, requested = requested, onOpened = { requested = null }) {
                                playing = it
                                stayAwake()
                            }
                        }
                        // Over the app, so the game underneath keeps its place.
                        if (ambient) AmbientScreen(playing?.let { saves.session(it) }, ambientTick)
                    }
                }
            }
        }
    }
}

/** How long the screen stays on in a game after the last touch. */
private const val GAME_SCREEN_ON_MS = 10_000L

/** The id of the game to open, on an intent from the tile or a complication. */
const val EXTRA_GAME = "game"

/** How long the home list stays blank under a game the app opens into. */
private const val OPENING_MS = 600L

private const val HOME = "home"
private fun gameRoute(id: String) = "game/$id"

@Composable
private fun App(saves: SaveStore, startRoute: String, requested: String?, onOpened: () -> Unit, onPlaying: (Game<*>?) -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    LaunchedEffect(requested) {
        val game = gameById(requested) ?: return@LaunchedEffect
        nav.popBackStack(HOME, inclusive = false)
        nav.navigate(gameRoute(game.id))
        onOpened()
    }
    // Opening into a game, the home list under it stays blank until the game is up, so it never flashes by.
    var opening by remember { mutableStateOf(startRoute != HOME) }
    SwipeDismissableNavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            if (opening) return@composable
            LaunchedEffect(Unit) { onPlaying(null) }
            HomeScreen(
                recent = saves.recentGameIds.mapNotNull(::gameById),
                pinned = saves.pinnedGameIds,
                onOpen = { nav.navigate(gameRoute(it.id)) },
                onPin = saves::togglePin,
                haptics = saves.hapticsOn,
                onHaptics = saves::setHaptics,
            )
        }
        composable("game/{id}") { entry ->
            val game = gameById(entry.arguments?.getString("id")) ?: return@composable
            LaunchedEffect(game.id) {
                saves.lastGameId = game.id
                onPlaying(game)
            }
            GameScreen(saves.session(game), onHome = { nav.popBackStack(HOME, inclusive = false) }, stats = saves.stats(game))
        }
    }
    if (startRoute != HOME) {
        LaunchedEffect(Unit) {
            nav.navigate(startRoute)
            delay(OPENING_MS)
            opening = false
        }
    }
}
