package com.blueprint.editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.NaturalPoint
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx
import kotlin.math.max
import kotlin.math.roundToInt

private val AMBER = Color(0xFFFFB627)
private val CYAN = Color(0xFF4FD1C5)
private val DEEP = Color(0xFF0A1F33)

/**
 * Everything needed to paint the current frame of the canvas overlay —
 * gathered once per recomposition so [drawAnnotations] stays a pure function.
 */
data class AnnotationFrame(
    val elements: List<BlueprintElement>,
    val selectedId: String?,
    val naturalW: Int,
    val naturalH: Int,
    val scale: Float,
    val pendingLineStart: NaturalPoint?,
    val pendingBoxStart: NaturalPoint?,
    /** Live finger position while dragging to place, in natural px — drives the line/box preview. */
    val livePoint: NaturalPoint?
)

/** Screen-space bounds of the selected element's quick-delete badge, for hit-testing taps on it. */
fun quickDeleteBadgeCenter(frame: AnnotationFrame, pan: Offset = Offset.Zero): Offset? {
    val el = frame.elements.firstOrNull { it.id == frame.selectedId } ?: return null
    val s = frame.scale
    return when (el) {
        is BlueprintElement.Dot -> Offset(el.x * s, el.y * s)
        is BlueprintElement.Line -> Offset((el.x1 + el.x2) / 2f * s, (el.y1 + el.y2) / 2f * s)
    }.let { center -> Offset(center.x + 14f + pan.x, center.y - 14f + pan.y) }
}

fun DrawScope.drawAnnotations(frame: AnnotationFrame, textMeasurer: TextMeasurer) {
    val s = frame.scale

    frame.elements.forEach { el ->
        when (el) {
            is BlueprintElement.Line -> drawLineElement(el, s, selected = el.id == frame.selectedId, textMeasurer = textMeasurer)
            is BlueprintElement.Dot -> {
                drawDotElement(el, s, selected = el.id == frame.selectedId, textMeasurer = textMeasurer)
                if (el.isSized) {
                    val box = el.boxMetrics(frame.naturalW, frame.naturalH)
                    drawCenterMark(Offset(box.centerX * s, box.centerY * s))
                }
                if (el.id == frame.selectedId) {
                    drawDimensionLines(el, frame, textMeasurer)
                }
            }
        }
    }

    frame.pendingLineStart?.let { drawPendingMarker(Offset(it.x * s, it.y * s)) }
    frame.pendingBoxStart?.let { drawPendingMarker(Offset(it.x * s, it.y * s)) }

    // Live preview while dragging
    val live = frame.livePoint
    if (live != null) {
        frame.pendingLineStart?.let { start ->
            drawPreviewLine(Offset(start.x * s, start.y * s), Offset(live.x * s, live.y * s))
        }
        frame.pendingBoxStart?.let { start ->
            drawPreviewBox(Offset(start.x * s, start.y * s), Offset(live.x * s, live.y * s))
        }
    }

    // Quick-delete badge for the selected element
    quickDeleteBadgeCenter(frame)?.let { center -> drawQuickDeleteBadge(center) }
}

private fun DrawScope.drawDotElement(el: BlueprintElement.Dot, s: Float, selected: Boolean, textMeasurer: TextMeasurer) {
    val center = Offset(el.x * s, el.y * s)
    val ringColor = if (selected) CYAN else AMBER
    drawCircle(color = ringColor.copy(alpha = 0.18f), radius = 10f, center = center)
    drawCircle(color = ringColor, radius = 10f, center = center, style = Stroke(width = 2f))
    drawCircle(color = ringColor, radius = 2f, center = center)

    drawChipLabel(
        text = el.id,
        anchor = Offset(center.x + 22f, center.y - 8f),
        textMeasurer = textMeasurer,
        borderColor = if (selected) CYAN else null
    )
}

private fun DrawScope.drawLineElement(el: BlueprintElement.Line, s: Float, selected: Boolean, textMeasurer: TextMeasurer) {
    val p1 = Offset(el.x1 * s, el.y1 * s)
    val p2 = Offset(el.x2 * s, el.y2 * s)
    val color = if (selected) CYAN else AMBER
    drawLine(color, p1, p2, strokeWidth = if (selected) 3f else 2f, cap = StrokeCap.Round)
    listOf(p1, p2).forEach { pt ->
        drawCircle(color, radius = 5f, center = pt)
        drawCircle(DEEP, radius = 5f, center = pt, style = Stroke(width = 1.5f))
    }
    val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val label = el.id + " · " + el.lengthPx() + "px"
    drawChipLabel(label, Offset(mid.x + 8f, mid.y - 18f), textMeasurer)
}

private fun DrawScope.drawCenterMark(center: Offset) {
    drawLine(CYAN, Offset(center.x - 8f, center.y), Offset(center.x + 8f, center.y), strokeWidth = 2f)
    drawLine(CYAN, Offset(center.x, center.y - 8f), Offset(center.x, center.y + 8f), strokeWidth = 2f)
    drawCircle(CYAN, radius = 2.5f, center = center)
}

private fun DrawScope.drawPendingMarker(center: Offset) {
    drawCircle(CYAN, radius = 8f, center = center, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
}

private fun DrawScope.drawPreviewLine(p1: Offset, p2: Offset) {
    drawLine(CYAN, p1, p2, strokeWidth = 2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
}

private fun DrawScope.drawPreviewBox(p1: Offset, p2: Offset) {
    val left = minOf(p1.x, p2.x)
    val top = minOf(p1.y, p2.y)
    val w = kotlin.math.abs(p2.x - p1.x)
    val h = kotlin.math.abs(p2.y - p1.y)
    drawRect(
        color = CYAN,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    )
    drawCenterMark(Offset(left + w / 2f, top + h / 2f))
}

private fun DrawScope.drawQuickDeleteBadge(center: Offset) {
    drawCircle(Color(0xFFFF6B6B), radius = 11f, center = center)
    drawCircle(DEEP, radius = 11f, center = center, style = Stroke(width = 2f))
}

/** Distance-line + label from each edge of the sized box to the corresponding image edge. Port of drawDimensionLines(). */
private fun DrawScope.drawDimensionLines(el: BlueprintElement.Dot, frame: AnnotationFrame, textMeasurer: TextMeasurer) {
    val s = frame.scale
    val box = el.boxMetrics(frame.naturalW, frame.naturalH)
    val bx1 = box.x1 * s; val by1 = box.y1 * s
    val bx2 = box.x2 * s; val by2 = box.y2 * s
    val midX = (bx1 + bx2) / 2f; val midY = (by1 + by2) / 2f
    val canvasW = frame.naturalW * s; val canvasH = frame.naturalH * s

    if (box.w > 0 && box.h > 0) {
        drawRect(
            color = AMBER,
            topLeft = Offset(bx1, by1),
            size = androidx.compose.ui.geometry.Size(bx2 - bx1, by2 - by1),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))),
            alpha = 0.75f
        )
    }

    val dimColor = CYAN.copy(alpha = 0.55f)
    // left
    drawLine(dimColor, Offset(0f, midY), Offset(bx1, midY), strokeWidth = 1f)
    drawChipLabel("<- left ${box.left}px", Offset(max(4f, bx1 / 2f - 30f), midY - 18f), textMeasurer, filled = false)
    // right
    drawLine(dimColor, Offset(bx2, midY), Offset(canvasW, midY), strokeWidth = 1f)
    drawChipLabel("right ${box.right}px ->", Offset(bx2 + (canvasW - bx2) / 2f - 34f, midY - 18f), textMeasurer, filled = false)
    // top
    drawLine(dimColor, Offset(midX, 0f), Offset(midX, by1), strokeWidth = 1f)
    drawChipLabel("^ top ${box.top}px", Offset(midX + 6f, max(2f, by1 / 2f - 8f)), textMeasurer, filled = false)
    // bottom
    drawLine(dimColor, Offset(midX, by2), Offset(midX, canvasH), strokeWidth = 1f)
    drawChipLabel("bottom ${box.bottom}px v", Offset(midX + 6f, by2 + (canvasH - by2) / 2f - 8f), textMeasurer, filled = false)
}

private fun DrawScope.drawChipLabel(
    text: String,
    anchor: Offset,
    textMeasurer: TextMeasurer,
    filled: Boolean = true,
    borderColor: Color? = null
) {
    val style = TextStyle(color = if (filled) AMBER else CYAN, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val layout = textMeasurer.measure(text, style)
    val padH = 5f; val padV = 2f
    val bgSize = androidx.compose.ui.geometry.Size(
        layout.size.width + padH * 2f,
        layout.size.height + padV * 2f
    )
    if (filled) {
        drawRect(DEEP.copy(alpha = 0.92f), topLeft = anchor, size = bgSize)
        if (borderColor != null) {
            drawRect(borderColor, topLeft = anchor, size = bgSize, style = Stroke(width = 1f))
        }
    }
    drawText(layout, topLeft = Offset(anchor.x + padH, anchor.y + padV))
}
