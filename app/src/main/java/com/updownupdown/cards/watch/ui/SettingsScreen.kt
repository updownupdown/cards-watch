package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.updownupdown.cards.watch.R
import com.updownupdown.cards.watch.data.CONTINUE_CHOICES
import com.updownupdown.cards.watch.data.SuitColours

/** A setting's switch or choice's width, × the list's; it sits in the middle. */
private const val SETTING_WIDTH = 0.8f

/** The space over each setting's heading after the first. */
private val SETTING_GAP = 18.dp

/**
 * The settings, from the home screen's Settings button: Haptics, Suit
 * Colours and how many games Continue Playing shows.
 */
@Composable
fun SettingsScreen(
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    suitColours: SuitColours = SuitColours.Auto,
    onSuitColours: (SuitColours) -> Unit = {},
    continueShown: Int = 3,
    onContinueShown: (Int) -> Unit = {},
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val systemAllows = systemHapticsOn(LocalContext.current)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ScreenTitle(R.drawable.ic_settings, "Settings", transformed(spec)) }
            // Off when the watch's own settings have haptics off.
            item {
                SwitchButton(
                    checked = haptics && systemAllows,
                    onCheckedChange = onHaptics,
                    enabled = systemAllows,
                    label = { Text("Haptics") },
                    secondaryLabel = if (systemAllows) null else ({ Text("Off in watch settings") }),
                    modifier = Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item { SectionHeading("Suit Colours", transformed(spec).padding(top = SETTING_GAP)) }
            items(SuitColours.entries, key = { "suits:${it.name}" }) { choice ->
                SettingChoice(
                    choice.label,
                    choice == suitColours,
                    { onSuitColours(choice) },
                    Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    SurfaceTransformation(spec)
                )
            }
            item { SectionHeading("Continue Playing", transformed(spec).padding(top = SETTING_GAP)) }
            items(CONTINUE_CHOICES, key = { "continue:$it" }) { count ->
                SettingChoice(
                    if (count == 0) "Hide" else "$count",
                    count == continueShown,
                    { onContinueShown(count) },
                    Modifier
                        .fillMaxWidth(SETTING_WIDTH)
                        .transformedHeight(this, spec),
                    SurfaceTransformation(spec)
                )
            }
            // Room to bring the foot of the list up clear of the round screen's.
            item { Spacer(Modifier.height(LIST_END)) }
        }
    }
}

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
