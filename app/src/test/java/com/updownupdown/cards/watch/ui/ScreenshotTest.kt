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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.fifteen.FifteenState
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
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MaterialTheme {
                // Round mask, as the screen would clip it.
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
                    AppScaffold { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun fifteen(state: FifteenState = FifteenPuzzle.newGame(Random(3))) =
        GameSession(FifteenPuzzle, SavedGame(1, state)) {}

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
    fun fifteenSmall() = capture("fifteen_small") {
        ScreenScaffold(timeText = {}) { GameScreen(fifteen(), {}, {}) }
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun fifteenLarge() = capture("fifteen_large") {
        ScreenScaffold(timeText = {}) { GameScreen(fifteen(), {}, {}) }
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
    fun fifteenWonSmall() = capture("fifteen_won_small") {
        ScreenScaffold(timeText = {}) {
            GameScreen(fifteen(FifteenState(FifteenPuzzle.SOLVED, moves = 87)), {}, {})
        }
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun home() = capture("home") {
        HomeScreen(lastGame = FifteenPuzzle, onOpen = {})
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun rules() = capture("rules") { RulesScreen(FifteenPuzzle) }
}
