package com.updownupdown.cards.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import com.updownupdown.cards.watch.data.ColourTheme
import com.updownupdown.cards.watch.ui.AppColors.CardFace

/**
 * Every color in the app. The launcher icon can't read these: its
 * background is in res/values/colors.xml and its spade in
 * res/drawable/ic_launcher_foreground.xml.
 */
object AppColors {
    /** A pale yellow for games: buttons where the game's own colour isn't to hand, and hints. The app's own accent is [LocalAccent]. */
    val PaleYellow = Color(0xFFFFE27A)

    /** List buttons, card faces and the text on game buttons. */
    val DeepBlue = Color(0xFF262737)
    val DeepBlueBorder = Color(0xFF1F1F28)

    /** Buttons on lists and menus. */
    val ListButton = DeepBlue
    val ListButtonText = Color.White

    /** Buttons inside a game, where the game's own pale color isn't to hand. */
    val GameButton = PaleYellow
    val GameButtonText = DeepBlue
    // A disabled game button is its color at 40%.

    val Icon = Color(0xFFE6E4EC)
    val Ambient = Color(0xFF8A8A8A)

    /** Status lines, counts and other quiet text. */
    val TextMuted = Color(0xFFC9C6D3)

    // Cards.
    val CardFace = DeepBlue
    val CardBack = Color(0xFF656573)

    /** The Solitarius logo on a card's back, and the surrounding line. */
    val CardBackLogo = DeepBlue
    val CardBackBorder = DeepBlueBorder

    /**
     * Shades a card that isn't on top (under a pile's top card, or behind
     * the last of a spread): 40% black, which turns a [CardFace] into #171721.
     */
    val CardUnderShade = Color(0x00000000)

    /** An empty pile or space: darker than a card, but showing against the black screen. */
    val CardEmptySlot = Color(0xFF12121A)

    /** The thin dark edge around every card, so overlapping cards stand apart. */
    val CardEdge = Color(0xFF000000)

    /** The rings that glow from Ouroboros's counts as a jewel or a trinket is collected. */
    val JewelGlow = Color(0xFFFF4D5E)
    val TrinketGlow = Color(0xFFFFC94A)

    /** A pale grey box behind a group, such as a game's ratings and stats. */
    val Panel = Color(0x24FFFFFF)

    /** The unfilled part of a progress bar. */
    val Track = Color(0xFF3A3A44)

    /** A mark in an empty slot, such as Ebb & Flow's arrows. */
    val SlotMark = Color(0xFF7C7C88)

    // A color for each suit.
    val Hearts = Color(0xFFFF677B)
    val Spades = Color(0xFF9EE3FF)
    val Diamonds = Color(0xFFFFAA7C)
    val Clubs = Color(0xFFA2E3A2)

    /** The two suit colours, when cards show only red and black (as white). */
    val TwoColourRed = Color(0xFFFF677B)
    val TwoColourBlack = Color(0xFFFFFFFF)

    /** Jailbreak's Joker, in prison orange. */
    val Joker = Color(0xFFFF8A2A)

    // A selected card's border (centred on its edge) and 30% tint, and status tints over a card
    // (also 30%; a hint, such as a valid move, is [PaleYellow]).
    val Neutral = Color(0xFF7BD5FF)
    val Good = Color(0xFF78EBB1)
    val Bad = Color(0xFFFF5269)
    val Grey = Color(0xFF808080)

    // Buttons whose color says what they do (Rogue Run: heal, equip, weapon,
    // fist, run; Lycan's captures), each light enough for the dark text on it.
    val ButtonGreen = Color(0xFF8AFCC2)
    val ButtonBlue = Color(0xFF8CCDFF)
    val ButtonPurple = Color(0xFFD68FFF)
    val ButtonRed = Color(0xFFFF5C6E)
    val ButtonOrange = Color(0xFFFF984D)
    val ButtonPink = Color(0xFFFF78C0)

    // Text labels standing for red cards, black cards and either.
    val LabelRed = Color(0xFFEF5350)
    val LabelBlack = Color(0xFFCFD3D8)
    val LabelPlain = Color(0xFFE8EAED)
}

/** The app's font, for all text. One variable font file, at the weights the app uses. */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    listOf(
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold
    ).map {
        Font(
            R.font.outfit,
            it,
            variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))
        )
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

/**
 * The app's own accent, from the colour theme ([ColourTheme.accent]): the
 * logo, screen titles' icons, the stats' totals, and Material's primary
 * (switches and choices). Games keep their own colours.
 */
val LocalAccent = compositionLocalOf { ColourTheme.Nocturne.accent }

@Composable
fun AppTheme(accent: Color = ColourTheme.Nocturne.accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent) { Themed(accent, content) }
}

@Composable
private fun Themed(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = accent,
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
