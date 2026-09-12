package com.blueprint.editor.data

/**
 * The rectangle (in full-image natural pixels) that all edge-distance
 * reporting is measured against — either the whole image (the default), or
 * a smaller "Active Area" the person marks out (e.g. to exclude a status
 * bar / nav bar strip from every measurement) WITHOUT physically cropping
 * any pixels.
 *
 * Every [BlueprintElement]'s own x/y/x1/y1/x2/y2 always stays an absolute
 * coordinate against the FULL original image — nothing about placement,
 * canvas rendering, or hit-testing ever reads this. [MeasurementFrame] only
 * changes what "distance from the left/top/right/bottom edge" means when
 * *reporting* numbers (Edit sheet, JSON export, AI Instructions), which is
 * exactly what makes it safe to set, change, or clear at any time — no
 * element ever moves or gets deleted because of it (unlike an actual crop).
 */
data class MeasurementFrame(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int
) {
    /** True when this frame is the whole image, i.e. nothing has been marked as an Active Area. */
    val isFullImage: Boolean get() = originX == 0 && originY == 0

    /** Expresses a frame-relative horizontal distance (e.g. a [BoxMetrics.left] value) as a percentage of this frame's width — always correct regardless of device/density, since it's a pure ratio. */
    fun percentOfWidth(value: Int): Double = if (width != 0) (value.toDouble() / width) * 100.0 else 0.0

    /** Same as [percentOfWidth] but for vertical distances against this frame's height. */
    fun percentOfHeight(value: Int): Double = if (height != 0) (value.toDouble() / height) * 100.0 else 0.0

    companion object {
        fun fullImage(naturalW: Int, naturalH: Int) = MeasurementFrame(0, 0, naturalW, naturalH)
    }
}

/** Formats a percentage to one decimal place, e.g. 18.5 -> "18.5%". Centralized so every screen/export formats the same way. */
fun formatPercent(value: Double): String = String.format("%.1f%%", value)
