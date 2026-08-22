package com.blueprint.editor.data

/**
 * Which corner/edge/center of an element's bounding box the placed dot
 * represents. Mirrors the <select id="sheetAnchor"> options exactly.
 */
enum class Anchor(val label: String) {
    TOP_LEFT("Top-Left corner"),
    TOP_CENTER("Top-Center"),
    TOP_RIGHT("Top-Right corner"),
    CENTER_LEFT("Center-Left"),
    CENTER("Exact Center"),
    CENTER_RIGHT("Center-Right"),
    BOTTOM_LEFT("Bottom-Left corner"),
    BOTTOM_CENTER("Bottom-Center"),
    BOTTOM_RIGHT("Bottom-Right corner");

    /** The string stored in exported JSON, e.g. "top-left". */
    val wireValue: String
        get() = name.lowercase().replace('_', '-')

    companion object {
        val Default = TOP_LEFT

        fun fromWireValue(value: String): Anchor =
            entries.firstOrNull { it.wireValue == value } ?: Default
    }
}
