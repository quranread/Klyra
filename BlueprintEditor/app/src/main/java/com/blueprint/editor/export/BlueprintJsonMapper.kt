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

private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

private fun BlueprintElement.toElementJson(frame: MeasurementFrame): ElementJson =
    when (this) {
        is BlueprintElement.Line -> {
            val rx1 = x1 - frame.originX; val ry1 = y1 - frame.originY
            val rx2 = x2 - frame.originX; val ry2 = y2 - frame.originY
            ElementJson(
                id = id,
                kind = "line",
                start = PointJson(x1, y1),
                end = PointJson(x2, y2),
                lengthPx = lengthPx(),
                angleDeg = angleDeg(),
                startXPercent = round1(frame.percentOfWidth(rx1)),
                startYPercent = round1(frame.percentOfHeight(ry1)),
                endXPercent = round1(frame.percentOfWidth(rx2)),
                endYPercent = round1(frame.percentOfHeight(ry2)),
                notes = notes.ifBlank { null }
            )
        }
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
                distanceFromLeftEdgePercent = round1(frame.percentOfWidth(box.left)),
                distanceFromRightEdgePercent = round1(frame.percentOfWidth(box.right)),
                distanceFromTopEdgePercent = round1(frame.percentOfHeight(box.top)),
                distanceFromBottomEdgePercent = round1(frame.percentOfHeight(box.bottom)),
                centerXPercent = if (isSized) round1(frame.percentOfWidth(box.centerX)) else null,
                centerYPercent = if (isSized) round1(frame.percentOfHeight(box.centerY)) else null,
                widthPercent = if (width > 0) round1(frame.percentOfWidth(width)) else null,
                heightPercent = if (height > 0) round1(frame.percentOfHeight(height)) else null,
                notes = notes.ifBlank { null }
            )
        }
    }
