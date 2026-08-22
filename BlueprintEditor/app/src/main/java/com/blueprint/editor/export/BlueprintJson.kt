package com.blueprint.editor.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level `*_blueprint.json` document — same shape as the web version's export. */
@Serializable
data class BlueprintJson(
    val image: ImageInfoJson,
    @SerialName("created_at") val createdAt: String,
    @SerialName("coordinate_system") val coordinateSystem: String = "original_pixels_top_left_origin",
    val elements: List<ElementJson>
)

@Serializable
data class ImageInfoJson(
    val filename: String,
    @SerialName("original_width") val originalWidth: Int,
    @SerialName("original_height") val originalHeight: Int,
    @SerialName("aspect_ratio") val aspectRatio: Double
)

/**
 * A single exported element. Both dot and line shapes are flattened into one
 * class (nullable fields) rather than a polymorphic hierarchy, since that's
 * exactly what the original JS produces — an object with `kind` deciding
 * which fields are populated — and it keeps consumers (e.g. an AI reading
 * this JSON) working with one flat, predictable shape per item.
 */
@Serializable
data class ElementJson(
    val id: String,
    val kind: String, // "dot" | "line"

    // --- dot/box fields (kind == "dot") ---
    val type: String? = null,
    val anchor: String? = null,
    @SerialName("dot_x") val dotX: Int? = null,
    @SerialName("dot_y") val dotY: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("center_x") val centerX: Int? = null,
    @SerialName("center_y") val centerY: Int? = null,
    @SerialName("distance_from_left_edge") val distanceFromLeftEdge: Int? = null,
    @SerialName("distance_from_right_edge") val distanceFromRightEdge: Int? = null,
    @SerialName("distance_from_top_edge") val distanceFromTopEdge: Int? = null,
    @SerialName("distance_from_bottom_edge") val distanceFromBottomEdge: Int? = null,

    // --- line fields (kind == "line") ---
    val start: PointJson? = null,
    val end: PointJson? = null,
    @SerialName("length_px") val lengthPx: Int? = null,
    @SerialName("angle_deg") val angleDeg: Int? = null,

    val notes: String? = null
)

@Serializable
data class PointJson(val x: Int, val y: Int)
