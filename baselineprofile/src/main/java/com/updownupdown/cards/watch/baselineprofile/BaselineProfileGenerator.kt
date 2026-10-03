package com.updownupdown.cards.watch.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through the app on a watch to record the code it runs: the home
 * list, a game (the one the list offers first), a few taps on its board, and
 * its rules. The taps are real moves in that game's saved deal.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.updownupdown.cards.watch", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        val x = device.displayWidth / 2
        val height = device.displayHeight
        fun at(fraction: Float) = (height * fraction).toInt()

        // Back to the home list (the app reopens the last game), and through it.
        device.pressBack()
        device.waitForIdle()
        device.swipe(x, at(0.7f), x, at(0.3f), 12)
        device.swipe(x, at(0.3f), x, at(0.7f), 12)
        device.waitForIdle()

        // Into the first game on the list, and a few taps across its board.
        device.click(x, at(0.55f))
        device.waitForIdle()
        for ((dx, dy) in listOf(0.4f to 0.35f, 0.6f to 0.5f, 0.45f to 0.6f, 0.55f to 0.4f)) {
            device.click((device.displayWidth * dx).toInt(), at(dy))
            Thread.sleep(700)
        }

        // Down to its buttons and rules, and back.
        device.swipe(x, at(0.75f), x, at(0.25f), 12)
        device.swipe(x, at(0.75f), x, at(0.25f), 12)
        device.waitForIdle()
        device.swipe(x, at(0.3f), x, at(0.8f), 12)
        device.swipe(x, at(0.3f), x, at(0.8f), 12)
        device.waitForIdle()
    }
}
