package com.blueprint.editor.export

import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.MeasurementFrame
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
    measurementFrame: MeasurementFrame,
    elements: List<BlueprintElement>
): BlueprintJson {
    val aspectRatio = if (naturalH != 0) {
        (Math.round((naturalW.toDouble() / naturalH) * 10000.0) / 10000.0)
    } else 0.0

    val usesActiveArea = measurementFrame.originX != 0 || measurementFrame.originY != 0 ||
        measurementFrame.width != naturalW || measurementFrame.height != naturalH
    val measurementReference = if (usesActiveArea) {
        "All left/right/top/bottom distances below are measured from a marked Active Area " +
            "(${measurementFrame.width}x${measurementFrame.height}px, starting ${measurementFrame.originX}px " +
            "from the full image's left edge and ${measurementFrame.originY}px from its top edge) — " +
            "NOT from the full image's own edges. The image itself was not cropped; every element's " +
            "raw position is still stored relative to the full original image."
    } else {
        "All left/right/top/bottom distances below are measured from the full image's own edges."
    }

    return BlueprintJson(
        image = ImageInfoJson(
            filename = filename,
            originalWidth = naturalW,
            originalHeight = naturalH,
            aspectRatio = aspectRatio
        ),
        createdAt = Instant.now().toString(),
        measurementReference = measurementReference,
        elements = elements.map { it.toElementJson(measurementFrame) }
    )
}

private fun BlueprintElement.toElementJson(frame: MeasurementFrame): ElementJson =
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
            val box = boxMetrics(frame)
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
