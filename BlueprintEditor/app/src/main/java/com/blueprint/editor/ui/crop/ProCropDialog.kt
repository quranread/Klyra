package com.blueprint.editor.ui.crop

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
    /**
     * [result] = the final cropped (and, if used, rotated/flipped/oval-masked)
     * bitmap. [cropLeft]/[cropTop] = the crop rect's top-left in the *working*
     * bitmap's pixel space — only meaningful re: the ORIGINAL image when
     * [geometryChanged] is false. [geometryChanged] is true if rotate or flip
     * was used even once, meaning existing mapped elements (dots/lines) can no
     * longer be simply re-anchored by subtracting an offset — the caller
     * should treat this as a fresh image rather than trying to remap them.
     */
    onApply: (result: ImageBitmap, cropLeft: Int, cropTop: Int, geometryChanged: Boolean) -> Unit
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
    onApply: (result: ImageBitmap, cropLeft: Int, cropTop: Int, geometryChanged: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Wraps a crop-tool action (rotate/flip/reset/done) so a failure shows a
    // readable error instead of crashing the whole app. If this ever fires,
    // the Toast text is the exact exception — that's what to report back.
    fun safeRun(action: () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            Toast.makeText(context, "Crop error: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
    // True the moment rotate/flip is used even once — after that, the crop
    // rect's (left, top) no longer maps back to the original image's pixel
    // space by simple subtraction, so the caller must not try to re-anchor
    // existing elements against it.
    var geometryChanged by remember { mutableStateOf(false) }

    // Frees whatever the working bitmap ends up being when this dialog
    // closes for ANY reason (Cancel, system back, tapping outside) — Done
    // already recycles it itself right before calling onApply. Without this,
    // cancelling after a rotate/flip would leak that intermediate bitmap.
    val latestWorkingBitmap = rememberUpdatedState(workingBitmap)
    DisposableEffect(Unit) {
        onDispose {
            CropTransform.recycleIfNotProtected(latestWorkingBitmap.value, sourceBitmap)
        }
    }

    // Reference measurement (1080x2436 screenshot, dialog height 2107px):
    // top toolbar 119px, preview 1625px, tools area below preview 363px.
    // Expressed as fractions of the panel's *actual* height so the same
    // proportions hold on any device.
    val topToolbarHeightFraction = 119f / 2107f
    val previewHeightFraction = 1625f / 2107f
    val toolsAreaHeightFraction = 363f / 2107f

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BgDeep)
    ) {
        val panelHeight = maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Box(
            modifier = Modifier
                .height(panelHeight * topToolbarHeightFraction)
                .fillMaxWidth()
                .background(BgPanel),
            contentAlignment = Alignment.Center
        ) {
            Text("Crop", color = Color.White, fontWeight = FontWeight.Bold)
        }

        // Canvas — fixed to the exact measured proportion of the dialog's
        // total height, not "whatever space is left" (weight(1f) doesn't
        // guarantee an exact match if the other rows' heights vary).
        Box(
            modifier = Modifier
                .height(panelHeight * previewHeightFraction)
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

        // Everything below the preview (aspect strip + rotate/reset/lock/oval
        // row + flip/cancel/done row) is capped to the exact measured 363px
        // (17.23% of dialog height) — this whole block gets a compact
        // rework once the reference "tools" screenshot arrives; for now the
        // three rows share this fixed-height space with tighter padding.
        Column(
            modifier = Modifier
                .height(panelHeight * toolsAreaHeightFraction)
                .fillMaxWidth()
        ) {
        // Aspect ratio strip — same presets/state as the existing CropScreen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF161616))
                .padding(vertical = 4.dp)
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

        // Rotate / Reset / Lock / Oval — icon-only, white row (matches reference)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIconButton(icon = Icons.Filled.RotateLeft) {
                safeRun {
                    val old = workingBitmap
                    workingBitmap = CropTransform.rotate90(old)
                    CropTransform.recycleIfNotProtected(old, sourceBitmap)
                    naturalW = workingBitmap.width
                    naturalH = workingBitmap.height
                    geometryChanged = true
                }
            }
            ToolIconButton(icon = Icons.Filled.RotateRight) {
                safeRun {
                    val old = workingBitmap
                    workingBitmap = CropTransform.rotate90(old)
                    CropTransform.recycleIfNotProtected(old, sourceBitmap)
                    naturalW = workingBitmap.width
                    naturalH = workingBitmap.height
                    geometryChanged = true
                }
            }
            ToolIconButton(icon = Icons.Filled.OpenInFull) {
                safeRun {
                    CropTransform.recycleIfNotProtected(workingBitmap, sourceBitmap)
                    workingBitmap = sourceBitmap
                    naturalW = sourceBitmap.width
                    naturalH = sourceBitmap.height
                    cropState.reset()
                    transformState.reset()
                    geometryChanged = false
                }
            }
            ToolIconButton(
                icon = Icons.Filled.Lock,
                active = cropState.lockedAspect != null
            ) {
                cropState.toggleLockCurrentAspect()
            }
            ToolIconButton(
                icon = Icons.Filled.RadioButtonUnchecked,
                active = transformState.isOval
            ) { transformState.toggleOval() }
        }

        // Flip / Cancel / Confirm — icon-only, white row (matches reference)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .padding(vertical = 4.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIconButton(icon = Icons.Filled.Flip, active = transformState.flippedH) {
                safeRun {
                    val old = workingBitmap
                    workingBitmap = CropTransform.flipHorizontal(old)
                    CropTransform.recycleIfNotProtected(old, sourceBitmap)
                    transformState.toggleFlipH()
                    geometryChanged = true
                }
            }
            ToolIconButton(
                icon = Icons.Filled.Flip,
                active = transformState.flippedV,
                rotationDegrees = 90f
            ) {
                safeRun {
                    val old = workingBitmap
                    workingBitmap = CropTransform.flipVertical(old)
                    CropTransform.recycleIfNotProtected(old, sourceBitmap)
                    transformState.toggleFlipV()
                    geometryChanged = true
                }
            }
            ToolIconButton(icon = Icons.Filled.Close, tint = Color(0xFFE05353), onClick = onCancel)
            ToolIconButton(
                icon = Icons.Filled.Check,
                tint = Amber,
                onClick = {
                    safeRun {
                        val cl = cropState.left.roundToInt()
                        val ct = cropState.top.roundToInt()
                        val result = CropTransform.cropAndMask(
                            source = workingBitmap,
                            left = cl,
                            top = ct,
                        width = cropState.width.roundToInt(),
                        height = cropState.height.roundToInt(),
                        oval = transformState.isOval
                    )
                    // The full-size working canvas (post rotate/flip) isn't
                    // needed anymore now that we've cropped out of it — only
                    // `result` gets kept.
                    CropTransform.recycleIfNotProtected(workingBitmap, sourceBitmap)
                    onApply(result, cl, ct, geometryChanged)
                    }
                }
            )
        }
        }
        }
    }
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    active: Boolean = false,
    tint: Color = Color(0xFF2B2B2B),
    rotationDegrees: Float = 0f,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) Amber else tint,
        modifier = Modifier
            .size(30.dp)
            .rotate(rotationDegrees)
            .clickable(onClick = onClick)
    )
}
