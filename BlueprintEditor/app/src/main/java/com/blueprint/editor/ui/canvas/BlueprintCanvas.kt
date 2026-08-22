package com.blueprint.editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
            val density = LocalDensity.current
            val contentWPx = viewModel.naturalW * transform.scale
            val contentHPx = viewModel.naturalH * transform.scale
            val contentWDp = with(density) { contentWPx.toDp() }
            val contentHDp = with(density) { contentHPx.toDp() }

            // Image, panned+scaled via offset/size (equivalent to canvasInner's CSS transform).
            Image(
                painter = remember(bitmap) { BitmapPainter(bitmap) },
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(transform.panX.roundToInt(), transform.panY.roundToInt()) }
                    .size(width = contentWDp, height = contentHDp)
            )

            // Annotation overlay drawn directly in the wrap's own (unscaled)
            // coordinate space at natural*scale+pan, so it never needs its own transform.
            Canvas(modifier = Modifier.fillMaxSize()) {
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
