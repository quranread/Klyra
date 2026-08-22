package com.blueprint.editor.ui.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import com.blueprint.editor.data.DrawMode
import com.blueprint.editor.data.NaturalPoint
import kotlin.math.roundToInt

/** Everything the gesture loop needs to call back into, kept as plain lambdas so this file has no ViewModel/Compose-state coupling. */
class BlueprintGestureCallbacks(
    val drawMode: () -> DrawMode,
    val frame: () -> AnnotationFrame,
    val transform: CanvasTransformState,
    /** Finger moved (or first touched down) while in a placement mode — drives magnifier + line/box preview. Local is canvas-wrap-local px; both args null hides the magnifier. */
    val onPlaceMove: (local: Offset?, natural: NaturalPoint?, isTouch: Boolean) -> Unit,
    /** Finger lifted while placing — commits the point. */
    val onPlaceCommit: (NaturalPoint) -> Unit,
    /** Tap landed on an existing dot/line endpoint. */
    val onSelect: (String) -> Unit,
    /** Tap landed on the selected element's quick-delete badge. */
    val onDeleteSelected: () -> Unit
)

suspend fun PointerInputScope.detectBlueprintCanvasGestures(callbacks: BlueprintGestureCallbacks) {
    awaitEachGesture {
        val activePointers = linkedMapOf<PointerId, Offset>()
        var placingPointerId: PointerId? = null
        var lastPlacingNatural: NaturalPoint? = null
        var panDragPointerId: PointerId? = null
        var panStartPointer = Offset.Zero
        var panStartPan = Offset(callbacks.transform.panX, callbacks.transform.panY)
        var inPinch = false
        var pinchStartDist = 0f
        var pinchStartScale = 1f
        var pinchAnchorNatural = Offset.Zero

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)

            for (change in event.changes) {
                if (change.pressed) activePointers[change.id] = change.position
                else activePointers.remove(change.id)
            }

            val newlyDown = event.changes.filter { it.changedToDown() }
            val newlyUp = event.changes.filter { it.changedToUp() }

            for (change in newlyDown) {
                when (activePointers.size) {
                    1 -> {
                        val local = change.position
                        val hit = hitTest(local, callbacks.frame(), Offset(callbacks.transform.panX, callbacks.transform.panY))
                        when (hit) {
                            is HitResult.SelectElement -> callbacks.onSelect(hit.id)
                            HitResult.DeleteSelected -> callbacks.onDeleteSelected()
                            null -> {
                                if (callbacks.drawMode() == DrawMode.PAN) {
                                    panDragPointerId = change.id
                                    panStartPointer = local
                                    panStartPan = Offset(callbacks.transform.panX, callbacks.transform.panY)
                                } else {
                                    placingPointerId = change.id
                                    val (nx, ny) = callbacks.transform.toNatural(local.x, local.y)
                                    val natural = NaturalPoint(nx.roundToInt(), ny.roundToInt())
                                    lastPlacingNatural = natural
                                    callbacks.onPlaceMove(local, natural, change.type == PointerType.Touch)
                                }
                            }
                        }
                    }
                    2 -> {
                        if (placingPointerId != null) {
                            callbacks.onPlaceMove(null, null, false)
                            placingPointerId = null
                            lastPlacingNatural = null
                        }
                        panDragPointerId = null
                        inPinch = true
                        val pts = activePointers.values.toList()
                        pinchStartDist = (pts[0] - pts[1]).getDistance()
                        pinchStartScale = callbacks.transform.scale
                        val midX = (pts[0].x + pts[1].x) / 2f
                        val midY = (pts[0].y + pts[1].y) / 2f
                        val (anx, any) = callbacks.transform.toNatural(midX, midY)
                        pinchAnchorNatural = Offset(anx, any)
                    }
                    else -> Unit // 3rd+ finger ignored
                }
            }

            when {
                inPinch && activePointers.size >= 2 -> {
                    val pts = activePointers.values.toList()
                    val d = (pts[0] - pts[1]).getDistance()
                    val midX = (pts[0].x + pts[1].x) / 2f
                    val midY = (pts[0].y + pts[1].y) / 2f
                    if (!callbacks.transform.zoomLocked && pinchStartDist > 0f) {
                        val newScale = callbacks.transform.clampScale(pinchStartScale * (d / pinchStartDist))
                        val newPanX = midX - pinchAnchorNatural.x * newScale
                        val newPanY = midY - pinchAnchorNatural.y * newScale
                        callbacks.transform.setScaleAndPanDirect(newScale, newPanX, newPanY)
                    }
                }
                panDragPointerId != null && activePointers.containsKey(panDragPointerId) -> {
                    val cur = activePointers.getValue(panDragPointerId!!)
                    val newPanX = panStartPan.x + (cur.x - panStartPointer.x)
                    val newPanY = panStartPan.y + (cur.y - panStartPointer.y)
                    callbacks.transform.setScaleAndPanDirect(callbacks.transform.scale, newPanX, newPanY)
                }
                placingPointerId != null && activePointers.containsKey(placingPointerId) -> {
                    val cur = activePointers.getValue(placingPointerId!!)
                    val (nx, ny) = callbacks.transform.toNatural(cur.x, cur.y)
                    val natural = NaturalPoint(nx.roundToInt(), ny.roundToInt())
                    lastPlacingNatural = natural
                    val changeType = event.changes.firstOrNull { it.id == placingPointerId }?.type
                    callbacks.onPlaceMove(cur, natural, changeType == PointerType.Touch)
                }
            }

            event.changes.forEach { if (it.positionChange() != Offset.Zero || it.pressed != it.previousPressed) it.consume() }

            for (change in newlyUp) {
                if (change.id == placingPointerId) {
                    placingPointerId = null
                    callbacks.onPlaceMove(null, null, false)
                    lastPlacingNatural?.let { callbacks.onPlaceCommit(it) }
                    lastPlacingNatural = null
                }
                if (change.id == panDragPointerId) {
                    panDragPointerId = null
                }
            }
            if (activePointers.size < 2) inPinch = false

            if (activePointers.isEmpty()) break
        }
    }
}
