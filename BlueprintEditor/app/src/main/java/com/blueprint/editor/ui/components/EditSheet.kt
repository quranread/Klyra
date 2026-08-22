package com.blueprint.editor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.Anchor
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.ElementType
import com.blueprint.editor.data.angleDeg
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.Danger
import com.blueprint.editor.ui.theme.TextMuted
import kotlin.math.abs

/**
 * The edit sheet for whichever element is currently selected. Every field
 * commits to [onUpdate] immediately as it changes — matching the original,
 * where width/height/anchor already live-update the canvas via
 * `liveUpdateDots()`, and there's no "cancel without saving" path (every
 * close button calls `closeSheet(true)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    element: BlueprintElement,
    naturalW: Int,
    naturalH: Int,
    onUpdate: (BlueprintElement) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(element.id, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Amber)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Danger)
                }
            }

            when (element) {
                is BlueprintElement.Dot -> DotFields(element, naturalW, naturalH, onUpdate)
                is BlueprintElement.Line -> LineFields(element, onUpdate)
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun DotFields(
    el: BlueprintElement.Dot,
    naturalW: Int,
    naturalH: Int,
    onUpdate: (BlueprintElement) -> Unit
) {
    var widthText by remember(el.id) { mutableStateOf(if (el.width > 0) el.width.toString() else "") }
    var heightText by remember(el.id) { mutableStateOf(if (el.height > 0) el.height.toString() else "") }
    var notes by remember(el.id) { mutableStateOf(el.notes) }
    var nudgeStep by remember { mutableIntStateOf(1) }

    ReadonlyRow("X (original px)", el.x.toString())
    ReadonlyRow("Y (original px)", el.y.toString())

    NudgePad(
        step = nudgeStep,
        onStepChange = { nudgeStep = it },
        onNudge = { dx, dy ->
            val newX = (el.x + dx).coerceIn(0, naturalW)
            val newY = (el.y + dy).coerceIn(0, naturalH)
            onUpdate(el.copy(x = newX, y = newY))
        }
    )

    LabeledDropdown(
        label = "Type",
        selected = el.type,
        options = ElementType.entries,
        optionLabel = { it.label },
        onSelect = { onUpdate(el.copy(type = it)) }
    )

    LabeledDropdown(
        label = "Anchor point",
        selected = el.anchor,
        options = Anchor.entries,
        optionLabel = { it.label },
        onSelect = { newAnchor -> onUpdate(el.copy(anchor = newAnchor)) }
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SteppedNumberField(
            label = "Width (px)",
            valueText = widthText,
            step = nudgeStep,
            onValueChange = { text ->
                widthText = text
                onUpdate(el.copy(width = text.toIntOrNull() ?: 0))
            },
            onStep = { delta ->
                val newVal = ((widthText.toIntOrNull() ?: 0) + delta).coerceAtLeast(0)
                widthText = newVal.toString()
                onUpdate(el.copy(width = newVal))
            },
            modifier = Modifier.weight(1f)
        )
        SteppedNumberField(
            label = "Height (px)",
            valueText = heightText,
            step = nudgeStep,
            onValueChange = { text ->
                heightText = text
                onUpdate(el.copy(height = text.toIntOrNull() ?: 0))
            },
            onStep = { delta ->
                val newVal = ((heightText.toIntOrNull() ?: 0) + delta).coerceAtLeast(0)
                heightText = newVal.toString()
                onUpdate(el.copy(height = newVal))
            },
            modifier = Modifier.weight(1f)
        )
    }

    // Live edge distances — recomputed from the *current* el, which already
    // reflects the latest width/height/anchor by the time this recomposes.
    val box = el.boxMetrics(naturalW, naturalH)
    val edgesText = buildString {
        append("Left: ${box.left}px   Right: ${box.right}px\n")
        append("Top: ${box.top}px   Bottom: ${box.bottom}px")
        if (box.w > 0 && box.h > 0) {
            append("\nCenter: X ${box.centerX}px, Y ${box.centerY}px")
        }
    }
    ReadonlyBlock("Edges (from image bounds)", edgesText)

    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it; onUpdate(el.copy(notes = it)) },
        label = { Text("Notes") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Fine-tune position controls (didn't land exactly right? nudge it here) —
 * a 4-directional pad that moves the dot's X/Y by the selected step size.
 * Direct port of the web version's `#nudgeFields` / `nudgePosition()`, added
 * after discovering that even the magnifier can leave a couple of px of
 * drift on very small elements — this lets that be corrected pixel-exactly
 * instead of re-tapping and hoping for better luck.
 */
@Composable
private fun NudgePad(step: Int, onStepChange: (Int) -> Unit, onNudge: (dx: Int, dy: Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Fine-tune position (didn't land exactly right? nudge it here)",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 5, 10).forEach { s ->
                    StepChip(label = "${s}px", selected = step == s, onClick = { onStepChange(s) })
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NudgeButton("↑") { onNudge(0, -step) }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    NudgeButton("←") { onNudge(-step, 0) }
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text("✛", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    NudgeButton("→") { onNudge(step, 0) }
                }
                NudgeButton("↓") { onNudge(0, step) }
            }
        }
    }
}

@Composable
private fun StepChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Amber else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (selected) androidx.compose.ui.graphics.Color(0xFF1A1305) else TextMuted
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(30.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NudgeButton(symbol: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(40.dp)
    ) {
        Text(symbol, color = Cyan, style = MaterialTheme.typography.titleMedium)
    }
}

/** A numeric text field with −/+ stepper buttons on either side, stepping by [step]. */
@Composable
private fun SteppedNumberField(
    label: String,
    valueText: String,
    step: Int,
    onValueChange: (String) -> Unit,
    onStep: (delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onStep(-step) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { onStep(step) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LineFields(el: BlueprintElement.Line, onUpdate: (BlueprintElement) -> Unit) {
    var notes by remember(el.id) { mutableStateOf(el.notes) }

    ReadonlyRow("Start X (original px)", el.x1.toString())
    ReadonlyRow("Start Y (original px)", el.y1.toString())
    ReadonlyRow("End X (original px)", el.x2.toString())
    ReadonlyRow("End Y (original px)", el.y2.toString())

    val dx = el.x2 - el.x1
    val dy = el.y2 - el.y1
    val direction = when {
        abs(dy) < 4 -> "Horizontal"
        abs(dx) < 4 -> "Vertical"
        else -> "Diagonal"
    }
    ReadonlyBlock("Line info", "Length: ${el.lengthPx()}px\nAngle: ${el.angleDeg()}°  ($direction)")

    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it; onUpdate(el.copy(notes = it)) },
        label = { Text("Notes") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReadonlyRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadonlyBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
