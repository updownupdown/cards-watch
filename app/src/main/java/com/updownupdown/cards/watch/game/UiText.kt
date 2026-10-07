package com.updownupdown.cards.watch.game

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.Collator
import java.util.Locale

/**
 * Text from the app's string resources, with its arguments, so the rules (pure
 * functions, with no Context) can say what to show and it shows in the
 * watch's language. An argument that is itself a [UiText] is looked up too.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A count's wording, with the count as its first argument unless [args] are given. */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
}

fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

fun UiText.resolve(resources: Resources): String {
    fun args(list: List<Any>) = list.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
    return when (this) {
        is UiText.Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args(args))
        is UiText.Plural -> resources.getQuantityString(id, count, *args(args.ifEmpty { listOf(count) }))
    }
}

@Composable
fun UiText.resolve(): String {
    // Read so a change of language looks the text up again.
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

/** The watch's language, for casing and sorting. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** In capitals, by the watch's language's rules (Turkish dotted İ). */
@Composable
fun String.upper(): String = uppercase(currentLocale())

/** [games] in order of their titles, as the watch's language sorts them. */
@Composable
fun <G : Game<*>> sortedByTitle(games: List<G>): List<G> {
    val resources = LocalContext.current.resources
    val collator = Collator.getInstance(currentLocale())
    return games.sortedWith(compareBy(collator) { resources.getString(it.title) })
}
