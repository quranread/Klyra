package com.blueprint.editor.data

import android.graphics.Bitmap

/**
 * Crops [source] to the natural-pixel rect [left],[top],[width],[height],
 * clamped safely inside the source's bounds so a slightly-out-of-range rect
 * (e.g. from rounding during a drag) never crashes `Bitmap.createBitmap`.
 */
fun cropBitmap(source: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
    val safeLeft = left.coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val safeTop = top.coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val safeWidth = width.coerceIn(1, source.width - safeLeft)
    val safeHeight = height.coerceIn(1, source.height - safeTop)
    return Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
}
