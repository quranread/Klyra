package com.blueprint.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.DrawMode
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgPanel

private val MODE_HINTS = mapOf(
    DrawMode.DOT to "Dot mode — tap image to place",
    DrawMode.LINE to "Line mode — tap start, then tap end",
    DrawMode.BOX to "Box mode — tap one corner, then the opposite corner",
    DrawMode.PAN to "Pan mode — drag with one finger to move around"
)

@Composable
fun ModeHintBar(
    drawMode: DrawMode,
    hasPendingLineStart: Boolean,
    hasPendingBoxStart: Boolean,
    modifier: Modifier = Modifier
) {
    val text = when {
        hasPendingLineStart -> "\uD83D\uDCCF Start point set — tap END point now (or tap \u21A9 Undo to cancel)"
        hasPendingBoxStart -> "\u25AD Corner set — tap the OPPOSITE corner now (or tap \u21A9 Undo to cancel)"
        else -> MODE_HINTS[drawMode].orEmpty()
    }
    Text(
        text = text,
        color = Amber,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
