package com.updownupdown.cards.watch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.updownupdown.cards.watch.game.Game

/** A game's rules as a scrolling list (the crown scrolls it too). */
@Composable
fun RulesScreen(game: Game<*>) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader(transformed(spec)) { Text(game.title) } }
            itemsIndexed(game.rules) { _, line ->
                when {
                    line.startsWith("## ") -> Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = transformed(spec).fillMaxWidth()
                            .padding(top = 8.dp, start = 14.dp, end = 14.dp),
                    )
                    else -> Text(
                        text = rich(line.replaceFirst(Regex("^- "), "• ")),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = transformed(spec).fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
            }
        }
    }
}

/** Makes **text** bold. */
private fun rich(line: String): AnnotatedString = buildAnnotatedString {
    line.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
