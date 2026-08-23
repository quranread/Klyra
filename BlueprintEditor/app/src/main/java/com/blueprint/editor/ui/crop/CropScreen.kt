package com.blueprint.editor.ui.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.BgPanel
import com.blueprint.editor.ui.theme.OnAmber
import kotlin.math.roundToInt

private const val HANDLE_HIT_RADIUS = 34f

/**
 * A dedicated, full-screen crop tool — separate from the main mapping canvas
 * (its own simple fit-to-screen, no pan/zoom/dot/line concerns) so cropping
 * is a focused, professional-feeling step: drag any corner/edge handle to
 * resize, drag inside the selection to move it, lock an aspect ratio, or
 * reset back to the full image. On Apply, the caller gets the crop rect back
 * in NATURAL (original-image) pixel coordinates — the one true unit every
 * other part of the app already speaks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: ImageBitmap,
    naturalW: Int,
    naturalH: Int,
    onCancel: () -> Unit,
    onApply: (left: Int, top: Int, width: Int, height: Int) -> Unit
) {
    val cropState = remember(naturalW, naturalH) { CropRectState(naturalW, naturalH) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Image", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPanel),
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
                actions = {
                    TextButton(onClick = { cropState.reset() }) { Text("Reset") }
                    Button(
                        onClick = {
                            onApply(
                                cropState.left.roundToInt(),
                                cropState.top.roundToInt(),
                                cropState.width.roundToInt(),
                                cropState.height.roundToInt()
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("Crop") }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(BgPanel).padding(vertical = 10.dp)) {
                Text(
                    "Aspect ratio",
                    color = com.blueprint.editor.ui.theme.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CROP_ASPECT_PRESETS) { preset ->
                        val selected = cropState.lockedAspect == preset.ratio
                        FilterChip(
                            selected = selected,
                            onClick = { cropState.setAspect(preset.ratio) },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber,
                                selectedLabelColor = OnAmber
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgDeep)
                .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) },
            contentAlignment = Alignment.Center
        ) {
            if (containerSize.width > 0f) {
                val fitScale = minOf(
                    (containerSize.width - 32f) / naturalW,
                    (containerSize.height - 32f) / naturalH
                ).coerceAtMost(1f)
                val displayWPx = naturalW * fitScale
                val displayHPx = naturalH * fitScale
                val originX = (containerSize.width - displayWPx) / 2f
                val originY = (containerSize.height - displayHPx) / 2f

                val displayWDp = with(density) { displayWPx.toDp() }
                val displayHDp = with(density) { displayHPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(originX.roundToInt(), originY.roundToInt()) }
                        .size(displayWDp, displayHDp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawImage(
                            image = bitmap,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(naturalW, naturalH) {
                            detectCropGestures(cropState, fitScale, originX, originY)
                        }
                ) {
                    val rectLeft = originX + cropState.left * fitScale
                    val rectTop = originY + cropState.top * fitScale
                    val rectRight = originX + cropState.right * fitScale
                    val rectBottom = originY + cropState.bottom * fitScale

                    val scrim = Color.Black.copy(alpha = 0.6f)
                    drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, rectTop))
                    drawRect(scrim, topLeft = Offset(0f, rectBottom), size = Size(size.width, size.height - rectBottom))
                    drawRect(scrim, topLeft = Offset(0f, rectTop), size = Size(rectLeft, rectBottom - rectTop))
                    drawRect(scrim, topLeft = Offset(rectRight, rectTop), size = Size(size.width - rectRight, rectBottom - rectTop))

                    drawRect(
                        Amber,
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectRight - rectLeft, rectBottom - rectTop),
                        style = Stroke(width = 2.5f)
                    )

                    val gridColor = Color.White.copy(alpha = 0.5f)
                    val w = rectRight - rectLeft
                    val h = rectBottom - rectTop
                    for (i in 1..2) {
                        val gx = rectLeft + w * i / 3f
                        drawLine(gridColor, Offset(gx, rectTop), Offset(gx, rectBottom), strokeWidth = 1f)
                        val gy = rectTop + h * i / 3f
                        drawLine(gridColor, Offset(rectLeft, gy), Offset(rectRight, gy), strokeWidth = 1f)
                    }

                    val cornerLen = 20f
                    listOf(
                        Offset(rectLeft, rectTop) to Offset(1f, 1f),
                        Offset(rectRight, rectTop) to Offset(-1f, 1f),
                        Offset(rectLeft, rectBottom) to Offset(1f, -1f),
                        Offset(rectRight, rectBottom) to Offset(-1f, -1f)
                    ).forEach { (corner, dir) ->
                        drawLine(Amber, corner, Offset(corner.x + cornerLen * dir.x, corner.y), strokeWidth = 4f)
                        drawLine(Amber, corner, Offset(corner.x, corner.y + cornerLen * dir.y), strokeWidth = 4f)
                    }
                    val midTop = Offset((rectLeft + rectRight) / 2f, rectTop)
                    val midBottom = Offset((rectLeft + rectRight) / 2f, rectBottom)
                    val midLeft = Offset(rectLeft, (rectTop + rectBottom) / 2f)
                    val midRight = Offset(rectRight, (rectTop + rectBottom) / 2f)
                    listOf(midTop, midBottom).forEach {
                        drawLine(Amber, Offset(it.x - 10f, it.y), Offset(it.x + 10f, it.y), strokeWidth = 4f)
                    }
                    listOf(midLeft, midRight).forEach {
                        drawLine(Amber, Offset(it.x, it.y - 10f), Offset(it.x, it.y + 10f), strokeWidth = 4f)
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectCropGestures(
    state: CropRectState,
    fitScale: Float,
    originX: Float,
    originY: Float
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val rectLeft = originX + state.left * fitScale
        val rectRight = originX + state.right * fitScale
        val rectTop = originY + state.top * fitScale
        val rectBottom = originY + state.bottom * fitScale

        val corners = mapOf(
            CropHandle.TOP_LEFT to Offset(rectLeft, rectTop),
            CropHandle.TOP_RIGHT to Offset(rectRight, rectTop),
            CropHandle.BOTTOM_LEFT to Offset(rectLeft, rectBottom),
            CropHandle.BOTTOM_RIGHT to Offset(rectRight, rectBottom),
            CropHandle.TOP to Offset((rectLeft + rectRight) / 2f, rectTop),
            CropHandle.BOTTOM to Offset((rectLeft + rectRight) / 2f, rectBottom),
            CropHandle.LEFT to Offset(rectLeft, (rectTop + rectBottom) / 2f),
            CropHandle.RIGHT to Offset(rectRight, (rectTop + rectBottom) / 2f)
        )

        var handle = corners.entries
            .minByOrNull { (_, pos) -> (down.position - pos).getDistance() }
            ?.takeIf { (_, pos) -> (down.position - pos).getDistance() <= HANDLE_HIT_RADIUS }
            ?.key

        if (handle == null) {
            val insideRect = down.position.x in rectLeft..rectRight && down.position.y in rectTop..rectBottom
            handle = if (insideRect) CropHandle.MOVE else return@awaitEachGesture
        }
        val resolvedHandle = handle

        down.consume()
        val pointer = down.id

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                state.dragHandle(resolvedHandle, delta.x / fitScale, delta.y / fitScale)
                change.consume()
            }
        }
    }
}
