package com.blueprint.editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.blueprint.editor.data.BlueprintViewModel
import com.blueprint.editor.data.NaturalPoint
import com.blueprint.editor.ui.theme.BgDeep
import kotlin.math.roundToInt

/**
 * The scrollable/zoomable image canvas: image + all dot/line/box annotations
 * + live magnifier while placing. Equivalent to `#canvasWrap` (fixed-size
 * viewport) containing `#canvasInner` (the panned/scaled content) in the
 * original markup — except here the "transform" is applied by drawing
 * everything at `natural * scale + pan` directly, rather than a CSS
 * transform on a child element, which keeps one gesture-owning layer.
 */
@Composable
fun BlueprintCanvas(
    viewModel: BlueprintViewModel,
    bitmap: ImageBitmap,
    transform: CanvasTransformState,
    modifier: Modifier = Modifier,
    onOpenSheet: (String) -> Unit = {},
    onContainerSizeChanged: (Size) -> Unit = {}
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var livePoint by remember { mutableStateOf<NaturalPoint?>(null) }
    var liveLocal by remember { mutableStateOf<Offset?>(null) }
    var isTouchPlacing by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    val frame = AnnotationFrame(
        elements = viewModel.elements,
        selectedId = viewModel.selectedId,
        naturalW = viewModel.naturalW,
        naturalH = viewModel.naturalH,
        scale = transform.scale,
        pendingLineStart = viewModel.pendingLineStart,
        pendingBoxStart = viewModel.pendingBoxStart,
        livePoint = livePoint
    )

    val callbacks = remember(viewModel, transform) {
        BlueprintGestureCallbacks(
            drawMode = { viewModel.drawMode },
            frame = {
                AnnotationFrame(
                    elements = viewModel.elements,
                    selectedId = viewModel.selectedId,
                    naturalW = viewModel.naturalW,
                    naturalH = viewModel.naturalH,
                    scale = transform.scale,
                    pendingLineStart = viewModel.pendingLineStart,
                    pendingBoxStart = viewModel.pendingBoxStart,
                    livePoint = livePoint
                )
            },
            transform = transform,
            onPlaceMove = { local, natural, isTouch ->
                liveLocal = local
                livePoint = natural
                isTouchPlacing = isTouch
            },
            onPlaceCommit = { point ->
                val newId = viewModel.commitPlacement(point)
                // Only dot/completed-line/completed-box placements return an id
                // that should open the edit sheet — a first line/box tap (which
                // just records the pending start point) returns null.
                if (newId != null) {
                    viewModel.openElement(newId)
                    onOpenSheet(newId)
                }
            },
            onSelect = { id -> viewModel.openElement(id); onOpenSheet(id) },
            onDeleteSelected = { viewModel.selectedId?.let { viewModel.deleteElement(it) } }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
                onContainerSizeChanged(containerSize)
            }
            .pointerInput(viewModel.drawMode) {
                detectBlueprintCanvasGestures(callbacks)
            }
    ) {
        if (viewModel.naturalW > 0) {
            // Image + annotations both drawn in the SAME Canvas, in raw pixels,
            // with zero dp<->px conversion anywhere in this path. This is
            // deliberate: an earlier version sized the Image composable via
            // `.size(naturalWDp, naturalHDp)` + `graphicsLayer { scaleX = ... }`,
            // which round-trips through Dp (px -> dp -> px again via density)
            // for the base layout size before the visual scale is even applied.
            // On some devices that round-trip doesn't cancel out cleanly, so the
            // bitmap visually renders far smaller than `transform.scale` implies
            // — while pan/zoom math (all pure px) still thinks it's the full
            // size. That mismatch is exactly what shows up as "empty" canvas
            // area that still responds to taps/magnifier with real image data.
            // Drawing the bitmap with Canvas.drawImage(dstOffset/dstSize in px)
            // guarantees the on-screen pixel size always equals
            // naturalW*scale/naturalH*scale exactly — the same px space
            // pointerInput, CanvasTransformState, and the annotation overlay
            // already use, so there is no second unit system left to drift.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dstW = (viewModel.naturalW * transform.scale).roundToInt().coerceAtLeast(1)
                val dstH = (viewModel.naturalH * transform.scale).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(transform.panX.roundToInt(), transform.panY.roundToInt()),
                    dstSize = IntSize(dstW, dstH)
                )
                translate(transform.panX, transform.panY) {
                    drawAnnotations(frame, textMeasurer)
                }
            }

            if (livePoint != null && isTouchPlacing && liveLocal != null) {
                MagnifierOverlay(
                    bitmap = bitmap,
                    naturalW = viewModel.naturalW,
                    naturalH = viewModel.naturalH,
                    scale = transform.scale,
                    anchorLocal = liveLocal!!,
                    point = livePoint!!,
                    containerSizePx = containerSize
                )
            }
        }
    }
}
