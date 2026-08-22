package com.blueprint.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgPanel2
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.Danger
import com.blueprint.editor.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementsListSheet(
    elements: List<BlueprintElement>,
    selectedId: String?,
    naturalW: Int,
    naturalH: Int,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Text(
                text = "Mapped elements" + if (elements.isNotEmpty()) " (${elements.size})" else "",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (elements.isEmpty()) {
                Text(
                    text = "No dots or lines placed yet. Tap the image to mark a point, or switch to Line mode to draw a measurement line.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(elements, key = { it.id }) { el ->
                        ElementRow(
                            element = el,
                            naturalW = naturalW,
                            naturalH = naturalH,
                            selected = el.id == selectedId,
                            onClick = { onSelect(el.id) },
                            onDelete = { onDelete(el.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ElementRow(
    element: BlueprintElement,
    naturalW: Int,
    naturalH: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (selected) Cyan else Amber
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else BgPanel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = element.id,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accent,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            when (element) {
                is BlueprintElement.Line -> {
                    Text("Line", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "(${element.x1},${element.y1}) → (${element.x2},${element.y2})  len:${element.lengthPx()}px",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                is BlueprintElement.Dot -> {
                    val box = element.boxMetrics(naturalW, naturalH)
                    Text(element.type.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    val sizeSuffix = if (element.width > 0 || element.height > 0) "  ${element.width}×${element.height}" else ""
                    val centerSuffix = if (element.isSized) "  center:${box.centerX},${box.centerY}" else ""
                    Text(
                        "L:${box.left} R:${box.right} T:${box.top} B:${box.bottom}$sizeSuffix$centerSuffix",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete ${element.id}", tint = Danger)
        }
    }
}
