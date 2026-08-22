package com.blueprint.editor.data

/**
 * One mapped element on the blueprint canvas. Coordinates are always in the
 * *original image's* pixel space (top-left origin), never in on-screen/zoomed
 * pixels — matching `naturalPointFromClient()` in the original web version.
 */
sealed class BlueprintElement {
    abstract val id: String
    abstract val notes: String

    /**
     * A single point (optionally sized into a box). Created by the Dot tool
     * (width=height=0) or the Box tool (width/height from the dragged rect).
     */
    data class Dot(
        override val id: String,
        val x: Int,
        val y: Int,
        val width: Int = 0,
        val height: Int = 0,
        val type: ElementType = ElementType.Default,
        val anchor: Anchor = Anchor.Default,
        override val notes: String = ""
    ) : BlueprintElement() {
        val isSized: Boolean get() = width > 0 && height > 0
    }

    /** A two-point measurement line, created by the Line tool. */
    data class Line(
        override val id: String,
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        override val notes: String = ""
    ) : BlueprintElement()
}
