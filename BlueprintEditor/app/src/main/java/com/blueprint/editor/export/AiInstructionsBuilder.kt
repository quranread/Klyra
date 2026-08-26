package com.blueprint.editor.export

import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.angleDeg
import com.blueprint.editor.data.boxMetrics
import com.blueprint.editor.data.lengthPx

/**
 * Names the 3x3 region a natural-pixel point falls into (top-left,
 * top-center, top-right, middle-left, center, middle-right, bottom-left,
 * bottom-center, bottom-right) — used to give every element a plain-English
 * "where roughly" anchor alongside its exact pixel numbers.
 */
private fun regionName(x: Int, y: Int, naturalW: Int, naturalH: Int): String {
    val col = when {
        x < naturalW / 3 -> "left"
        x < naturalW * 2 / 3 -> "center"
        else -> "right"
    }
    val row = when {
        y < naturalH / 3 -> "top"
        y < naturalH * 2 / 3 -> "middle"
        else -> "bottom"
    }
    return if (row == "middle" && col == "center") "exact center" else "$row-$col area"
}

/**
 * Builds the plain-text, copy-paste-ready instructions block for handing
 * this blueprint to an AI. Rewritten (per direct feedback) to use full,
 * unambiguous sentences instead of terse `label: value` lines or JSON-style
 * keys — every number is spelled out with what it means and which edge it's
 * measured from, plus a plain-English region ("bottom-right area") so a
 * coding AI can double-check itself even without doing the arithmetic.
 */
fun buildAiInstructions(
    filename: String,
    naturalW: Int,
    naturalH: Int,
    elements: List<BlueprintElement>
): String {
    if (elements.isEmpty()) return "No elements mapped yet."

    val header = buildString {
        append("Reference image: ${filename.ifBlank { "attached screenshot" }}.\n")
        append(
            "This image is $naturalH pixels tall from top to bottom, and $naturalW pixels wide " +
                "from left to right. Every position described below is measured against this exact, " +
                "original size — do not rescale or reinterpret these numbers, and do not reposition " +
                "any element based on your own judgement. Y increases downward (0 is the very top " +
                "edge); X increases rightward (0 is the very left edge)."
        )
    }

    val blocks = elements.map { el ->
        when (el) {
            is BlueprintElement.Line -> buildString {
                val startRegion = regionName(el.x1, el.y1, naturalW, naturalH)
                val endRegion = regionName(el.x2, el.y2, naturalW, naturalH)
                append("${el.id} is a line.\n")
                append(
                    "It starts at a point that is ${el.y1} pixels down from the top edge and " +
                        "${el.x1} pixels in from the left edge — that start point sits in the $startRegion " +
                        "of the image.\n"
                )
                append(
                    "It ends at a point that is ${el.y2} pixels down from the top edge and " +
                        "${el.x2} pixels in from the left edge — that end point sits in the $endRegion " +
                        "of the image.\n"
                )
                append("The total length of this line is ${el.lengthPx()} pixels, at an angle of ${el.angleDeg()} degrees.")
                if (el.notes.isNotBlank()) append("\nNote about this line: ${el.notes}")
            }
            is BlueprintElement.Dot -> buildString {
                val box = el.boxMetrics(naturalW, naturalH)
                val anchorRegion = regionName(el.x, el.y, naturalW, naturalH)
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
                    val centerRegion = regionName(box.centerX, box.centerY, naturalW, naturalH)
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
