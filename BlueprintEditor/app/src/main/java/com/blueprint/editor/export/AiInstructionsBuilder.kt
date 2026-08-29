package com.blueprint.editor.export

import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.MeasurementFrame
import com.blueprint.editor.data.angleDeg
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx

/**
 * Names the 3x3 region a natural-pixel point falls into (top-left,
 * top-center, top-right, middle-left, center, middle-right, bottom-left,
 * bottom-center, bottom-right) — used to give every element a plain-English
 * "where roughly" anchor alongside its exact pixel numbers. Regions are
 * computed against [frame] (the Active Area if one is set, otherwise the
 * full image) so "bottom-right area" always means the same thing as the
 * printed edge distances, not the full screenshot's corners.
 */
private fun regionName(x: Int, y: Int, frame: MeasurementFrame): String {
    val relX = x - frame.originX
    val relY = y - frame.originY
    val col = when {
        relX < frame.width / 3 -> "left"
        relX < frame.width * 2 / 3 -> "center"
        else -> "right"
    }
    val row = when {
        relY < frame.height / 3 -> "top"
        relY < frame.height * 2 / 3 -> "middle"
        else -> "bottom"
    }
    return if (row == "middle" && col == "center") "exact center" else "$row-$col area"
}

/**
 * Builds the plain-text, copy-paste-ready instructions block for handing
 * this blueprint to an AI. Uses full, unambiguous sentences instead of terse
 * `label: value` lines or JSON-style keys — every number is spelled out with
 * what it means and which edge it's measured from, plus a plain-English
 * region ("bottom-right area") so a coding AI can double-check itself even
 * without doing the arithmetic.
 *
 * [measurementFrame] is Part C's Active Area support: when the person has
 * marked out a sub-rect (e.g. to exclude a status bar / nav bar strip)
 * without physically cropping anything, every distance below is measured
 * against THAT rect instead of the full image — with an explicit sentence
 * saying so, so nothing is ambiguous.
 *
 * [isHtmlConversion] is Part D: HTML is usually built with relative/flow
 * layout (%, flexbox, gap, auto margins) rather than fixed pixel positions,
 * so converting it to or from a fixed-canvas system like Kotlin/Compose is
 * exactly where an AI is most likely to quietly re-derive its own layout
 * instead of trusting these numbers. When true, an extra, more forceful
 * paragraph is appended spelling out the fixed-canvas + single-scale-factor
 * approach explicitly for that conversion direction.
 */
fun buildAiInstructions(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    measurementFrame: MeasurementFrame,
    elements: List<BlueprintElement>,
    isHtmlConversion: Boolean = false
): String {
    if (elements.isEmpty()) return "No elements mapped yet."

    val usesActiveArea = measurementFrame.originX != 0 || measurementFrame.originY != 0 ||
        measurementFrame.width != naturalW || measurementFrame.height != naturalH

    val header = buildString {
        append("Reference image: ${filename.ifBlank { "attached screenshot" }}.\n")
        if (usesActiveArea) {
            append(
                "The full screenshot is $naturalH pixels tall and $naturalW pixels wide, but a smaller " +
                    "active area has been marked out inside it on purpose (for example, to exclude a " +
                    "status bar or navigation bar). Every position below is measured against that marked " +
                    "active area, NOT the full screenshot: the active area is ${measurementFrame.height} " +
                    "pixels tall and ${measurementFrame.width} pixels wide, starting ${measurementFrame.originX} " +
                    "pixels in from the full screenshot's left edge and ${measurementFrame.originY} pixels " +
                    "down from its top edge. Treat the active area's own top-left corner as position (0,0) " +
                    "for every number below.\n"
            )
        } else {
            append(
                "This image is $naturalH pixels tall from top to bottom, and $naturalW pixels wide " +
                    "from left to right. Every position described below is measured against this exact, " +
                    "original size.\n"
            )
        }
        append(
            "Do not rescale or reinterpret these numbers, and do not reposition any element based on " +
                "your own judgement. Y increases downward (0 is the top edge of the area described above); " +
                "X increases rightward (0 is its left edge)."
        )
        if (isHtmlConversion) {
            append("\n\n")
            append(
                "IMPORTANT — this is an HTML-to-code (or code-to-HTML) conversion, which is exactly " +
                    "where layout mistakes happen most: HTML is usually built with relative/flow layout " +
                    "(percentages, flexbox, grid, gap, auto margins), and CSS's own \"px\" unit is NOT the " +
                    "same as a raw device pixel on Android or other native platforms. Follow this exact " +
                    "approach instead of your own layout instincts:\n" +
                    "1. Build ONE fixed-size root container, sized exactly to the reference resolution " +
                    "given above (${measurementFrame.width} by ${measurementFrame.height} pixels) — in " +
                    "HTML that means an element with that literal pixel width and height, position: " +
                    "relative, not a percentage or auto-sized container.\n" +
                    "2. Place every element inside it using absolute positioning only (CSS " +
                    "position: absolute / top / left in pixels, or, in Kotlin/Compose, " +
                    "Modifier.offset { IntOffset(x, y) } in raw pixels) — do not use percentages, " +
                    "flexbox, grid, gap, or auto margins to position anything, even if that would " +
                    "normally be cleaner code.\n" +
                    "3. Scale that one fixed-size root container as a single whole to fit the real " +
                    "screen (one CSS transform: scale(), or one Compose graphicsLayer/scale applied to " +
                    "the root only) — never scale or reposition individual child elements separately.\n" +
                    "4. If an element contains text, its own font engine may render slightly differently " +
                    "between HTML and Kotlin — keep the anchor point given above exactly where stated, " +
                    "and do not resize the element based on how its text happens to wrap."
            )
        }
    }

    val blocks = elements.map { el ->
        when (el) {
            is BlueprintElement.Line -> buildString {
                val startRegion = regionName(el.x1, el.y1, measurementFrame)
                val endRegion = regionName(el.x2, el.y2, measurementFrame)
                append("${el.id} is a line.\n")
                append(
                    "It starts at a point that is ${el.y1 - measurementFrame.originY} pixels down from the top edge and " +
                        "${el.x1 - measurementFrame.originX} pixels in from the left edge — that start point sits in the $startRegion " +
                        "of the image.\n"
                )
                append(
                    "It ends at a point that is ${el.y2 - measurementFrame.originY} pixels down from the top edge and " +
                        "${el.x2 - measurementFrame.originX} pixels in from the left edge — that end point sits in the $endRegion " +
                        "of the image.\n"
                )
                append("The total length of this line is ${el.lengthPx()} pixels, at an angle of ${el.angleDeg()} degrees.")
                if (el.notes.isNotBlank()) append("\nNote about this line: ${el.notes}")
            }
            is BlueprintElement.Dot -> buildString {
                val box = el.boxMetrics(measurementFrame)
                val anchorRegion = regionName(el.x, el.y, measurementFrame)
                append("${el.id} marks a \"${el.type.wireValue}\" element.\n")
                append("This point is ${box.top} pixels down from the top edge of the image.\n")
                append("This point is ${box.bottom} pixels up from the bottom edge of the image.\n")
                append("This point is ${box.left} pixels in from the left edge of the image.\n")
                append("This point is ${box.right} pixels in from the right edge of the image.\n")
                append("That places this element in the $anchorRegion of the image.")
                if (el.width > 0 || el.height > 0) {
                    append(
                        "\nThis element measures ${el.width} pixels wide and ${el.height} pixels tall in total."
                    )
                }
                if (el.isSized) {
                    val centerRegion = regionName(box.x1 + el.width / 2, box.y1 + el.height / 2, measurementFrame)
                    append(
                        "\nThe exact center point of this element is ${box.centerY} pixels down from the " +
                            "top edge and ${box.centerX} pixels in from the left edge (still in the $centerRegion)."
                    )
                }
                if (el.notes.isNotBlank()) append("\nNote about this element: ${el.notes}")
            }
        }
    }

    return (listOf(header) + blocks).joinToString("\n\n")
}
