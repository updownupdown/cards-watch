package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.data.AppLanguage
import com.updownupdown.cards.watch.data.CONTINUE_CHOICES
import com.updownupdown.cards.watch.data.ColourTheme
import com.updownupdown.cards.watch.data.SuitColours
import com.updownupdown.cards.watch.data.autoLanguage

/** A setting's switch or choice's width, × the list's; it sits in the middle. */
private const val SETTING_WIDTH = 0.8f

/** The space over each setting's heading after the first. */
private val SETTING_GAP = 18.dp

/**
 * The settings, from the home screen's Settings button: Haptics, Suit
 * Colours, how many games Continue Playing shows, the Language (Auto, which
 * follows the watch, or, with Auto off, one of the app's), English Game
 * Titles and the Colour Theme.
 */
@Composable
fun SettingsScreen(
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    suitColours: SuitColours = SuitColours.Auto,
    onSuitColours: (SuitColours) -> Unit = {},
    continueShown: Int = 3,
    onContinueShown: (Int) -> Unit = {},
    language: AppLanguage? = null,
    onLanguage: (AppLanguage?) -> Unit = {},
    englishTitles: Boolean = false,
    onEnglishTitles: (Boolean) -> Unit = {},
    colourTheme: ColourTheme = ColourTheme.Nocturne,
    onColourTheme: (ColourTheme) -> Unit = {},
    /** The item the list starts at, for screenshots of its foot. */
    startAt: Int = 0,
) {
    val listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = startAt)
    val spec = rememberTransformationSpec()
    val systemAllows = systemHapticsOn(LocalContext.current)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ScreenTitle(R.drawable.ic_settings, stringResource(R.string.settings), transformed(spec)) }
            // Off when the watch's own settings have haptics off.
            item {
                SwitchButton(
                    checked = haptics && systemAllows,
                    onCheckedChange = onHaptics,
                    enabled = systemAllows,
                    label = { Text(stringResource(R.string.haptics)) },
                    secondaryLabel = if (systemAllows) null else ({ Text(stringResource(R.string.haptics_off)) }),
                    modifier = Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item { SectionHeading(stringResource(R.string.suit_colours), transformed(spec).padding(top = SETTING_GAP)) }
            items(SuitColours.entries, key = { "suits:${it.name}" }) { choice ->
                SettingChoice(
                    stringResource(choice.label),
                    choice == suitColours,
                    { onSuitColours(choice) },
                    Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    SurfaceTransformation(spec)
                )
            }
            item { SectionHeading(stringResource(R.string.continue_playing), transformed(spec).padding(top = SETTING_GAP)) }
            items(CONTINUE_CHOICES, key = { "continue:$it" }) { count ->
                SettingChoice(
                    if (count == 0) stringResource(R.string.hide) else "$count",
                    count == continueShown,
                    { onContinueShown(count) },
                    Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    SurfaceTransformation(spec)
                )
            }
            item { SectionHeading(stringResource(R.string.language), transformed(spec).padding(top = SETTING_GAP)) }
            // Auto, naming the language it picks; turned off, each language by its own name.
            item(key = "language:auto") {
                SwitchButton(
                    checked = language == null,
                    onCheckedChange = { auto -> onLanguage(if (auto) null else autoLanguage()) },
                    label = { Text(stringResource(R.string.language_auto, stringResource(autoLanguage().nativeName))) },
                    modifier = Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            if (language != null) {
                items(AppLanguage.entries, key = { "language:${it.tag}" }) { choice ->
                    SettingChoice(
                        stringResource(choice.nativeName),
                        choice == language,
                        { onLanguage(choice) },
                        Modifier
                            .fillMaxWidth(SETTING_WIDTH)
                            .transformedHeight(this, spec),
                        SurfaceTransformation(spec)
                    )
                }
            }
            item { Spacer(Modifier.height(SETTING_GAP)) }
            item {
                SwitchButton(
                    checked = englishTitles,
                    onCheckedChange = onEnglishTitles,
                    label = { Text(stringResource(R.string.english_titles)) },
                    modifier = Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            // The colour themes, three to a row, then the chosen one's name.
            item { SectionHeading(stringResource(R.string.colour_theme), transformed(spec).padding(top = SETTING_GAP)) }
            items(ColourTheme.entries.chunked(THEMES_PER_ROW), key = { "themes:${it.first().name}" }) { row ->
                Row(transformed(spec), horizontalArrangement = Arrangement.spacedBy(THEME_GAP)) {
                    row.forEach { theme -> ThemeChoice(theme, theme == colourTheme) { onColourTheme(theme) } }
                }
            }
            item {
                Text(
                    stringResource(colourTheme.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = transformed(spec).padding(top = THEME_GAP),
                )
            }
            // Room to bring the foot of the list up clear of the round screen's.
            item { Spacer(Modifier.height(LIST_END)) }
        }
    }
}

/** A colour theme's button: the spade in its colour on a round button, ringed when it's the one chosen. */
@Composable
private fun ThemeChoice(theme: ColourTheme, selected: Boolean, onSelect: () -> Unit) {
    val name = stringResource(theme.label)
    Box(
        Modifier
            .size(THEME_BUTTON + THEME_RING * 2)
            .then(if (selected) Modifier.border(THEME_RING / 2, theme.accent, CircleShape) else Modifier)
            .padding(THEME_RING)
            .clip(CircleShape)
            .background(AppColors.ListButton)
            .selectable(selected, onClick = onSelect, role = Role.RadioButton)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.logo_spade),
            contentDescription = null,
            colorFilter = ColorFilter.tint(theme.accent),
            modifier = Modifier.size(THEME_SPADE),
        )
    }
}

/** The colour themes' buttons: how many to a row, each one's size, the ring round the chosen one, the gap between, and the spade's drawable in each. */
private const val THEMES_PER_ROW = 3
private val THEME_BUTTON = 34.dp
private val THEME_RING = 3.dp
private val THEME_GAP = 3.dp
private val THEME_SPADE = 23.dp

/** A choice among a setting's options, as a radio button. */
@Composable
private fun SettingChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    RadioButton(
        selected = selected,
        onSelect = onSelect,
        label = { Text(label) },
        modifier = modifier,
        transformation = transformation,
    )
}

@WearGamePreview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black)
        ) {
            AppScaffold { SettingsScreen(haptics = true, onHaptics = {}) }
        }
    }
}
