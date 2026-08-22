package com.blueprint.editor.export

import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.angleDeg
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx
import java.time.Instant

/**
 * Builds the exportable JSON document for the current session — same field
 * set and rounding rules as the original `exportBtn` click handler.
 */
fun buildBlueprintJson(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    elements: List<BlueprintElement>
): BlueprintJson {
    val aspectRatio = if (naturalH != 0) {
        (Math.round((naturalW.toDouble() / naturalH) * 10000.0) / 10000.0)
    } else 0.0

    return BlueprintJson(
        image = ImageInfoJson(
            filename = filename,
            originalWidth = naturalW,
            originalHeight = naturalH,
            aspectRatio = aspectRatio
        ),
        createdAt = Instant.now().toString(),
        elements = elements.map { it.toElementJson(naturalW, naturalH) }
    )
}

private fun BlueprintElement.toElementJson(naturalW: Int, naturalH: Int): ElementJson =
    when (this) {
        is BlueprintElement.Line -> ElementJson(
            id = id,
            kind = "line",
            start = PointJson(x1, y1),
            end = PointJson(x2, y2),
            lengthPx = lengthPx(),
            angleDeg = angleDeg(),
            notes = notes.ifBlank { null }
        )
        is BlueprintElement.Dot -> {
            val box = boxMetrics(naturalW, naturalH)
            ElementJson(
                id = id,
                kind = "dot",
                type = type.wireValue,
                anchor = anchor.wireValue,
                dotX = x,
                dotY = y,
                width = width,
                height = height,
                centerX = if (isSized) box.centerX else null,
                centerY = if (isSized) box.centerY else null,
                distanceFromLeftEdge = box.left,
                distanceFromRightEdge = box.right,
                distanceFromTopEdge = box.top,
                distanceFromBottomEdge = box.bottom,
                notes = notes.ifBlank { null }
            )
        }
    }
