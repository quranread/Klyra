package com.blueprint.editor.ui.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Bitmap-level transform helpers for the Pro Crop tool: rotate, flip, and the
 * final oval-masked crop. Compose's ImageBitmap has no native rotate/flip/mask
 * support, so these drop down to android.graphics.Bitmap/Canvas/Matrix and
 * convert back — the same approach the rest of the app already uses wherever
 * it touches raw pixels.
 */
object CropTransform {

    /** Rotates 90° clockwise. Width/height swap, exactly like a real photo rotate. */
    fun rotate90(source: ImageBitmap): ImageBitmap {
        val src = source.asAndroidBitmap()
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        return rotated.asImageBitmap()
    }

    fun flipHorizontal(source: ImageBitmap): ImageBitmap {
        val src = source.asAndroidBitmap()
        val matrix = Matrix().apply { postScale(-1f, 1f, src.width / 2f, src.height / 2f) }
        val flipped = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        return flipped.asImageBitmap()
    }

    fun flipVertical(source: ImageBitmap): ImageBitmap {
        val src = source.asAndroidBitmap()
        val matrix = Matrix().apply { postScale(1f, -1f, src.width / 2f, src.height / 2f) }
        val flipped = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        return flipped.asImageBitmap()
    }

    /**
     * Crops [source] to the natural-pixel rect (left, top, width, height).
     * If [oval] is true, the result also gets an oval alpha mask baked in
     * (transparent corners) so the caller gets a ready-to-use cutout.
     */
    fun cropAndMask(
        source: ImageBitmap,
        left: Int, top: Int, width: Int, height: Int,
        oval: Boolean
    ): ImageBitmap {
        val src = source.asAndroidBitmap()
        val safeLeft = left.coerceIn(0, (src.width - 1).coerceAtLeast(0))
        val safeTop = top.coerceIn(0, (src.height - 1).coerceAtLeast(0))
        val safeW = width.coerceIn(1, src.width - safeLeft)
        val safeH = height.coerceIn(1, src.height - safeTop)

        val cropped = Bitmap.createBitmap(src, safeLeft, safeTop, safeW, safeH)
        if (!oval) return cropped.asImageBitmap()

        val output = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawOval(RectF(0f, 0f, safeW.toFloat(), safeH.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        cropped.recycle()
        return output.asImageBitmap()
    }

    /**
     * Frees the Android bitmap behind [bitmap], unless it's the same object
     * as [protect] (typically the original source bitmap, which the caller
     * still owns and may need again — e.g. Reset, or if the user cancels).
     * Every rotate/flip/crop here allocates a brand-new full-resolution
     * bitmap; without this, repeated rotates/flips pile up tens of MB each
     * and eventually crash the app with an OutOfMemoryError.
     */
    fun recycleIfNotProtected(bitmap: ImageBitmap, protect: ImageBitmap) {
        if (bitmap === protect) return
        try {
            val android = bitmap.asAndroidBitmap()
            if (!android.isRecycled) android.recycle()
        } catch (_: Throwable) {
            // Never let cleanup itself crash the app.
        }
    }
}

