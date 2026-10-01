package com.updownupdown.cards.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.updownupdown.cards.watch.R

/**
 * Every colour in the app. The launcher icon can't read these: its
 * background is in res/values/colors.xml and its spade in
 * res/drawable/ic_launcher_foreground.xml.
 */
object AppColors {
    /** The theme colour: the Solitarius logo's yellow, also used for text accents. */
    val PaleYellow = Color(0xFFFFCF87)
    /** List buttons, card faces and the text on game buttons. */
    val DeepBlue = Color(0xFF262737)

    /** Buttons on lists and menus. */
    val ListButton = DeepBlue
    val ListButtonText = Color.White
    /** Buttons inside a game. */
    val GameButton = PaleYellow
    val GameButtonText = DeepBlue
    // A disabled game button is its colour at 40%.

    val Icon = Color(0xFFE6E4EC)
    val Ambient = Color(0xFF8A8A8A)
    /** Status lines, counts and other quiet text. */
    val TextMuted = Color(0xFFC9C6D3)

    // Cards.
    val CardFace = DeepBlue
    val CardBack = Color(0xFF7A7C9F)
    /** The Solitarius logo on a card's back, and the line around it. */
    val CardBackLogo = DeepBlue
    val CardBackBorder = DeepBlue
    /**
     * Shades a card that isn't on top (under a pile's top card, or behind
     * the last of a spread): 40% black, which turns a [CardFace] into #171721.
     */
    val CardUnderShade = Color(0x66000000)
    /** An empty pile or space: darker than a card, but showing against the black screen. */
    val CardEmptySlot = Color(0xFF12121A)
    /** The thin dark edge around every card, so overlapping cards stand apart. */
    val CardEdge = Color(0xFF000000)

    // A colour for each suit.
    val Hearts = Color(0xFFE82D4D)
    val Spades = Color(0xFF9FBAC5)
    val Diamonds = Color(0xFFE82D9D)
    val Clubs = Color(0xFFC8E6B8)

    // A selected card's border (centred on its edge) and 30% tint, and status tints over a card
    // (also 30%; a hint, such as a valid move, is [PaleYellow]).
    val Neutral = Color(0xFF7BD5FF)
    val Good = Color(0xFF78EBB1)
    val Bad = Color(0xFFE0485C)
    /** With the three above, colours an action's button (Rogue Run: heal, equip, weapon, fist). */
    val Purple = Color(0xFFB79CFF)

    // Text labels standing for red cards, black cards and either.
    val LabelRed = Color(0xFFEF5350)
    val LabelBlack = Color(0xFFCFD3D8)
    val LabelPlain = Color(0xFFE8EAED)
}

/** The app's font, for all text. One variable font file, at the weights the app uses. */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(R.font.outfit, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

/** Wear's text styles, each in [Outfit]. */
private val AppTypography = Typography().run {
    copy(
        arcLarge = arcLarge.copy(fontFamily = Outfit),
        arcMedium = arcMedium.copy(fontFamily = Outfit),
        arcSmall = arcSmall.copy(fontFamily = Outfit),
        displayLarge = displayLarge.copy(fontFamily = Outfit),
        displayMedium = displayMedium.copy(fontFamily = Outfit),
        displaySmall = displaySmall.copy(fontFamily = Outfit),
        titleLarge = titleLarge.copy(fontFamily = Outfit),
        titleMedium = titleMedium.copy(fontFamily = Outfit),
        titleSmall = titleSmall.copy(fontFamily = Outfit),
        labelLarge = labelLarge.copy(fontFamily = Outfit),
        labelMedium = labelMedium.copy(fontFamily = Outfit),
        labelSmall = labelSmall.copy(fontFamily = Outfit),
        bodyLarge = bodyLarge.copy(fontFamily = Outfit),
        bodyMedium = bodyMedium.copy(fontFamily = Outfit),
        bodySmall = bodySmall.copy(fontFamily = Outfit),
        bodyExtraSmall = bodyExtraSmall.copy(fontFamily = Outfit),
        numeralExtraLarge = numeralExtraLarge.copy(fontFamily = Outfit),
        numeralLarge = numeralLarge.copy(fontFamily = Outfit),
        numeralMedium = numeralMedium.copy(fontFamily = Outfit),
        numeralSmall = numeralSmall.copy(fontFamily = Outfit),
        numeralExtraSmall = numeralExtraSmall.copy(fontFamily = Outfit),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = AppColors.PaleYellow,
            onPrimary = AppColors.DeepBlue,
            primaryContainer = AppColors.DeepBlue,
            onPrimaryContainer = Color.White,
            surfaceContainer = AppColors.DeepBlue,
            surfaceContainerLow = AppColors.DeepBlue,
            surfaceContainerHigh = AppColors.DeepBlue,
            onSurface = Color.White,
            onSurfaceVariant = AppColors.TextMuted,
        ),
        typography = AppTypography,
        content = content,
    )
}
