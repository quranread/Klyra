package com.blueprint.editor.export

import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.angleDeg
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx

/**
 * Builds the plain-text, copy-paste-ready instructions block for handing
 * this blueprint to an AI — same format/wording as the web version's
 * "AI Instructions" tab (`renderInstructions()`). Meant to be pasted into a
 * chat alongside the annotated PNG: the image gives visual confirmation,
 * this text gives exact numbers, and the header line explicitly tells the
 * AI not to override them with its own judgement.
 */
fun buildAiInstructions(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    elements: List<BlueprintElement>
): String {
    if (elements.isEmpty()) return "No elements mapped yet."

    val header = buildString {
        append("Reference image: ${filename.ifBlank { "attached screenshot" }} (${naturalW}×${naturalH}px)\n")
        append(
            "All measurements below are EXACT pixel distances from the image edges, " +
                "based on the original resolution. Do not reposition these elements based " +
                "on your own judgement — use these exact values."
        )
    }

    val blocks = elements.map { el ->
        when (el) {
            is BlueprintElement.Line -> buildString {
                append("${el.id} — Line\n")
                append("From: (${el.x1}, ${el.y1})  To: (${el.x2}, ${el.y2})\n")
                append("Length: ${el.lengthPx()}px, Angle: ${el.angleDeg()}°")
                if (el.notes.isNotBlank()) append("\nNotes: ${el.notes}")
            }
            is BlueprintElement.Dot -> buildString {
                val box = el.boxMetrics(naturalW, naturalH)
                append("${el.id} — ${el.type.wireValue}\n")
                append("Left: ${box.left}px | Right: ${box.right}px | Top: ${box.top}px | Bottom: ${box.bottom}px")
                if (el.width > 0 || el.height > 0) append("\nSize: ${el.width} × ${el.height}px")
                if (el.isSized) append("\nCenter: X ${box.centerX}px, Y ${box.centerY}px")
                if (el.notes.isNotBlank()) append("\nNotes: ${el.notes}")
            }
        }
    }

    return (listOf(header) + blocks).joinToString("\n\n")
}
