package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.Rank
import com.updownupdown.cards.watch.cards.Suit
import com.updownupdown.cards.watch.game.Game
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.GameStats
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.accordion.Accordion
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
import com.updownupdown.cards.watch.games.littlewindmill.LittleWindmill
import com.updownupdown.cards.watch.games.hanoi.TowerOfHanoi
import com.updownupdown.cards.watch.games.jailbreak.Jailbreak
import com.updownupdown.cards.watch.games.lightsout.LightsOut
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.memory.Memory
import com.updownupdown.cards.watch.games.nestor.Nestor
import com.updownupdown.cards.watch.games.ouroboros.Ouroboros
import com.updownupdown.cards.watch.games.ozanam.OzanamsSquare
import com.updownupdown.cards.watch.games.ozanam.Source
import com.updownupdown.cards.watch.games.rogue.RogueRun
import com.updownupdown.cards.watch.games.royalmarriage.RoyalMarriage
import com.updownupdown.cards.watch.games.rogue.RogueState
import com.updownupdown.cards.watch.games.tentwentythirty.TenTwentyThirty
import com.updownupdown.cards.watch.games.triplestack.TripleStack
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private fun capture(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme {
                // Round mask, as the screen would clip it.
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black)) {
                    AppScaffold { content() }
                }
            }
        }
        // Long enough for a result to come up after a winning move.
        compose.mainClock.advanceTimeBy(2_000)
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun <S : Any> game(game: Game<S>, state: S = game.newGame(Random(3))) {
        val session = GameSession(game, SavedGame(game.saveVersion, state)) {}
        capture(game.id) { GameScreen(session, {}) }
    }

    private fun <S : Any> Game<S>.after(vararg moves: (S) -> S?): S =
        moves.fold(newGame(Random(3))) { state, move -> move(state) ?: state }

    @Test fun fifteen() = game(FifteenPuzzle)

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun fifteenLarge() = capture("fifteen_large") {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        GameScreen(session, {})
    }

    @Test
    fun fifteenWon() = capture("fifteen_won") {
        // One slide from solved, then that slide.
        val nearly = FifteenPuzzle.slide(FifteenState(FifteenPuzzle.SOLVED, moves = 85), 14)!!
        val session = GameSession(FifteenPuzzle, SavedGame(1, nearly)) {}
        LaunchedEffect(Unit) { session.play(FifteenPuzzle.slide(session.state, 15)!!) }
        GameScreen(session, {})
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

    @Test
    fun rogueWeapon() = capture("rogue_weapon") {
        // A monster picked that the weapon can fight: Wpn takes the place of the room and stock.
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


    @Test fun lycan() = game(Lycan, Lycan.after({ Lycan.togglePlayer(it, it.player[0].id) }))

    @Test fun ebbAndFlow() = game(EbbAndFlow)

    @Test
    fun ebbAndFlowPlayed() = capture("ebb-and-flow_played") {
        val state = EbbAndFlow.after({ EbbAndFlow.play(it, it.hand.first(), 0) }, { EbbAndFlow.play(it, it.hand.last(), 2) })
        GameScreen(GameSession(EbbAndFlow, SavedGame(EbbAndFlow.saveVersion, state)) {}, {})
    }

    @Test fun littleWindmill() = game(LittleWindmill, LittleWindmill.after({ LittleWindmill.draw(it) }, { LittleWindmill.draw(it) }))

    @Test fun nestor() = game(Nestor)

    @Test fun jailbreak() = game(Jailbreak, Jailbreak.after({ Jailbreak.draw(it) }, { Jailbreak.draw(it) }))

    @Test fun accordion() = game(Accordion)

    @Test fun fortune() = game(FortunesCall, FortunesCall.after({ FortunesCall.call(it, Call.Red) }))

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun home() = capture("home") {
        HomeScreen(recent = listOf(FifteenPuzzle, Nestor), pinned = listOf(Lycan.id), onOpen = {}, onPin = {}, haptics = true, onHaptics = {})
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun homeSettings() = capture("home_settings") {
        HomeScreen(
            recent = listOf(FifteenPuzzle, Nestor),
            pinned = emptyList(),
            onOpen = {},
            onPin = {},
            haptics = true,
            onHaptics = {},
            continueShown = 1,
            stats = mapOf(Nestor.id to GameStats(won = 3, lost = 2, bestStreak = 2), Lycan.id to GameStats(lost = 4)),
            startAt = 31,
        )
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun homeStats() = capture("home_stats") {
        HomeScreen(
            recent = listOf(FifteenPuzzle, Nestor),
            pinned = emptyList(),
            onOpen = {},
            onPin = {},
            haptics = true,
            onHaptics = {},
            continueShown = 1,
            stats = mapOf(Nestor.id to GameStats(won = 3, lost = 2, bestStreak = 2), Lycan.id to GameStats(lost = 4)),
            startAt = 42,
        )
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun rogueLarge() = capture("rogue_large") {
        GameScreen(GameSession(RogueRun, SavedGame(RogueRun.saveVersion, RogueRun.newGame(Random(3)))) {}, {})
    }

    @Test
    fun rogueLost() = capture("rogue_lost") {
        // One fist away from death.
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
    fun fourSuitsOff() = capture("dominion_two_colours") {
        val session = GameSession(Dominion, SavedGame(Dominion.saveVersion, Dominion.newGame(Random(3)))) {}
        GameScreen(session, {}, fourSuitColours = false)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun rules() = capture("rules") {
        // Scrolled a screen down, to the top of the rules.
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        GameScreen(session, {}, ScrollState(454))
    }

    @Test
    fun actions() = capture("actions") {
        // Scrolled to where the view snaps: Reset and Undo under the board.
        val session = GameSession(Baroness, SavedGame(1, Baroness.newGame(Random(3)))) {}
        GameScreen(session, {}, ScrollState(actionsSnap(384, LocalDensity.current)))
    }

    @Test
    fun rulesEnd() = capture("rules_end") {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        GameScreen(session, {}, ScrollState(100_000), GameStats(won = 5, lost = 3, streak = 1, bestStreak = 3, bestScore = 64, bestTimeMs = 92_000))
    }

    @Test
    fun ratings() = capture("ratings", before = { compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 330f), durationMillis = 3000) }; compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 330f), durationMillis = 3000) } }) {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        GameScreen(session, {}, ScrollState(100_000), GameStats(won = 5, lost = 3))
    }

    @Test
    fun ambient() = capture("ambient") { AmbientScreen(null) }

    @Test
    fun ambientGame() = capture("ambient_game") {
        val state = Beehive.after({ Beehive.draw(it) })
        AmbientScreen(GameSession(Beehive, SavedGame(Beehive.saveVersion, state)) {})
    }

    @Test fun hanoiMoved() = game(TowerOfHanoi, TowerOfHanoi.after({ TowerOfHanoi.move(it, 0, 1) }, { TowerOfHanoi.move(it, 0, 2) }))

    @Test fun elevens() = game(Elevens)

    @Test fun tenTwentyThirty() = game(TenTwentyThirty)


    @Test fun royalMarriage() = game(RoyalMarriage, RoyalMarriage.after({ RoyalMarriage.toggle(it, 2) }))

    @Test fun ozanamsSquare() = game(OzanamsSquare, OzanamsSquare.after({ OzanamsSquare.place(it, Source.Tray(it.tray[0][0]), it.given.indexOf(false)) }))

    @Test fun augury() = game(Augury, Augury.after({ Augury.playCard(it, it.hand[0]) }, { Augury.playCard(it, it.hand[1]) }, { Augury.playCard(it, it.hand[2]) }))

    @Test fun auguryLong() = capture("augury_long") {
        val state = Augury.after(*Array(14) { { s: com.updownupdown.cards.watch.games.augury.AuguryState -> Augury.playCard(s, s.hand[0]) } })
        GameScreen(GameSession(Augury, SavedGame(Augury.saveVersion, state)) {}, {})
    }

    @Test fun ouroboros() = game(Ouroboros, Ouroboros.after({ Ouroboros.lookAround(it) }))

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
    fun ouroborosLarge() = capture("ouroboros_large") {
        val state = Ouroboros.after({ Ouroboros.lookAround(it) }, { s -> Ouroboros.tops(s).firstOrNull { Ouroboros.usable(s, it) }?.let { Ouroboros.mark(s, it, 1) } })
        GameScreen(GameSession(Ouroboros, SavedGame(Ouroboros.saveVersion, state)) {}, {})
    }

    @Test fun ouroborosPicked() = capture("ouroboros_picked", before = { compose.onRoot().performTouchInput { click(Offset(168f, 345f)) } }) {
        GameScreen(GameSession(Ouroboros, SavedGame(Ouroboros.saveVersion, Ouroboros.after({ Ouroboros.lookAround(it) }))) {}, {})
    }

    // The second card, a number: its target comes in from the loop.
    @Test fun ouroborosTarget() = capture("ouroboros_target", before = { compose.onRoot().performTouchInput { click(Offset(216f, 345f)) } }) {
        GameScreen(GameSession(Ouroboros, SavedGame(Ouroboros.saveVersion, Ouroboros.after({ Ouroboros.lookAround(it) }))) {}, {})
    }

    @Test fun splash() = capture("splash") { SplashScreen() }
}
