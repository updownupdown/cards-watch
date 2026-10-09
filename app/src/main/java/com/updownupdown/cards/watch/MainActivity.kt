package com.updownupdown.cards.watch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.updownupdown.cards.watch.data.LocalEnglishTitles
import com.updownupdown.cards.watch.data.SaveStore
import com.updownupdown.cards.watch.data.autoLanguage
import com.updownupdown.cards.watch.data.withAppLanguage
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.gameById
import com.updownupdown.cards.watch.ui.AmbientScreen
import com.updownupdown.cards.watch.ui.AppTheme
import com.updownupdown.cards.watch.ui.GameScreen
import com.updownupdown.cards.watch.ui.HomeScreen
import com.updownupdown.cards.watch.ui.LocalAsleep
import com.updownupdown.cards.watch.ui.LocalHapticsOn
import com.updownupdown.cards.watch.ui.SettingsScreen
import com.updownupdown.cards.watch.ui.SplashScreen
import com.updownupdown.cards.watch.ui.StatsScreen
import com.updownupdown.cards.watch.ui.TINT_MS
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
    /** Whether that game's board is in view: not scrolled down to its rules, nor under its result. */
    private var boardShown by mutableStateOf(true)
    /** When the watch last woke, to let the tap that woke it go nowhere. */
    private var wokeAt = 0L
    /** Whether the touch going on now is kept from the app (it began asleep, or as the watch woke). */
    private var swallowing = false
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
                wokeAt = SystemClock.uptimeMillis()
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
        // A tap that wakes the watch only wakes it: it doesn't reach the game
        // under the sleep screen, whether it arrives before the watch wakes or just after.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowing = ambient || SystemClock.uptimeMillis() - wokeAt < WAKE_GRACE_MS
        }
        if (swallowing) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        stayAwake()
        // Nor does the crown, turned while asleep.
        if (ambient) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // In the language chosen in Settings.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(withAppLanguage(base))
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
        // A fresh launch shows the logo first, while what's under it gets ready.
        var splash by mutableStateOf(savedInstanceState == null)
        var tinted by mutableStateOf(false)
        setContent {
            AppTheme(saves.colourTheme.accent) {
                CompositionLocalProvider(
                    LocalHapticsOn provides saves.hapticsOn,
                    LocalAsleep provides ambient,
                    LocalEnglishTitles provides saves.englishTitles,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppScaffold {
                            App(
                                saves,
                                startRoute = resume?.let { gameRoute(it.id) } ?: HOME,
                                requested = requested,
                                onOpened = { requested = null },
                                onBoardShown = { boardShown = it },
                            ) {
                                playing = it
                                stayAwake()
                            }
                        }
                        if (splash) {
                            LaunchedEffect(Unit) {
                                // White on black first, then the spade takes the theme's colour.
                                delay(SPLASH_WHITE_MS)
                                tinted = true
                                delay(TINT_MS + SPLASH_HOLD_MS)
                                splash = false
                            }
                        }
                        AnimatedVisibility(splash, enter = EnterTransition.None, exit = fadeOut(tween(SPLASH_FADE_MS))) {
                            SplashScreen(saves.colourTheme.accent, tinted)
                        }
                        // Over the app, so the game underneath keeps its place. A game's board shows
                        // only while it's what's in view (not its rules, nor its result); else the logo.
                        if (ambient) AmbientScreen(playing?.takeIf { boardShown }?.let { saves.session(it) }, ambientTick)
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

/**
 * As the app starts: how long the logo stays white, how long it holds once
 * tinted ([TINT_MS] after it starts to turn), and how long it takes to fade.
 */
private const val SPLASH_WHITE_MS = 200L
private const val SPLASH_HOLD_MS = 200L
private const val SPLASH_FADE_MS = 250

/** How soon after waking a touch may reach the app: the one that woke it doesn't. */
private const val WAKE_GRACE_MS = 400L

/** How long the home list stays blank under a game the app opens into. */
private const val OPENING_MS = 600L

private const val HOME = "home"
private const val STATS = "stats"
private const val SETTINGS = "settings"
private const val GAME_ROUTE = "game/{id}"
private fun gameRoute(id: String) = "game/$id"

@Composable
private fun App(
    saves: SaveStore,
    startRoute: String,
    requested: String?,
    onOpened: () -> Unit,
    onBoardShown: (Boolean) -> Unit,
    onPlaying: (Game<*>?) -> Unit,
) {
    val nav = rememberSwipeDismissableNavController()
    val activity = LocalActivity.current
    // The game on top, if it's a game, from the back stack: the home list stays
    // composed under a game (to be seen as it's swiped away), so it can't tell.
    val top by nav.currentBackStackEntryFlow.collectAsState(null)
    val playing = top?.takeIf { it.destination.route == GAME_ROUTE }?.let { gameById(it.arguments?.getString("id")) }
    LaunchedEffect(playing) { onPlaying(playing) }
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
            HomeScreen(
                recent = saves.recentGameIds.mapNotNull(::gameById),
                pinned = saves.pinnedGameIds,
                onOpen = { nav.navigate(gameRoute(it.id)) },
                onPin = saves::togglePin,
                onStats = { nav.navigate(STATS) },
                onSettings = { nav.navigate(SETTINGS) },
                continueShown = saves.continueShown,
            )
        }
        composable(STATS) {
            StatsScreen(saves.allStats(), onOpen = { nav.navigate(gameRoute(it.id)) })
        }
        composable(SETTINGS) {
            SettingsScreen(
                haptics = saves.hapticsOn,
                onHaptics = saves::setHaptics,
                suitColours = saves.suitColours,
                onSuitColours = saves::chooseSuitColours,
                continueShown = saves.continueShown,
                onContinueShown = saves::chooseContinueShown,
                language = saves.language,
                onLanguage = { choice ->
                    if (choice != saves.language) {
                        val shown = saves.language ?: autoLanguage()
                        saves.chooseLanguage(choice)
                        // Made again in the new language, still on Settings; turning
                        // Auto off picks the language already shown, so nothing to remake.
                        if ((choice ?: autoLanguage()) != shown) activity?.recreate()
                    }
                },
                englishTitles = saves.englishTitles,
                onEnglishTitles = saves::chooseEnglishTitles,
                colourTheme = saves.colourTheme,
                onColourTheme = saves::chooseColourTheme,
            )
        }
        composable(GAME_ROUTE) { entry ->
            val game = gameById(entry.arguments?.getString("id")) ?: return@composable
            LaunchedEffect(game.id) { saves.lastGameId = game.id }
            GameScreen(
                saves.session(game),
                onHome = { nav.popBackStack(HOME, inclusive = false) },
                stats = saves.stats(game),
                fourSuitColours = saves.suitColours.fourIn(game),
                onBoardShown = onBoardShown,
            )
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
