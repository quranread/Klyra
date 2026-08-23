package com.blueprint.editor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.blueprint.editor.ui.theme.TextMuted
import java.util.Locale

/** Popup for the "Image Info" tab — matches the web version's `#tabInfo` / `renderInfo()`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageInfoSheet(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    zoomPercent: Int,
    elementCount: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = com.blueprint.editor.ui.theme.BgPanel2) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Image Info", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            val aspect = if (naturalH != 0) naturalW.toDouble() / naturalH else 0.0
            val lines = listOf(
                "filename: ${filename.ifBlank { "—" }}",
                "original_width: ${naturalW}px",
                "original_height: ${naturalH}px",
                "aspect_ratio: ${String.format(Locale.US, "%.4f", aspect)}",
                "current_zoom: $zoomPercent%",
                "elements_mapped: $elementCount",
                "lock_status: locked (read-only)"
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
