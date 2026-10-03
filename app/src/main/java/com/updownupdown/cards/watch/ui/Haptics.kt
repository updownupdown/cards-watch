package com.updownupdown.cards.watch.ui

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.updownupdown.cards.watch.game.Feedback

/** The app's haptics. Everything that buzzes goes through here, to tune in one place. */
class Haptics(private val view: View, private val enabled: () -> Boolean = { true }) : Feedback {
    override fun move() = buzz(HapticFeedbackConstants.CONTEXT_CLICK)
    override fun reject() = buzz(HapticFeedbackConstants.REJECT)
    fun press() = buzz(HapticFeedbackConstants.CONTEXT_CLICK)
    fun longPress() = buzz(HapticFeedbackConstants.LONG_PRESS)
    fun win() = buzz(HapticFeedbackConstants.CONFIRM)
    fun lose() = buzz(HapticFeedbackConstants.REJECT)

    private fun buzz(type: Int) {
        if (enabled()) view.performHapticFeedback(type)
    }
}

/** Whether the app's own haptics switch (on the home screen) is on. */
val LocalHapticsOn = compositionLocalOf { true }

/** Whether the watch's own settings allow haptic feedback (the app's switch can't turn it on past that). */
// Deprecated for apps that set the switch, but there's no other way to read it.
@Suppress("DEPRECATION")
fun systemHapticsOn(context: Context) =
    Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val on by rememberUpdatedState(LocalHapticsOn.current)
    return remember(view) { Haptics(view) { on } }
}
