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
 * Reads dimensions + decodes a full bitmap for the given content [uri].
 * Mirrors `loadImage(src)`'s use of `tempImg.naturalWidth/Height` — done via
 * `inJustDecodeBounds` first so we never hold two full decodes in memory.
 */
fun loadImageFromUri(context: Context, uri: Uri): LoadedImage? {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: return null

    val name = queryDisplayName(context, uri) ?: (uri.lastPathSegment ?: "image")

    return LoadedImage(
        uri = uri,
        filename = name,
        width = bounds.outWidth,
        height = bounds.outHeight,
        bitmap = bitmap.asImageBitmap()
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
}
