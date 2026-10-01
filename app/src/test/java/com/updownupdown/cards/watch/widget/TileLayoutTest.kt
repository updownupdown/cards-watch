package com.updownupdown.cards.watch.widget

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import com.updownupdown.cards.watch.games.clock.Clock
import com.updownupdown.cards.watch.games.lycan.Lycan
import com.updownupdown.cards.watch.games.memory.Memory
import com.updownupdown.cards.watch.games.seer.Seer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The tile's layout builds for every number of recent games, on both screen sizes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TileLayoutTest {
    private fun device(width: Int) = DeviceParameters.Builder().setScreenWidthDp(width).setScreenHeightDp(width).build()

    private val games = listOf(Seer, Lycan, Clock, Memory)

    @Test
    fun itBuildsWithAnyNumberOfRecentGames() {
        for (width in listOf(192, 227)) {
            for (count in 0..games.size) tileLayout(RuntimeEnvironment.getApplication(), games.take(count), device(width))
        }
    }

    @Test
    fun itShowsTheTwoGamesBeforeTheLast() {
        assertEquals(emptyList<Any>(), othersOnTile(games.take(1)))
        assertEquals(listOf(Lycan, Clock), othersOnTile(games))
    }
}
