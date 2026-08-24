package com.blueprint.editor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Central state + business logic for the editor session. This is a direct
 * port of the module-level `let` variables and functions in the original
 * script's top-level IIFE (elements, redoStack, nextDotIndex/nextLineIndex,
 * selectedId, drawMode, pendingLineStart/pendingBoxStart, genId, undo/redo,
 * commitPlacement, clear).
 *
 * Canvas transform state (scale/pan/zoom lock) and UI-only concerns (magnifier,
 * coord strip) are intentionally NOT here — those land in Part 3 alongside the
 * actual canvas composable, since they're view/gesture concerns rather than
 * document state.
 */
class BlueprintViewModel : ViewModel() {

    // ---- Image / session ----
    var filename by mutableStateOf("")
        private set
    var naturalW by mutableStateOf(0)
        private set
    var naturalH by mutableStateOf(0)
        private set
    var imageUri by mutableStateOf<String?>(null)
        private set

    // ---- Elements ----
    val elements = mutableStateListOf<BlueprintElement>()
    private val redoStack = mutableStateListOf<BlueprintElement>()
    private var nextDotIndex = 1
    private var nextLineIndex = 1

    var selectedId by mutableStateOf<String?>(null)
        private set

    // ---- Active tool + in-progress placement ----
    var drawMode by mutableStateOf(DrawMode.DOT)
        private set
    var pendingLineStart by mutableStateOf<NaturalPoint?>(null)
        private set
    var pendingBoxStart by mutableStateOf<NaturalPoint?>(null)
        private set

    val hasElements: Boolean get() = elements.isNotEmpty()
    val canUndo: Boolean get() = elements.isNotEmpty() || pendingLineStart != null || pendingBoxStart != null
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    // ---- Image lifecycle ----

    /** Loads a new image, resetting all mapped elements — matches `loadImage()`. */
    fun loadImage(uri: String, name: String, width: Int, height: Int) {
        imageUri = uri
        filename = name
        naturalW = width
        naturalH = height
        elements.clear()
        redoStack.clear()
        nextDotIndex = 1
        nextLineIndex = 1
        selectedId = null
        pendingLineStart = null
        pendingBoxStart = null
        drawMode = DrawMode.DOT
    }

    /**
     * Applies a crop: the visible image itself has already been re-decoded to
     * [newW]x[newH] by the caller (the actual pixel crop happens in
     * [cropBitmap]) — this just re-anchors every existing element to the new,
     * smaller coordinate space by subtracting the crop's top-left offset, and
     * drops anything that fell entirely outside the new bounds (with a small
     * margin so a box that's only partly clipped is kept, not deleted).
     * Unlike [loadImage], this does NOT clear elements or reset undo/redo —
     * cropping refines an in-progress mapping session rather than starting a
     * new one.
     */
    fun applyCrop(cropLeft: Int, cropTop: Int, newW: Int, newH: Int) {
        val margin = 40
        val kept = elements.mapNotNull { el ->
            when (el) {
                is BlueprintElement.Dot -> {
                    val nx = el.x - cropLeft
                    val ny = el.y - cropTop
                    if (nx < -margin || ny < -margin || nx > newW + margin || ny > newH + margin) null
                    else el.copy(x = nx, y = ny)
                }
                is BlueprintElement.Line -> {
                    val nx1 = el.x1 - cropLeft; val ny1 = el.y1 - cropTop
                    val nx2 = el.x2 - cropLeft; val ny2 = el.y2 - cropTop
                    val bothOutside = (nx1 < -margin || nx1 > newW + margin || ny1 < -margin || ny1 > newH + margin) &&
                        (nx2 < -margin || nx2 > newW + margin || ny2 < -margin || ny2 > newH + margin)
                    if (bothOutside) null else el.copy(x1 = nx1, y1 = ny1, x2 = nx2, y2 = ny2)
                }
            }
        }
        elements.clear()
        elements.addAll(kept)
        redoStack.clear()
        naturalW = newW
        naturalH = newH
        selectedId = null
        pendingLineStart = null
        pendingBoxStart = null
    }

    // ---- Tool selection ----

    fun selectDrawMode(mode: DrawMode) {
        // Switching tools mid-placement cancels the pending line/box, same as
        // the web version implicitly does (a new mode never resumed old
        // pending state — pendingLineStart/pendingBoxStart were mode-specific).
        pendingLineStart = null
        pendingBoxStart = null
        drawMode = mode
    }

    // ---- ID generation ----

    private fun genId(kind: String): String = if (kind == "line") {
        "L-" + nextLineIndex++.toString().padStart(2, '0')
    } else {
        "E-" + nextDotIndex++.toString().padStart(2, '0')
    }

    // ---- Placement (tap-to-place workflow) ----

    /**
     * Handles a tap at [point] (original-image pixel space) according to the
     * active [drawMode]. Returns the id of a newly-committed element (so the
     * caller can open its edit sheet), or null if this tap only started a
     * pending line/box, or placed a plain dot needing no immediate id-return
     * distinction (a dot is also returned, since the web version opens the
     * sheet for those too via the caller — see commitPlacement()).
     */
    fun commitPlacement(point: NaturalPoint): String? {
        return when (drawMode) {
            DrawMode.LINE -> commitLinePlacement(point)
            DrawMode.BOX -> commitBoxPlacement(point)
            DrawMode.DOT -> commitDotPlacement(point)
            DrawMode.PAN -> null // pan mode never places elements
        }
    }

    private fun commitLinePlacement(point: NaturalPoint): String? {
        val start = pendingLineStart
        if (start == null) {
            pendingLineStart = point
            return null
        }
        val id = genId("line")
        elements.add(
            BlueprintElement.Line(id = id, x1 = start.x, y1 = start.y, x2 = point.x, y2 = point.y)
        )
        redoStack.clear()
        pendingLineStart = null
        return id
    }

    private fun commitBoxPlacement(point: NaturalPoint): String? {
        val start = pendingBoxStart
        if (start == null) {
            pendingBoxStart = point
            return null
        }
        val x1 = minOf(start.x, point.x)
        val y1 = minOf(start.y, point.y)
        val w = kotlin.math.abs(point.x - start.x)
        val h = kotlin.math.abs(point.y - start.y)
        val id = genId("dot")
        elements.add(
            BlueprintElement.Dot(
                // Anchor at the box's exact center rather than its top-left
                // corner: the whole point of tapping two corners is usually to
                // find the true middle of a circular/symmetric element (a
                // button, icon, avatar), so the id marker + label should sit
                // exactly on top of the center crosshair, not off at a corner
                // while the crosshair sits elsewhere. Anchor.CENTER makes
                // boxMetrics() reconstruct the same x1/y1/x2/y2 box from this
                // center point, so nothing about the box's size/edges changes —
                // only where the marker itself is drawn.
                id = id, x = x1 + w / 2, y = y1 + h / 2, width = w, height = h,
                type = ElementType.Default, anchor = Anchor.CENTER
            )
        )
        redoStack.clear()
        pendingBoxStart = null
        return id
    }

    private fun commitDotPlacement(point: NaturalPoint): String {
        val id = genId("dot")
        elements.add(
            BlueprintElement.Dot(
                id = id, x = point.x, y = point.y, width = 0, height = 0,
                type = ElementType.Default, anchor = Anchor.TOP_LEFT
            )
        )
        redoStack.clear()
        return id
    }

    // ---- Undo / redo ----

    /** Matches undoBtn's handler: cancel pending placement first, else pop last element. */
    fun undo() {
        if (pendingLineStart != null) { pendingLineStart = null; return }
        if (pendingBoxStart != null) { pendingBoxStart = null; return }
        val last = elements.lastOrNull() ?: return
        elements.removeAt(elements.lastIndex)
        if (selectedId == last.id) selectedId = null
        redoStack.add(last)
    }

    fun redo() {
        val item = redoStack.removeLastOrNull() ?: return
        elements.add(item)
    }

    /** Matches clearBtn's handler (caller is responsible for the confirm dialog). */
    fun clearAll() {
        elements.clear()
        redoStack.clear()
        nextDotIndex = 1
        nextLineIndex = 1
        selectedId = null
    }

    /**
     * Used after Pro Crop's rotate/flip: old dot/line coordinates no longer
     * correspond to the new pixel geometry, so this clears mapped elements
     * (same as [clearAll]) and updates naturalW/H in one call — naturalW/H
     * have private setters, so this can't be done piecemeal from outside.
     */
    fun replaceImageGeometry(newW: Int, newH: Int) {
        elements.clear()
        redoStack.clear()
        nextDotIndex = 1
        nextLineIndex = 1
        selectedId = null
        pendingLineStart = null
        pendingBoxStart = null
        naturalW = newW
        naturalH = newH
    }

    // ---- Selection + editing ----

    fun selectElement(id: String?) {
        selectedId = id
    }

    /**
     * Opens the edit sheet for [id] — matches `openSheet()`: cancels any
     * half-placed line/box (its start point is discarded, not committed).
     */
    fun openElement(id: String) {
        pendingLineStart = null
        pendingBoxStart = null
        selectedId = id
    }

    /** Closes the edit sheet — matches `closeSheet()`'s deselect step. */
    fun closeSelection() {
        selectedId = null
    }

    /** Replaces the element with the given id (used by the edit sheet's live-preview + Save/Done). */
    fun updateElement(updated: BlueprintElement) {
        val index = elements.indexOfFirst { it.id == updated.id }
        if (index >= 0) elements[index] = updated
    }

    fun deleteElement(id: String) {
        elements.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
        // Matches the original: deleting a specific point breaks the linear undo/redo chain.
        redoStack.clear()
    }

    fun elementById(id: String?): BlueprintElement? =
        id?.let { target -> elements.firstOrNull { it.id == target } }
}
