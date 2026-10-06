package com.updownupdown.cards.watch.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** A button on a list or menu, full width unless [modifier] sizes it. */
@Composable
fun ListButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ListButton, contentColor = AppColors.ListButtonText),
        label = {
            // The icon and label together in the middle.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(end = 2.dp).size(20.dp))
                Text(label, maxLines = 1)
            }
        },
        modifier = modifier,
    )
}

/** The space after a list's last item, to bring its foot up clear of the round screen's. */
internal val LIST_END = 40.dp

/** The space between a section's heading and what follows, on top of the list's own. */
private val HEADING_GAP = 5.dp

/** A screen's title at the top of its list: its icon over its name. */
@Composable
internal fun ScreenTitle(@DrawableRes icon: Int, title: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), contentDescription = null, tint = AppColors.PaleYellow, modifier = Modifier.size(30.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/** A section's heading in a list, small and muted, in the middle. */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HEADING_GAP),
    )
}

/** Shrinks and fades a list item near the round screen's top and bottom. */
fun TransformingLazyColumnItemScope.transformed(spec: TransformationSpec): Modifier {
    val item = this
    return Modifier.transformedHeight(item, spec).graphicsLayer {
        with(item) { with(spec) { applyContentTransformation(scrollProgress) } }
    }
}
