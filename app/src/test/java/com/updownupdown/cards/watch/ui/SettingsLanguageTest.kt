package com.updownupdown.cards.watch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.updownupdown.cards.watch.data.AppLanguage
import com.updownupdown.cards.watch.data.autoLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' Language: a switch for Auto, and the languages only with it off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
class SettingsLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    private var language: AppLanguage? by mutableStateOf(null)

    /** Settings, scrolled to the Language setting, choosing into [language]. */
    private fun show(start: AppLanguage?) {
        language = start
        compose.setContent {
            AppTheme {
                AppScaffold {
                    SettingsScreen(haptics = true, onHaptics = {}, language = language, onLanguage = { language = it }, startAt = 10)
                }
            }
        }
    }

    private val auto = "Auto (English)"

    @Test
    fun autoIsASwitchNamingItsLanguage() {
        assertEquals(AppLanguage.English, autoLanguage())
        show(null)
        compose.onNodeWithText(auto).assertIsOn()
        compose.onNodeWithText("Français").assertDoesNotExist()
        compose.onNodeWithText("Türkçe").assertDoesNotExist()
    }

    @Test
    fun turningAutoOffKeepsItsLanguageAndListsTheOthers() {
        show(null)
        compose.onNodeWithText(auto).performClick()
        compose.waitForIdle()
        assertEquals(AppLanguage.English, language)
        compose.onNodeWithText(auto).assertIsOff()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Français").assertExists()
    }

    @Test
    fun aChosenLanguageShowsTheListAndAutoTurnsBackOn() {
        show(AppLanguage.Turkish)
        compose.onNodeWithText(auto).assertIsOff()
        compose.onNodeWithText("Français").performClick()
        assertEquals(AppLanguage.French, language)
        compose.onNodeWithText(auto).performClick()
        compose.waitForIdle()
        assertNull(language)
        compose.onNodeWithText("Français").assertDoesNotExist()
    }
}
