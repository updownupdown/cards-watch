package com.updownupdown.cards.watch.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.game.Game
import java.util.Locale

/**
 * The languages the app is in: the phone app's (its `src/i18n/languages.ts`),
 * each named in itself, as the Settings screen lists them.
 */
enum class AppLanguage(val tag: String, @param:StringRes val nativeName: Int) {
    English("en", R.string.language_en),
    French("fr", R.string.language_fr),
    Spanish("es", R.string.language_es),
    Italian("it", R.string.language_it),
    Portuguese("pt", R.string.language_pt),
    Turkish("tr", R.string.language_tr),
    Indonesian("id", R.string.language_id),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun byTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** The language [locale] is in, if the app has it. Android still calls Indonesian "in". */
        fun of(locale: Locale): AppLanguage? = byTag(if (locale.language == "in") "id" else locale.language)
    }
}

/** The settings' file, and the Language and English Game Titles settings in it. */
internal const val PREFS = "app"
internal const val KEY_LANGUAGE = "language"
internal const val KEY_ENGLISH_TITLES = "englishTitles"

/** The language chosen in Settings, or null for Auto, which follows the watch. */
fun chosenLanguage(context: Context): AppLanguage? =
    AppLanguage.byTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null))

/** Whether games go by their English titles, whatever the language (a setting). */
fun englishTitles(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENGLISH_TITLES, false)

/** The language Auto shows: the first of the watch's languages the app has, else English. */
fun autoLanguage(): AppLanguage {
    val locales = Resources.getSystem().configuration.locales
    return (0 until locales.size()).firstNotNullOfOrNull { AppLanguage.of(locales[it]) } ?: AppLanguage.English
}

/**
 * [base] in the language chosen in Settings, for the app, its screens, the
 * tile and the complication to start from (`attachBaseContext`). On Auto it's
 * [base] as it is, which follows the watch's languages.
 */
fun withAppLanguage(base: Context): Context {
    val language = chosenLanguage(base) ?: return base
    val config = Configuration(base.resources.configuration)
    config.setLocales(LocaleList(language.locale))
    return base.createConfigurationContext(config)
}

/** [context]'s resources in English, for the English Game Titles setting. */
fun englishResources(context: Context): Resources {
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(Locale.ENGLISH))
    return context.createConfigurationContext(config).resources
}

/** Whether games go by their English titles (the setting), for what's on screen. */
val LocalEnglishTitles = compositionLocalOf { false }

/** [game]'s title, as the screens show it. */
@Composable
fun titleOf(game: Game<*>): String = titleResources().getString(game.title)

/** The resources titles come from: the app's, or English ones with English Game Titles on. */
@Composable
fun titleResources(): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val english = LocalEnglishTitles.current
    return remember(context, configuration, english) { if (english) englishResources(context) else context.resources }
}

/** [game]'s title, for the tile and complication, which read the setting themselves. */
fun titleOf(game: Game<*>, context: Context): String =
    (if (englishTitles(context)) englishResources(context) else context.resources).getString(game.title)
