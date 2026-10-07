package com.updownupdown.cards.watch.data

import android.content.Context
import com.updownupdown.cards.watch.games.nestor.Nestor
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Language and English Game Titles settings, saved and applied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanguageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val saves = SaveStore(context, CoroutineScope(Dispatchers.Unconfined))

    private fun language(of: Context) = of.resources.configuration.locales[0].language

    @Test
    fun autoLeavesTheWatchsLanguage() {
        saves.chooseLanguage(null)
        assertNull(chosenLanguage(context))
        assertSame(context, withAppLanguage(context))
    }

    @Test
    fun aChosenLanguageIsApplied() {
        saves.chooseLanguage(AppLanguage.French)
        assertEquals(AppLanguage.French, chosenLanguage(context))
        assertEquals("fr", language(withAppLanguage(context)))
        // And kept: a new store reads it back.
        assertEquals(AppLanguage.French, SaveStore(context, CoroutineScope(Dispatchers.Unconfined)).language)
        saves.chooseLanguage(null)
    }

    @Test
    fun indonesianIsKnownByEitherCode() {
        assertEquals(AppLanguage.Indonesian, AppLanguage.of(Locale.forLanguageTag("id")))
        assertEquals(AppLanguage.Indonesian, AppLanguage.of(Locale("in")))
        assertNull(AppLanguage.of(Locale.GERMAN))
    }

    @Test
    fun englishTitlesComeFromEnglishResources() {
        assertFalse(englishTitles(context))
        saves.chooseEnglishTitles(true)
        assertTrue(englishTitles(context))
        assertEquals("en", englishResources(withAppLanguage(context)).configuration.locales[0].language)
        assertEquals("Nestor", titleOf(Nestor, context))
        saves.chooseEnglishTitles(false)
    }
}
