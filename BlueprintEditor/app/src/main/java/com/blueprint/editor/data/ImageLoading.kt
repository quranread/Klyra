package com.blueprint.editor.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Result of loading an image the user picked — everything the canvas + ViewModel need to start a session. */
data class LoadedImage(
    val uri: Uri,
    val filename: String,
    val width: Int,
    val height: Int,
    val bitmap: ImageBitmap
)

/**
 * Decodes the full bitmap for the given content [uri] and reads its dimensions
 * directly off that same bitmap. A separate `inJustDecodeBounds` pre-check was
 * removed on purpose: that pass and the real decode are two independent calls
 * into the platform decoder, and on some devices/formats they don't agree on
 * pixel dimensions (density-aware auto-scaling on the real decode is the usual
 * cause) — sizing the canvas off `bounds.outWidth/outHeight` while drawing a
 * bitmap that came out a different size is exactly what stretches the image.
 * Reading width/height off the actual decoded [android.graphics.Bitmap]
 * guarantees the two can never disagree.
 */
fun loadImageFromUri(context: Context, uri: Uri): LoadedImage? {
    val resolver = context.contentResolver

    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: return null

    val name = queryDisplayName(context, uri) ?: (uri.lastPathSegment ?: "image")

    return LoadedImage(
        uri = uri,
        filename = name,
        width = bitmap.width,
        height = bitmap.height,
        bitmap = bitmap.asImageBitmap()
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
}
