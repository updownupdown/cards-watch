package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.rememberTextMeasurer
import com.updownupdown.cards.watch.cards.LocalCardText
import androidx.wear.compose.material3.ScreenScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fifteen.FifteenState
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.games.acesup.AcesUp
import com.updownupdown.cards.watch.games.baroness.Baroness
import com.updownupdown.cards.watch.games.beehive.Beehive
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.dominion.Dominion
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.seer.Seer
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import com.updownupdown.cards.watch.games.fortune.Call
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.games.hanoi.TowerOfHanoi
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.memory.Memory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * Renders screens on the small and large round watches, to check layouts
 * without a device. Run `./gradlew recordRoborazziDebug`; images land in
 * app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalCardText provides rememberTextMeasurer()) {
                    // Round mask, as the screen would clip it.
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
                        AppScaffold { content() }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun <S : Any> game(game: Game<S>, state: S = game.newGame(Random(3))) {
        val session = GameSession(game, SavedGame(game.saveVersion, state)) {}
        capture(game.id) { ScreenScaffold(timeText = {}) { GameScreen(session, {}, {}) } }
    }

    private fun <S : Any> Game<S>.after(vararg moves: (S) -> S?): S =
        moves.fold(newGame(Random(3))) { state, move -> move(state) ?: state }

    @Test fun fifteen() = game(FifteenPuzzle)

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun fifteenLarge() = capture("fifteen_large") {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        ScreenScaffold(timeText = {}) { GameScreen(session, {}, {}) }
    }

    @Test
    fun fifteenWon() = capture("fifteen_won") {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenState(FifteenPuzzle.SOLVED, moves = 87))) {}
        ScreenScaffold(timeText = {}) { GameScreen(session, {}, {}) }
    }

    @Test fun memory() = game(Memory, Memory.after({ Memory.tap(it, 0) }, { Memory.tap(it, 7) }))

    @Test fun lightsOut() = game(LightsOut)

    @Test fun hanoi() = game(TowerOfHanoi)

    @Test fun clock() = game(Clock, Clock.after({ Clock.reveal(it) }, { Clock.reveal(it) }, { Clock.reveal(it) }))

    @Test fun acesUp() = game(AcesUp, AcesUp.after({ AcesUp.deal(it) }, { AcesUp.deal(it) }))

    @Test fun baroness() = game(Baroness, Baroness.after({ Baroness.deal(it) }))

    @Test fun beehive() = game(Beehive, Beehive.after({ Beehive.draw(it) }))

    @Test fun tripleStack() = game(TripleStack, TripleStack.after({ TripleStack.toggle(it, 0) }, { TripleStack.toggle(it, 3) }))

    @Test fun dominion() = game(Dominion)

    @Test fun rogueRun() = game(RogueRun, RogueRun.after({ RogueRun.select(it, 1) }))

    @Test fun seer() = game(Seer, Seer.after({ Seer.predict(it, 0) }, { Seer.predict(it, 2) }, { Seer.predict(it, 1) }))

    @Test fun lycan() = game(Lycan, Lycan.after({ Lycan.togglePlayer(it, it.player[0].id) }))

    @Test fun fortune() = game(FortunesCall, FortunesCall.after({ FortunesCall.call(it, Call.Red) }))

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun home() = capture("home") {
        HomeScreen(lastGame = FifteenPuzzle, onOpen = {})
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun rules() = capture("rules") { RulesScreen(FifteenPuzzle) }

    @Test
    fun ambient() = capture("ambient") { AmbientScreen("Tower of Hanoi") }

    @Test fun hanoiMoved() = game(TowerOfHanoi, TowerOfHanoi.after({ TowerOfHanoi.move(it, 0, 1) }, { TowerOfHanoi.move(it, 0, 2) }))
}
