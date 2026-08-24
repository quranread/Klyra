package com.blueprint.editor.ui.crop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.min

/** Which part of the crop rect a drag gesture grabbed. */
enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, MOVE }

/** A named aspect ratio preset for the crop tool's quick-select chips. */
data class AspectPreset(val label: String, val ratio: Float?) // null = Free

val CROP_ASPECT_PRESETS = listOf(
    AspectPreset("Free", null),
    AspectPreset("1:1", 1f),
    AspectPreset("4:3", 4f / 3f),
    AspectPreset("16:9", 16f / 9f),
    AspectPreset("9:16", 9f / 16f)
)

/**
 * Holds the crop selection in NATURAL (original-image) pixel coordinates —
 * exactly the same unit every other part of the app already uses — so
 * applying the crop is a direct, unambiguous pixel rect with no separate
 * conversion step to get wrong.
 */
class CropRectState(private val naturalW: Int, private val naturalH: Int) {
    var left by mutableFloatStateOf(0f)
        private set
    var top by mutableFloatStateOf(0f)
        private set
    var right by mutableFloatStateOf(naturalW.toFloat())
        private set
    var bottom by mutableFloatStateOf(naturalH.toFloat())
        private set
    var lockedAspect by mutableStateOf<Float?>(null)
        private set

    private val minSize: Float = min(naturalW, naturalH) * 0.08f

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun reset() {
        left = 0f; top = 0f; right = naturalW.toFloat(); bottom = naturalH.toFloat()
        lockedAspect = null
    }

    /**
     * Applies an aspect ratio: always the LARGEST rect of that ratio that
     * fits the full natural image, centered. Previously this shrank the
     * CURRENT (already-shrunk) rect, so repeat-tapping the same chip — or
     * switching between chips — kept shrinking further each time instead of
     * landing on a consistent size. Recomputing from the full image every
     * time makes it idempotent: any number of taps on the same ratio gives
     * the same result.
     */
    fun setAspect(ratio: Float?) {
        lockedAspect = ratio
        if (ratio == null) return
        var w = naturalW.toFloat()
        var h = w / ratio
        if (h > naturalH) {
            h = naturalH.toFloat()
            w = h * ratio
        }
        val newLeft = ((naturalW - w) / 2f).coerceAtLeast(0f)
        val newTop = ((naturalH - h) / 2f).coerceAtLeast(0f)
        left = newLeft
        top = newTop
        right = (newLeft + w).coerceAtMost(naturalW.toFloat())
        bottom = (newTop + h).coerceAtMost(naturalH.toFloat())
    }

    /**
     * Locks (or unlocks) further drags to the rect's CURRENT aspect ratio,
     * without moving or resizing it — unlike [setAspect], which is for the
     * preset chips and snaps to the largest rect of a given ratio.
     */
    fun toggleLockCurrentAspect() {
        lockedAspect = if (lockedAspect == null) width / height else null
    }

    /** Moves the whole rect by (dx, dy) natural px, clamped so it never leaves the image. */
    fun moveBy(dx: Float, dy: Float) {
        val w = width; val h = height
        val newLeft = (left + dx).coerceIn(0f, naturalW - w)
        val newTop = (top + dy).coerceIn(0f, naturalH - h)
        left = newLeft; right = newLeft + w
        top = newTop; bottom = newTop + h
    }

    /** Drags [handle] by (dx, dy) natural px, respecting the min size and any locked aspect ratio. */
    fun dragHandle(handle: CropHandle, dx: Float, dy: Float) {
        if (handle == CropHandle.MOVE) { moveBy(dx, dy); return }

        var l = left; var t = top; var r = right; var b = bottom

        when (handle) {
            CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT -> l = (l + dx).coerceIn(0f, r - minSize)
            else -> {}
        }
        when (handle) {
            CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT -> r = (r + dx).coerceIn(l + minSize, naturalW.toFloat())
            else -> {}
        }
        when (handle) {
            CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT -> t = (t + dy).coerceIn(0f, b - minSize)
            else -> {}
        }
        when (handle) {
            CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT -> b = (b + dy).coerceIn(t + minSize, naturalH.toFloat())
            else -> {}
        }

        lockedAspect?.let { ratio ->
            // Re-derive the dimension the user isn't directly dragging so the
            // ratio holds exactly.
            when (handle) {
                CropHandle.LEFT, CropHandle.RIGHT -> {
                    val targetH = (r - l) / ratio
                    val cy = (t + b) / 2f
                    var newT = (cy - targetH / 2f).coerceAtLeast(0f)
                    var newB = (newT + targetH).coerceAtMost(naturalH.toFloat())
                    newT = newB - targetH
                    t = newT; b = newB
                }
                CropHandle.TOP, CropHandle.BOTTOM -> {
                    val targetW = (b - t) * ratio
                    val cx = (l + r) / 2f
                    var newL = (cx - targetW / 2f).coerceAtLeast(0f)
                    var newR = (newL + targetW).coerceAtMost(naturalW.toFloat())
                    newL = newR - targetW
                    l = newL; r = newR
                }
                CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT -> t = b - (r - l) / ratio
                CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT -> b = t + (r - l) / ratio
                CropHandle.MOVE -> {}
            }
        }

        left = l; top = t; right = r; bottom = b
    }
}
