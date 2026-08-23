package com.blueprint.editor.ui.crop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the non-rect transform toggles for the Pro Crop tool (oval mask, and
 * flip flags used purely to highlight the Flip H / Flip V buttons as "on" —
 * the actual pixel flip is baked into the working bitmap immediately via
 * [CropTransform], so this class never touches pixels itself).
 */
class CropTransformState {
    var isOval by mutableStateOf(false)
        private set
    var flippedH by mutableStateOf(false)
        private set
    var flippedV by mutableStateOf(false)
        private set

    fun toggleOval() { isOval = !isOval }
    fun toggleFlipH() { flippedH = !flippedH }
    fun toggleFlipV() { flippedV = !flippedV }

    fun reset() {
        isOval = false; flippedH = false; flippedV = false
    }
}
