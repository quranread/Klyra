package com.blueprint.editor.data

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * The computed bounding box (in original-image pixels) for a sized Dot,
 * plus distances to all four image edges. Direct port of `getBox()`.
 */
data class BoxMetrics(
    val x1: Int, val y1: Int,
    val x2: Int, val y2: Int,
    val w: Int, val h: Int,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val centerX: Int, val centerY: Int
)

/**
 * Computes [BoxMetrics] for a [BlueprintElement.Dot] against the loaded
 * image's natural size, honoring the dot's [Anchor] the same way the web
 * version's `getBox(el)` does.
 */
fun BlueprintElement.Dot.boxMetrics(naturalW: Int, naturalH: Int): BoxMetrics =
    boxMetrics(MeasurementFrame.fullImage(naturalW, naturalH))

/**
 * Same as [boxMetrics], but reports left/top/right/bottom/center relative to
 * [frame] instead of the full image — this is the one place Part C's
 * "Active Area" actually changes any numbers. The box's own x1/y1/x2/y2
 * (this element's true position) are computed exactly as before, in full
 * absolute image pixels; only the distances-to-edges below get re-based to
 * [frame]'s origin/size.
 */
fun BlueprintElement.Dot.boxMetrics(frame: MeasurementFrame): BoxMetrics {
    val w = width
    val h = height
    var x1: Double
    var y1: Double
    when (anchor) {
        Anchor.TOP_LEFT -> { x1 = x.toDouble();        y1 = y.toDouble() }
        Anchor.TOP_CENTER -> { x1 = x - w / 2.0;        y1 = y.toDouble() }
        Anchor.TOP_RIGHT -> { x1 = x - w.toDouble();    y1 = y.toDouble() }
        Anchor.CENTER_LEFT -> { x1 = x.toDouble();      y1 = y - h / 2.0 }
        Anchor.CENTER -> { x1 = x - w / 2.0;             y1 = y - h / 2.0 }
        Anchor.CENTER_RIGHT -> { x1 = x - w.toDouble();  y1 = y - h / 2.0 }
        Anchor.BOTTOM_LEFT -> { x1 = x.toDouble();       y1 = y - h.toDouble() }
        Anchor.BOTTOM_CENTER -> { x1 = x - w / 2.0;      y1 = y - h.toDouble() }
        Anchor.BOTTOM_RIGHT -> { x1 = x - w.toDouble();  y1 = y - h.toDouble() }
    }
    val rx1 = round(x1).toInt()
    val ry1 = round(y1).toInt()
    val rx2 = rx1 + w
    val ry2 = ry1 + h
    return BoxMetrics(
        x1 = rx1, y1 = ry1, x2 = rx2, y2 = ry2, w = w, h = h,
        left = rx1 - frame.originX,
        top = ry1 - frame.originY,
        right = (frame.originX + frame.width) - rx2,
        bottom = (frame.originY + frame.height) - ry2,
        centerX = round(rx1 + w / 2.0).toInt() - frame.originX,
        centerY = round(ry1 + h / 2.0).toInt() - frame.originY
    )
}

/** Euclidean length of a line, rounded to the nearest px — matches `Math.hypot` usage. */
fun BlueprintElement.Line.lengthPx(): Int =
    hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).roundToInt()

/** Direction of a line in degrees, matching `Math.atan2(dy, dx) * 180 / PI`. */
fun BlueprintElement.Line.angleDeg(): Int =
    round(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()) * 180.0 / Math.PI).toInt()
