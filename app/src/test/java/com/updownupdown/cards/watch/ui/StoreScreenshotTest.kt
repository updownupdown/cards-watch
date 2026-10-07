package com.updownupdown.cards.watch.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.data.AppLanguage
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.elevens.Elevens
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.nestor.Nestor
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.rogue.RogueState
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Play listing's Wear OS screenshots, in each language, on the large round
 * watch (454 px square, black round the screen, as Play asks: no transparency).
 * Images land in app/build/outputs/store/<language>.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
class StoreScreenshotTest(private val language: AppLanguage) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages() = AppLanguage.entries.map { arrayOf(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(language.locale)) }
            val context = LocalContext.current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                AppTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).clip(CircleShape)) {
                        AppScaffold { content() }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().captureRoboImage("build/outputs/store/${language.tag}/$name.png")
    }

    private fun <S : Any> game(name: String, game: Game<S>, state: S) =
        capture(name) { GameScreen(GameSession(game, SavedGame(game.saveVersion, state)) {}, {}) }

    private fun <S : Any> Game<S>.after(seed: Int, vararg moves: (S) -> S?): S =
        moves.fold(newGame(Random(seed))) { state, move -> move(state) ?: state }

    @Test fun home() = capture("1_home") {
        HomeScreen(recent = listOf(RogueRun, Nestor, Lycan), pinned = listOf(Elevens.id), onOpen = {}, onPin = {})
    }

    @Test
    fun rogueRun() = game(
        "2_rogue_run", RogueRun,
        RogueState(
            deck = RogueRun.DECK.take(23),
            room = listOf(Card(Rank.King, Suit.Spades), Card(Rank.Five, Suit.Clubs), Card(Rank.Six, Suit.Hearts), Card(Rank.Four, Suit.Diamonds)),
            roomNumber = 7,
            weapon = Card(Rank.Nine, Suit.Diamonds),
            weaponUsedOn = listOf(Card(Rank.Jack, Suit.Clubs), Card(Rank.Seven, Suit.Spades)),
            health = 14,
            selected = 0,
        ),
    )

    @Test fun nestor() = game("3_nestor", Nestor, Nestor.newGame(Random(4)))

    @Test fun fifteen() = game("4_fifteen", FifteenPuzzle, FifteenPuzzle.newGame(Random(3)))

    @Test fun elevens() = game("5_elevens", Elevens, Elevens.newGame(Random(5)))

    @Test fun lycan() = game("6_lycan", Lycan, Lycan.after(3, { Lycan.togglePlayer(it, it.player[0].id) }))
}
