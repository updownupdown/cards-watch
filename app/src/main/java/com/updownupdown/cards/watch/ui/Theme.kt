package com.updownupdown.cards.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.updownupdown.cards.watch.R

object AppColors {
    /** The theme colour: the Solitarius logo's yellow, also used for text accents. */
    val PaleYellow = Color(0xFFFFCF87)
    /** Buttons on lists and menus. */
    val DeepBlue = Color(0xFF191A24)
    /** Buttons inside a game, with [DeepBlue] text. */
    val GameButton = Color(0xFF868295)
    /** [GameButton] at 40% over black, for disabled buttons. */
    val GameButtonDisabled = Color(0xFF363440)
    val Icon = Color(0xFFE6E4EC)
    val Ambient = Color(0xFF8A8A8A)
}

val Outfit = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium))

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
            onSurfaceVariant = Color(0xFFC9C6D3),
        ),
        content = content,
    )
}
