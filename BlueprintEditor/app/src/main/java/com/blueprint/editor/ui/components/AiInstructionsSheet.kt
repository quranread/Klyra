package com.blueprint.editor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.MeasurementFrame
import com.blueprint.editor.export.buildAiInstructions
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.TextMuted

/**
 * Popup for the "AI Instructions" tab — shows the plain-text handoff block
 * built by `buildAiInstructions()` with a "Copy All" button, matching the
 * web version's `#tabInstructions` + `copyInstrBtn`. Builds the text itself
 * (rather than receiving a pre-built string) so the Part-D "HTML conversion"
 * switch below can regenerate it live without the caller needing to know
 * about that toggle at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInstructionsSheet(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    measurementFrame: MeasurementFrame,
    elements: List<BlueprintElement>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    var justCopied by remember { mutableStateOf(false) }
    var isHtmlConversion by remember { mutableStateOf(false) }

    val text = remember(filename, naturalW, naturalH, measurementFrame, elements, isHtmlConversion) {
        buildAiInstructions(filename, naturalW, naturalH, measurementFrame, elements, isHtmlConversion)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = com.blueprint.editor.ui.theme.BgPanel2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("AI Instructions", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Instructions", text))
                    justCopied = true
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (justCopied) "Copied" else "Copy All")
                }
            }

            Spacer(Modifier.padding(top = 4.dp))

            // Part D: HTML is usually relative/flow-layout, which is exactly
            // where a coding AI is most likely to quietly re-derive its own
            // positioning instead of trusting these exact pixels. Flip this
            // on whenever the conversion involves HTML on either end (HTML
            // being turned into Kotlin/native code, or vice versa) to append
            // an extra, more forceful fixed-canvas + absolute-positioning
            // paragraph built specifically for that failure mode.
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HTML conversion", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Add extra warnings against % / flexbox / grid layout",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = isHtmlConversion,
                    onCheckedChange = { isHtmlConversion = it; justCopied = false },
                    colors = SwitchDefaults.colors(checkedTrackColor = Amber)
                )
            }

            Spacer(Modifier.padding(top = 4.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
