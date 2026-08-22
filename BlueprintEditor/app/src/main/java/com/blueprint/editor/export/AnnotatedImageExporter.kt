package com.blueprint.editor.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx

private const val AMBER = 0xFFFFB627.toInt()
private const val CYAN = 0xFF4FD1C5.toInt()
private const val DEEP_STROKE = 0xFF0A1F33.toInt()
private const val LABEL_BG = 0xEB0A1F33.toInt() // ~92% alpha, matches the web export's label chips

/**
 * Renders the original-resolution image with every dot/box/line/center-mark
 * burned in — a direct port of the web version's `exportPngBtn` canvas
 * drawing (dashed box outline, filled dot + dark ring, cyan center
 * crosshair, amber line + endpoint circles, dark label chips). This is what
 * gets handed to an AI alongside the JSON/instructions: a single image that
 * visually confirms every measurement.
 *
 * Always draws at the image's *natural* resolution (no on-screen scale/pan)
 * — the annotations must land on the exact same pixels recorded in the data.
 */
fun buildAnnotatedBitmap(
    source: ImageBitmap,
    naturalW: Int,
    naturalH: Int,
    elements: List<BlueprintElement>
): Bitmap {
    val result = source.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; style = Paint.Style.FILL }
    val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DEEP_STROKE; style = Paint.Style.STROKE; strokeWidth = 3f }
    val boxOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AMBER; style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }
    val centerCross = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN; style = Paint.Style.STROKE; strokeWidth = 2f }
    val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN; style = Paint.Style.FILL }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AMBER; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LABEL_BG; style = Paint.Style.FILL }
    val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AMBER; textSize = 24f; isFakeBoldText = true
    }

    elements.forEach { el ->
        when (el) {
            is BlueprintElement.Line -> {
                canvas.drawLine(el.x1.toFloat(), el.y1.toFloat(), el.x2.toFloat(), el.y2.toFloat(), linePaint)
                listOf(el.x1 to el.y1, el.x2 to el.y2).forEach { (x, y) ->
                    canvas.drawCircle(x.toFloat(), y.toFloat(), 8f, dotFill)
                    canvas.drawCircle(x.toFloat(), y.toFloat(), 8f, dotRing)
                }
                val midX = (el.x1 + el.x2) / 2f
                val midY = (el.y1 + el.y2) / 2f
                drawLabel(canvas, "${el.id} · ${el.lengthPx()}px", midX + 10f, midY - 16f, labelBg, labelText)
            }
            is BlueprintElement.Dot -> {
                if (el.width > 0 && el.height > 0) {
                    val box = el.boxMetrics(naturalW, naturalH)
                    canvas.drawRect(
                        box.x1.toFloat(), box.y1.toFloat(), box.x2.toFloat(), box.y2.toFloat(), boxOutline
                    )
                    val cx = box.centerX.toFloat(); val cy = box.centerY.toFloat()
                    canvas.drawLine(cx - 10f, cy, cx + 10f, cy, centerCross)
                    canvas.drawLine(cx, cy - 10f, cx, cy + 10f, centerCross)
                    canvas.drawCircle(cx, cy, 3f, centerDot)
                }
                canvas.drawCircle(el.x.toFloat(), el.y.toFloat(), 10f, dotFill)
                canvas.drawCircle(el.x.toFloat(), el.y.toFloat(), 10f, dotRing)
                drawLabel(canvas, "${el.id} (${el.type.wireValue})", el.x + 16f, el.y - 18f, labelBg, labelText)
            }
        }
    }

    return result
}

private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, bgPaint: Paint, textPaint: Paint) {
    val textWidth = textPaint.measureText(text)
    val padH = 8f
    val padV = 6f
    val rect = RectF(x, y, x + textWidth + padH * 2f, y + textPaint.textSize + padV * 2f)
    canvas.drawRect(rect, bgPaint)
    canvas.drawText(text, x + padH, y + textPaint.textSize + padV / 2f, textPaint)
}
