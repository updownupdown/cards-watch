package com.updownupdown.cards.watch.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.data.AppLanguage
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.augury.Augury
import com.updownupdown.cards.watch.games.baroness.Baroness
import com.updownupdown.cards.watch.games.beehive.Beehive
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.dominion.Dominion
import com.updownupdown.cards.watch.games.ebbandflow.EbbAndFlow
import com.updownupdown.cards.watch.games.elevens.Elevens
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fifteen.FifteenState
import com.updownupdown.cards.watch.games.fortune.Call
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.games.jailbreak.Jailbreak
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.nestor.Nestor
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.rogue.RogueState
import com.updownupdown.cards.watch.games.royalmarriage.RoyalMarriage
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens with the most text, in each language but English, on the small
 * round watch, to check translations fit. Images land in
 * app/build/outputs/roborazzi/<language>.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
class LocaleScreenshotTest(private val language: AppLanguage) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages() = AppLanguage.entries.filter { it != AppLanguage.English }.map { arrayOf(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(language.locale)) }
            val context = base.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                AppTheme {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
                        AppScaffold { content() }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${language.tag}/$name.png")
    }

    private fun <S : Any> game(game: Game<S>, state: S = game.newGame(Random(3))) {
        val session = GameSession(game, SavedGame(game.saveVersion, state)) {}
        capture(game.id) { GameScreen(session, {}) }
    }

    private fun <S : Any> Game<S>.after(vararg moves: (S) -> S?): S =
        moves.fold(newGame(Random(3))) { state, move -> move(state) ?: state }

    private val someStats = mapOf(
        Nestor.id to GameStats(won = 7, lost = 3, bestStreak = 4, bestTimeMs = 154_000),
        Lycan.id to GameStats(won = 1, lost = 4, bestStreak = 1, bestScore = 31, bestTimeMs = 402_000),
        RogueRun.id to GameStats(won = 2, lost = 2, bestStreak = 2, bestScore = 14, bestTimeMs = 233_000),
    )

    @Test fun home() = capture("home") { HomeScreen(recent = listOf(FifteenPuzzle, Nestor), pinned = listOf(Lycan.id), onOpen = {}, onPin = {}) }

    @Test fun settings() = capture("settings") { SettingsScreen(haptics = true, onHaptics = {}, continueShown = 1) }

    @Test fun settingsLanguage() = capture("settings_language") { SettingsScreen(haptics = true, onHaptics = {}, language = language, startAt = 19) }

    @Test fun stats() = capture("stats") { StatsScreen(someStats, onOpen = {}) }

    // Down to each game's line, under its heading.
    @Test fun statsScrolled() = capture("stats_scrolled", before = { compose.onRoot().performTouchInput { swipeUp() } }) {
        StatsScreen(someStats, onOpen = {})
    }

    @Test fun statsEmpty() = capture("stats_empty") { StatsScreen(emptyMap(), onOpen = {}) }

    private fun fifteen() = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}

    @Test fun rules() = capture("rules") { GameScreen(fifteen(), {}, ScrollState(454)) }

    @Test
    fun actions() = capture("actions") {
        GameScreen(GameSession(Baroness, SavedGame(1, Baroness.newGame(Random(3)))) {}, {}, ScrollState(actionsSnap(384, LocalDensity.current)))
    }

    @Test
    fun rulesEnd() = capture("rules_end") {
        GameScreen(fifteen(), {}, ScrollState(100_000), GameStats(won = 5, lost = 3, streak = 1, bestStreak = 3, bestScore = 64, bestTimeMs = 92_000))
    }

    @Test
    fun ratings() = capture("ratings", before = {
        repeat(5) { compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 330f), durationMillis = 3000) } }
    }) { GameScreen(fifteen(), {}, ScrollState(100_000), GameStats(won = 5, lost = 3)) }

    @Test
    fun fifteenWon() = capture("fifteen_won") {
        val nearly = FifteenPuzzle.slide(FifteenState(FifteenPuzzle.SOLVED, moves = 85), 14)!!
        val session = GameSession(FifteenPuzzle, SavedGame(1, nearly)) {}
        LaunchedEffect(Unit) { session.play(FifteenPuzzle.slide(session.state, 15)!!) }
        GameScreen(session, {}, stats = GameStats(won = 4, lost = 8, bestScore = 70, bestTimeMs = 70_000))
    }

    @Test
    fun rogueLost() = capture("rogue_lost") {
        val state = RogueState(
            deck = RogueRun.DECK.take(12),
            room = listOf(Card(Rank.King, Suit.Spades), Card(Rank.Five, Suit.Clubs), null, Card(Rank.Four, Suit.Hearts)),
            health = 3,
        )
        val session = GameSession(RogueRun, SavedGame(RogueRun.saveVersion, state)) {}
        LaunchedEffect(Unit) { session.play(RogueRun.fight(session.state, 0, false)!!) }
        GameScreen(session, {}, stats = GameStats(won = 4, lost = 8, bestTimeMs = 70_000))
    }

    @Test
    fun rogueWeapon() = capture("rogue_weapon") {
        val state = RogueState(
            deck = RogueRun.DECK.take(12),
            room = listOf(Card(Rank.King, Suit.Spades), Card(Rank.Five, Suit.Clubs), null, Card(Rank.Four, Suit.Hearts)),
            roomNumber = 7,
            weapon = Card(Rank.Seven, Suit.Diamonds),
            weaponUsedOn = listOf(Card(Rank.Nine, Suit.Clubs), Card(Rank.Six, Suit.Spades)),
            health = 11,
            selected = 1,
        )
        GameScreen(GameSession(RogueRun, SavedGame(RogueRun.saveVersion, state)) {}, {})
    }

    @Test fun acesUp() = game(AcesUp, AcesUp.after({ AcesUp.deal(it) }))

    @Test fun baroness() = game(Baroness)

    @Test fun beehive() = game(Beehive, Beehive.after({ Beehive.draw(it) }))

    @Test fun clock() = game(Clock)

    @Test fun dominion() = game(Dominion)

    @Test fun ebbAndFlow() = game(EbbAndFlow)

    @Test fun elevens() = game(Elevens)

    @Test fun fortune() = game(FortunesCall, FortunesCall.after({ FortunesCall.call(it, Call.Red) }))

    @Test fun jailbreak() = game(Jailbreak, Jailbreak.after({ Jailbreak.draw(it) }))

    @Test fun littleWindmill() = game(LittleWindmill)

    @Test fun lycan() = game(Lycan, Lycan.after({ Lycan.togglePlayer(it, it.player[0].id) }))

    @Test fun ouroboros() = game(Ouroboros, Ouroboros.after({ Ouroboros.lookAround(it) }))

    @Test fun royalMarriage() = game(RoyalMarriage, RoyalMarriage.after({ RoyalMarriage.toggle(it, 2) }))

    @Test fun tripleStack() = game(TripleStack, TripleStack.after({ TripleStack.toggle(it, 0) }, { TripleStack.toggle(it, 3) }))

    @Test fun augury() = game(Augury, Augury.after({ Augury.playCard(it, it.hand[0]) }))

    @Test fun rogueRun() = game(RogueRun, RogueRun.after({ RogueRun.select(it, 1) }))
}
