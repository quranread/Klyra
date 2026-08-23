package com.blueprint.editor.ui.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.BgPanel
import com.blueprint.editor.ui.theme.OnAmber
import kotlin.math.roundToInt

/**
 * The "Pro Crop" tool — its own floating Dialog window (not a screen, not a
 * reused destination). Sized and positioned to match the exact reference
 * measurements taken with the Blueprint Editor off a 1080x2436 screenshot
 * (left 48 / right 49 / top 160 / bottom 169), expressed here as proportions
 * of the real screen so it holds the same relative position on any device.
 *
 * Everything the existing CropScreen already does well — the crop rect drag
 * gestures, aspect-ratio presets, min-size clamping — is reused as-is via
 * [CropRectState] and [detectCropGestures]. Only what didn't exist yet
 * (rotate, flip, oval mask, and the floating-window presentation itself) is
 * new here.
 */
private const val REF_W = 1080f
private const val REF_H = 2436f
private const val MARGIN_LEFT_FRAC = 48f / REF_W
private const val MARGIN_RIGHT_FRAC = 49f / REF_W
private const val MARGIN_TOP_FRAC = 160f / REF_H
private const val MARGIN_BOTTOM_FRAC = 169f / REF_H

@Composable
fun ProCropDialog(
    sourceBitmap: ImageBitmap,
    onDismiss: () -> Unit,
    onApply: (ImageBitmap) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
        ) {
            val marginLeft = maxWidth * MARGIN_LEFT_FRAC
            val marginRight = maxWidth * MARGIN_RIGHT_FRAC
            val marginTop = maxHeight * MARGIN_TOP_FRAC
            val marginBottom = maxHeight * MARGIN_BOTTOM_FRAC

            ProCropPanel(
                sourceBitmap = sourceBitmap,
                onCancel = onDismiss,
                onApply = onApply,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = marginLeft, y = marginTop)
                    .size(
                        width = maxWidth - marginLeft - marginRight,
                        height = maxHeight - marginTop - marginBottom
                    )
            )
        }
    }
}

@Composable
private fun ProCropPanel(
    sourceBitmap: ImageBitmap,
    onCancel: () -> Unit,
    onApply: (ImageBitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    // Rotate/flip are baked into the working bitmap immediately, so pixels on
    // screen always match what Done will actually crop out of.
    var workingBitmap by remember { mutableStateOf(sourceBitmap) }
    var naturalW by remember { mutableIntStateOf(sourceBitmap.width) }
    var naturalH by remember { mutableIntStateOf(sourceBitmap.height) }

    // Re-created whenever the working image's dimensions change (e.g. after a
    // 90° rotate), so the crop rect always starts full-frame on the new size.
    val cropState = remember(naturalW, naturalH) { CropRectState(naturalW, naturalH) }
    val transformState = remember { CropTransformState() }

    var containerSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BgDeep)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Crop", color = Color.White, fontWeight = FontWeight.Bold)
        }

        // Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BgDeep)
                .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) },
            contentAlignment = Alignment.TopStart
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
                            image = workingBitmap,
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

                    if (transformState.isOval) {
                        drawOval(
                            Amber,
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                            style = Stroke(width = 2.5f)
                        )
                    } else {
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

        // Aspect ratio strip — same presets/state as the existing CropScreen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161616))
                .padding(vertical = 10.dp)
        ) {
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

        // Rotate / Reset / Lock / Oval
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIconButton(label = "Rotate") {
                workingBitmap = CropTransform.rotate90(workingBitmap)
                naturalW = workingBitmap.width
                naturalH = workingBitmap.height
            }
            ToolIconButton(label = "Reset") {
                workingBitmap = sourceBitmap
                naturalW = sourceBitmap.width
                naturalH = sourceBitmap.height
                cropState.reset()
                transformState.reset()
            }
            ToolIconButton(
                label = "Lock",
                active = cropState.lockedAspect != null
            ) {
                if (cropState.lockedAspect == null) {
                    cropState.setAspect(cropState.width / cropState.height)
                } else {
                    cropState.setAspect(null)
                }
            }
            ToolIconButton(
                label = "Oval",
                active = transformState.isOval
            ) { transformState.toggleOval() }
        }

        // Flip / Cancel / Done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIconButton(label = "Flip H", active = transformState.flippedH) {
                workingBitmap = CropTransform.flipHorizontal(workingBitmap)
                transformState.toggleFlipH()
            }
            ToolIconButton(label = "Flip V", active = transformState.flippedV) {
                workingBitmap = CropTransform.flipVertical(workingBitmap)
                transformState.toggleFlipV()
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFFFF6B6B))
            }
            Button(
                onClick = {
                    val result = CropTransform.cropAndMask(
                        source = workingBitmap,
                        left = cropState.left.roundToInt(),
                        top = cropState.top.roundToInt(),
                        width = cropState.width.roundToInt(),
                        height = cropState.height.roundToInt(),
                        oval = transformState.isOval
                    )
                    onApply(result)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber)
            ) { Text("Done") }
        }
    }
}

@Composable
private fun ToolIconButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = if (active) Amber else Color(0xFF9A9A9A),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

