package com.blueprint.editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.blueprint.editor.data.BlueprintElement
import com.blueprint.editor.data.NaturalPoint

private const val DOT_HIT_RADIUS = 24f
private const val LINE_ENDPOINT_HIT_RADIUS = 20f
private const val BADGE_HIT_RADIUS = 18f
private const val SNAP_RADIUS = 26f

sealed class HitResult {
    data class SelectElement(val id: String) : HitResult()
    object DeleteSelected : HitResult()
}

/**
 * Tests a screen-space (canvas-wrap-local, i.e. RAW pointer position before
 * any pan/zoom transform) tap point against, in priority order: the selected
 * element's quick-delete badge, then dot markers, then line endpoints.
 *
 * IMPORTANT: [pan] must be the same (panX, panY) the canvas is currently
 * rendered with. Annotations are drawn via `translate(transform.panX,
 * transform.panY) { drawAnnotations(...) }` in BlueprintCanvas, so their
 * on-screen position is always `natural * scale + pan` — this function must
 * add that same pan offset, or every hit-test will silently drift by
 * whatever the current pan is (which is essentially never zero, since
 * fit-to-screen itself centers the image with a non-zero pan). Without this,
 * tapping an existing dot/line to select it — or tapping its quick-delete
 * badge — simply won't register once the user has panned/zoomed at all.
 *
 * Returns null if the tap didn't land on anything interactive, meaning the
 * caller should fall through to normal placement/pan handling — matching
 * `e.target.closest('.dot')` etc. in the original pointerdown handler.
 */
fun hitTest(
    localPoint: Offset,
    frame: AnnotationFrame,
    pan: Offset
): HitResult? {
    quickDeleteBadgeCenter(frame, pan)?.let { badgeCenter ->
        if ((localPoint - badgeCenter).getDistance() <= BADGE_HIT_RADIUS) {
            return HitResult.DeleteSelected
        }
    }

    val s = frame.scale
    frame.elements.forEach { el ->
        when (el) {
            is BlueprintElement.Dot -> {
                val center = Offset(el.x * s + pan.x, el.y * s + pan.y)
                if ((localPoint - center).getDistance() <= DOT_HIT_RADIUS) {
                    return HitResult.SelectElement(el.id)
                }
            }
            is BlueprintElement.Line -> {
                val p1 = Offset(el.x1 * s + pan.x, el.y1 * s + pan.y)
                val p2 = Offset(el.x2 * s + pan.x, el.y2 * s + pan.y)
                if ((localPoint - p1).getDistance() <= LINE_ENDPOINT_HIT_RADIUS ||
                    (localPoint - p2).getDistance() <= LINE_ENDPOINT_HIT_RADIUS
                ) {
                    return HitResult.SelectElement(el.id)
                }
            }
        }
    }
    return null
}

/**
 * "Hub/spoke" line support: while placing a line's start or end point, if the
 * tap/drag lands within [SNAP_RADIUS] of an EXISTING dot's anchor point or an
 * existing line's endpoint, snap exactly to that point's natural coordinate
 * instead of the raw touch position. This lets several separate line
 * segments share one perfectly-exact shared vertex — draw a line out to the
 * left from a center dot, then another to the right, up, down, etc., all
 * meeting at that same pixel — without needing a heavier multi-point
 * polyline data model. Picks the CLOSEST candidate within range across every
 * element, not just the first match.
 */
fun findSnapNaturalPoint(localPoint: Offset, frame: AnnotationFrame, pan: Offset): NaturalPoint? {
    val s = frame.scale
    var best: NaturalPoint? = null
    var bestDist = SNAP_RADIUS

    frame.elements.forEach { el ->
        when (el) {
            is BlueprintElement.Dot -> {
                val screen = Offset(el.x * s + pan.x, el.y * s + pan.y)
                val d = (localPoint - screen).getDistance()
                if (d <= bestDist) {
                    bestDist = d
                    best = NaturalPoint(el.x, el.y)
                }
            }
            is BlueprintElement.Line -> {
                val p1 = Offset(el.x1 * s + pan.x, el.y1 * s + pan.y)
                val d1 = (localPoint - p1).getDistance()
                if (d1 <= bestDist) {
                    bestDist = d1
                    best = NaturalPoint(el.x1, el.y1)
                }
                val p2 = Offset(el.x2 * s + pan.x, el.y2 * s + pan.y)
                val d2 = (localPoint - p2).getDistance()
                if (d2 <= bestDist) {
                    bestDist = d2
                    best = NaturalPoint(el.x2, el.y2)
                }
            }
        }
    }
    return best
}
