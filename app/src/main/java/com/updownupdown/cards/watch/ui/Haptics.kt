package com.updownupdown.cards.watch.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.updownupdown.cards.watch.game.Feedback

/** The app's haptics. Everything that buzzes goes through here, to tune in one place. */
class Haptics(private val view: View) : Feedback {
    override fun move() = buzz(HapticFeedbackConstants.CONTEXT_CLICK)
    override fun reject() = buzz(HapticFeedbackConstants.REJECT)
    fun press() = buzz(HapticFeedbackConstants.CONTEXT_CLICK)
    fun longPress() = buzz(HapticFeedbackConstants.LONG_PRESS)
    fun win() = buzz(HapticFeedbackConstants.CONFIRM)

    private fun buzz(type: Int) {
        view.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
