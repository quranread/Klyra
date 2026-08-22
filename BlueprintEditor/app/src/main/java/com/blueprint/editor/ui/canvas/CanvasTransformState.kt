package com.blueprint.editor.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 5f

/**
 * Holds pan/zoom for the image canvas. Direct port of the module-level
 * `scale`/`panX`/`panY`/`zoomLocked` variables + `setZoom()`/`clampScale()`
 * from the original script. Lives outside [com.blueprint.editor.data.BlueprintViewModel]
 * since it's view/gesture state, not document state (it resets on rotation/
 * recomposition scope, not on undo/redo).
 */
class CanvasTransformState {
    var scale by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set
    var zoomLocked by mutableStateOf(false)
        private set

    val zoomPercent: Int get() = (scale * 100).roundToInt()

    fun clampScale(s: Float): Float = s.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun toggleLock() {
        zoomLocked = !zoomLocked
    }

    /**
     * Fits the image to the given container size, matching `setZoom('fit')`:
     * scale = min(wrapW/naturalW, wrapH/naturalH, 1), then centers it.
     */
    fun fitToContainer(containerW: Float, containerH: Float, naturalW: Int, naturalH: Int) {
        if (naturalW <= 0 || naturalH <= 0) return
        val wrapW = containerW - 16f
        val wrapH = containerH - 16f
        scale = clampScale(min(min(wrapW / naturalW, wrapH / naturalH), 1f))
        panX = (containerW - naturalW * scale) / 2f
        panY = (containerH - naturalH * scale) / 2f
    }

    /**
     * Sets zoom to [newScale], keeping the point under (anchorX, anchorY) —
     * in the *container's* local coordinates — visually fixed. Matches the
     * anchored branch of `setZoom(val, anchorClientX, anchorClientY)`.
     */
    fun setZoomAtAnchor(newScale: Float, anchorX: Float, anchorY: Float) {
        if (zoomLocked) return
        val anchorNaturalX = (anchorX - panX) / scale
        val anchorNaturalY = (anchorY - panY) / scale
        scale = clampScale(newScale)
        panX = anchorX - anchorNaturalX * scale
        panY = anchorY - anchorNaturalY * scale
    }

    /** Zoom in/out around the container's center — used by the +/- toolbar buttons. */
    fun zoomStep(factor: Float, containerW: Float, containerH: Float) {
        if (zoomLocked) return
        setZoomAtAnchor(scale * factor, containerW / 2f, containerH / 2f)
    }

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /** Sets scale+pan directly, bypassing anchor recomputation — used by the live pinch loop, which computes its own fixed anchor once at gesture start (matching the original's `pinch = {...}` object). */
    fun setScaleAndPanDirect(newScale: Float, newPanX: Float, newPanY: Float) {
        scale = newScale
        panX = newPanX
        panY = newPanY
    }

    /** Converts a point in the container's local coordinates to natural (original-image) pixels. */
    fun toNatural(localX: Float, localY: Float): Pair<Float, Float> =
        (localX - panX) / scale to (localY - panY) / scale

    fun toScreen(naturalX: Float, naturalY: Float): Pair<Float, Float> =
        naturalX * scale + panX to naturalY * scale + panY
}
