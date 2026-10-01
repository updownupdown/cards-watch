package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.updownupdown.cards.watch.game.GameSession
import com.updownupdown.cards.watch.game.SavedGame
import com.updownupdown.cards.watch.games.fifteen.FifteenPuzzle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** The game screen snaps to the board or to the Reset and Undo buttons under it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSSmallRound)
class GameScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val scroll = ScrollState(0)

    /** Shows a game and returns the scroll offset the view snaps to below the board. */
    private fun show(): Int {
        val session = GameSession(FifteenPuzzle, SavedGame(1, FifteenPuzzle.newGame(Random(3)))) {}
        compose.setContent { AppTheme { AppScaffold { GameScreen(session, {}, scroll) } } }
        return actionsSnap(compose.onRoot().fetchSemanticsNode().size.height, compose.density)
    }

    /** Drags up from the middle by [fraction] of the screen, slowly or as a flick. */
    private fun dragUp(fraction: Float, millis: Long) {
        compose.onRoot().performTouchInput { swipeUp(centerY, centerY - height * fraction, millis) }
        compose.waitForIdle()
    }

    @Test
    fun aSmallDragSpringsBackToTheBoard() {
        show()
        dragUp(0.15f, 1500)
        assertEquals(0, scroll.value)
    }

    @Test
    fun aSmallFlickSpringsBackToTheBoard() {
        show()
        dragUp(0.1f, 60)
        assertEquals(0, scroll.value)
    }

    @Test
    fun aLongDragGoesToTheButtons() {
        val page = show()
        dragUp(0.45f, 1500)
        assertEquals(page, scroll.value)
    }

    @Test
    fun aFlickGoesToTheButtons() {
        val page = show()
        dragUp(0.32f, 60)
        assertEquals(page, scroll.value)
    }

    @Test
    fun draggingBackFromTheButtonsReturnsToTheBoard() {
        val page = show()
        dragUp(0.45f, 1500)
        assertEquals(page, scroll.value)
        compose.onRoot().performTouchInput { swipeDown(centerY, centerY + height * 0.32f, 60) }
        compose.waitForIdle()
        assertEquals(0, scroll.value)
    }
}
