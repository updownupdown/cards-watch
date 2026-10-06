package com.updownupdown.cards.watch.games

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.updownupdown.cards.watch.cards.CARD_FLIP_MS
import com.updownupdown.cards.watch.cards.CARD_MOVE_MS
import com.updownupdown.cards.watch.cards.Card
import com.updownupdown.cards.watch.cards.cardTag
import com.updownupdown.cards.watch.game.Feedback
import com.updownupdown.cards.watch.games.fortune.Call
import com.updownupdown.cards.watch.games.fortune.FortunesCall
import com.updownupdown.cards.watch.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** What Fortune's Call's cards do after a call, stepping the clock by hand. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
class FortunesCallBoardTest {
    @get:Rule
    val compose = createComposeRule()

    private object Quiet : Feedback {
        override fun move() {}
        override fun reject() {}
    }

    private var state by mutableStateOf(FortunesCall.newGame(Random(3)))

    private fun x(card: Card) = compose.onNodeWithTag(cardTag(card)).fetchSemanticsNode().positionInRoot.x
    private fun gone(card: Card) = compose.onNodeWithTag(cardTag(card)).assertDoesNotExist()

    private fun step(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test
    fun theCalledCardShowsThenTakesTheOthersPlaceAndANewOneArrives() {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { FortunesCall.Board(state, { state = it }, Quiet, Modifier.size(123.dp)) } }
        step(50)
        val compared = state.anchor
        val called = state.deck[0]
        val after = state.deck[1]
        val left = x(called)
        val right = x(compared)
        assertTrue("the card to call is left of the one to compare", left < right)

        // When each step starts, from the call, as the board times them.
        val leaves = CARD_FLIP_MS + FortunesCall.SHOW_MS
        val crosses = leaves + FortunesCall.FOLLOW_MS
        val crossed = crosses + CARD_MOVE_MS
        val next = crosses + CARD_MOVE_MS + FortunesCall.NEXT_MS
        // Half the gap between two steps, to look between them.
        fun between(a: Long, b: Long) = (a + b) / 2

        state = FortunesCall.call(state, Call.Red)!!
        var now = between(0, leaves)
        step(now)
        // Turned over where it was, with the old card still beside it and no new card yet.
        assertEquals(left, x(called), 1f)
        assertEquals(right, x(compared), 1f)
        gone(after)

        // The old card leaves, the called one crosses, and only then the next arrives.
        step(between(crosses, crossed) - now)
        now = between(crosses, crossed)
        assertTrue("the compared card is leaving to the right", x(compared) > right)
        step(between(crossed, next) - now)
        now = between(crossed, next)
        assertEquals(right, x(called), 1f)
        gone(compared)
        gone(after)
        step(next + CARD_MOVE_MS + 500 - now)
        assertEquals(left, x(after), 1f)
    }
}
