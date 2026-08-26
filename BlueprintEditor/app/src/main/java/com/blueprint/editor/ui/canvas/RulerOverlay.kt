package com.blueprint.editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueprint.editor.ui.theme.BgPanel
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.TextMuted
import kotlin.math.ceil
import kotlin.math.floor

val RULER_SIZE = 22.dp
private val NICE_STEPS = intArrayOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000, 10000)
private const val MIN_LABEL_SPACING_DP = 46f

/** Picks the smallest "nice" pixel step whose on-screen spacing is >= [minPxSpacingScreen]. */
private fun niceStep(minPxSpacingScreen: Float, scale: Float): Int {
    val minNaturalStep = minPxSpacingScreen / scale
    return NICE_STEPS.firstOrNull { it >= minNaturalStep } ?: NICE_STEPS.last()
}

/**
 * Top horizontal ruler — pixel tick marks along X, spacing adapts to zoom so
 * labels never overlap. Matches Figma/Photoshop-style rulers so the person
 * can read off an approximate pixel position without needing to tap first.
 */
@Composable
fun TopRuler(
    naturalW: Int,
    scale: Float,
    panX: Float,
    liveNaturalX: Int?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val minSpacingPx = with(density) { MIN_LABEL_SPACING_DP.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_SIZE)
            .background(BgPanel)
    ) {
        val step = niceStep(minSpacingPx, scale)
        val firstVisibleNatural = floor((-panX / scale) / step).toInt() * step
        val lastVisibleNatural = ceil(((size.width - panX) / scale) / step).toInt() * step

        var x = firstVisibleNatural
        while (x <= lastVisibleNatural) {
            if (x in 0..naturalW) {
                val screenX = x * scale + panX
                drawLine(TextMuted, Offset(screenX, size.height - 8f), Offset(screenX, size.height), strokeWidth = 1f)
                val layout = textMeasurer.measure(x.toString(), TextStyle(color = TextMuted, fontSize = 9.sp))
                drawText(layout, topLeft = Offset(screenX + 3f, 2f))
            }
            x += step
        }

        liveNaturalX?.let { lx ->
            val screenX = lx * scale + panX
            drawLine(Cyan, Offset(screenX, 0f), Offset(screenX, size.height), strokeWidth = 1.5f)
        }
    }
}

/** Left vertical ruler — pixel tick marks along Y, same adaptive spacing as [TopRuler]. */
@Composable
fun LeftRuler(
    naturalH: Int,
    scale: Float,
    panY: Float,
    liveNaturalY: Int?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val minSpacingPx = with(density) { MIN_LABEL_SPACING_DP.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(RULER_SIZE)
            .background(BgPanel)
    ) {
        val step = niceStep(minSpacingPx, scale)
        val firstVisibleNatural = floor((-panY / scale) / step).toInt() * step
        val lastVisibleNatural = ceil(((size.height - panY) / scale) / step).toInt() * step

        var y = firstVisibleNatural
        while (y <= lastVisibleNatural) {
            if (y in 0..naturalH) {
                val screenY = y * scale + panY
                drawLine(TextMuted, Offset(size.width - 8f, screenY), Offset(size.width, screenY), strokeWidth = 1f)
                val layout = textMeasurer.measure(y.toString(), TextStyle(color = TextMuted, fontSize = 9.sp))
                rotate(degrees = -90f, pivot = Offset(2f, screenY)) {
                    drawText(layout, topLeft = Offset(2f, screenY - 8f))
                }
            }
            y += step
        }

        liveNaturalY?.let { ly ->
            val screenY = ly * scale + panY
            drawLine(Cyan, Offset(0f, screenY), Offset(size.width, screenY), strokeWidth = 1.5f)
        }
    }
}

/** Small blank corner square where the two rulers meet. */
@Composable
fun RulerCorner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(RULER_SIZE).height(RULER_SIZE).background(BgPanel))
}

/**
 * Full grid lines across the visible image, at the same adaptive step as the
 * rulers, so the eye can line things up without needing the rulers' edge
 * labels. Toggleable — off by default to keep the canvas clean.
 */
fun DrawScope.drawGridOverlay(naturalW: Int, naturalH: Int, scale: Float, panX: Float, panY: Float) {
    val step = niceStep(60f, scale) // slightly sparser than the ruler's own labels, to avoid visual noise
    val gridColor = Color.White.copy(alpha = 0.07f)

    var x = 0
    while (x <= naturalW) {
        val screenX = x * scale + panX
        drawLine(gridColor, Offset(screenX, panY), Offset(screenX, naturalH * scale + panY), strokeWidth = 1f)
        x += step
    }
    var y = 0
    while (y <= naturalH) {
        val screenY = y * scale + panY
        drawLine(gridColor, Offset(panX, screenY), Offset(naturalW * scale + panX, screenY), strokeWidth = 1f)
        y += step
    }
}
