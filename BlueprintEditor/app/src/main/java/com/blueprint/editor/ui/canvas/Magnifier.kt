package com.blueprint.editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueprint.editor.data.NaturalPoint
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.LineGridStrong
import kotlin.math.roundToInt

private val MAG_DIAM = 132.dp
private const val MAG_FACTOR = 2.6f

/**
 * Circular zoomed preview shown while a finger is dragging to place a
 * dot/line/box point — lets the user see exactly where they'll land under
 * their fingertip. Direct port of `.magnifier` + `showMagnifier()`.
 *
 * [anchorLocal] is the finger's current position in the canvas wrap's local
 * (untransformed) coordinates. [point] is the same position in natural
 * (original-image) pixels. [containerSizePx] is the canvas wrap's own size,
 * used to clamp the magnifier on-screen (a stand-in for the original's
 * window-fixed positioning, since the wrap already fills most of the screen).
 */
@Composable
fun MagnifierOverlay(
    bitmap: ImageBitmap,
    naturalW: Int,
    naturalH: Int,
    scale: Float,
    anchorLocal: Offset,
    point: NaturalPoint,
    containerSizePx: Size
) {
    val density = LocalDensity.current
    val diamPx = with(density) { MAG_DIAM.toPx() }
    val radiusPx = diamPx / 2f
    val edgePad = with(density) { 8.dp.toPx() }

    var left = anchorLocal.x - radiusPx
    var top = anchorLocal.y - radiusPx * 2 - with(density) { 24.dp.toPx() }
    if (top < edgePad) top = anchorLocal.y + with(density) { 32.dp.toPx() }
    left = left.coerceIn(edgePad, (containerSizePx.width - diamPx - edgePad).coerceAtLeast(edgePad))

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(MAG_DIAM)
            .clip(CircleShape)
            .background(BgDeep)
            .border(3.dp, Cyan, CircleShape)
    ) {
        val effScale = scale * MAG_FACTOR
        Canvas(modifier = Modifier.size(MAG_DIAM)) {
            val circlePath = Path().apply { addOval(Rect(Offset.Zero, size)) }
            clipPath(circlePath) {
                val dstW = naturalW * effScale
                val dstH = naturalH * effScale
                val dx = radiusPx - point.x * effScale
                val dy = radiusPx - point.y * effScale
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                    dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1))
                )
            }
            drawLine(Amber, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1f)
            drawLine(Amber, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f)
            drawCircle(Amber, radius = 3f, center = Offset(size.width / 2f, size.height / 2f))
        }
    }

    val coordTop = top + diamPx + with(density) { 6.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), coordTop.roundToInt()) }
            .background(BgDeep.copy(alpha = 0.95f))
            .border(1.dp, LineGridStrong)
    ) {
        Text(
            text = "X: ${point.x}  Y: ${point.y}",
            color = Amber,
            fontSize = 11.sp,
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
