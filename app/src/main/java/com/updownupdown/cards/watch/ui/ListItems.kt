package com.updownupdown.cards.watch.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** Shrinks and fades a list item near the round screen's top and bottom. */
fun TransformingLazyColumnItemScope.transformed(spec: TransformationSpec): Modifier {
    val item = this
    return Modifier.transformedHeight(item, spec).graphicsLayer {
        with(item) { with(spec) { applyContentTransformation(scrollProgress) } }
    }
}
