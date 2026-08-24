package com.blueprint.editor.pixellab

/**
 * PixelLab — the second tool in this repo/app, deliberately kept in its own
 * package with zero dependency on Blueprint Editor's ViewModel or state.
 * That's what makes "split this into its own app later" a folder move
 * instead of an untangling job.
 *
 * For now it reuses the already-built Pro Crop dialog as its first feature.
 * More tools (filters, adjustments, text, etc.) get added here the same way
 * Blueprint Editor's crop tool was: as their own self-contained pieces.
 */

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.loadImageFromUri
import com.blueprint.editor.ui.crop.ProCropDialog
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.OnAmber
import com.blueprint.editor.ui.theme.TextMuted

/** Matches the reference toolbar's blue. */
private val ToolbarBlue = Color(0xFF4A7FE0)

@Composable
fun PixelLabScreen(onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // No visible back arrow in the reference bar, so the system/gesture back
    // button is what returns to Home instead.
    BackHandler(onBack = onBack)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = loadImageFromUri(context, uri) ?: return@rememberLauncherForActivityResult
        bitmap = loaded.bitmap
    }

    Scaffold(
        topBar = {
            PixelLabTopBar(
                onAdd = { pickImage.launch("image/*") },
                onSave = { /* TODO: export/save current bitmap — needs a save destination decided */ },
                onShare = { /* TODO: Android share sheet — needs a FileProvider set up first */ },
                onQuote = { /* TODO: unclear purpose yet — confirm what this should do */ },
                onMore = { /* TODO: overflow menu — confirm what belongs in it */ },
                onEdit = { if (bitmap != null) showCrop = true },
                onDelete = { bitmap = null },
                onUndo = { /* TODO: no edit history yet in PixelLab */ },
                onZoom = { /* TODO: zoom control not implemented yet */ },
                onGrid = { /* TODO: grid overlay not implemented yet */ },
                onLayers = { /* TODO: layers panel not implemented yet */ }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgDeep),
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = bitmap
            if (currentBitmap == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No image loaded", color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { pickImage.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Image")
                    }
                }
            } else {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            }

            if (showCrop && currentBitmap != null) {
                ProCropDialog(
                    sourceBitmap = currentBitmap,
                    onDismiss = { showCrop = false },
                    onApply = { result, _, _, _ ->
                        bitmap = result
                        showCrop = false
                    }
                )
            }
        }
    }
}

/**
 * Two-row toolbar matching the reference screenshot exactly: add / save /
 * share / quote / overflow on top, then a floating edit+delete pill on the
 * left with undo / zoom / grid / layers alongside it below.
 */
@Composable
private fun PixelLabTopBar(
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onQuote: () -> Unit,
    onMore: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onZoom: () -> Unit,
    onGrid: () -> Unit,
    onLayers: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolbarBlue)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIcon(Icons.Filled.Add, onAdd)
            TopBarIcon(Icons.Filled.Save, onSave)
            TopBarIcon(Icons.Filled.Share, onShare)
            TopBarIcon(Icons.Filled.FormatQuote, onQuote)
            TopBarIcon(Icons.Filled.MoreVert, onMore)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TopBarIcon(Icons.Filled.Edit, onEdit, size = 22.dp)
                TopBarIcon(Icons.Filled.Delete, onDelete, size = 22.dp)
            }
            TopBarIcon(Icons.AutoMirrored.Filled.Undo, onUndo)
            TopBarIcon(Icons.Filled.ZoomIn, onZoom)
            TopBarIcon(Icons.Filled.GridOn, onGrid)
            TopBarIcon(Icons.Filled.Layers, onLayers)
        }
    }
}

@Composable
private fun TopBarIcon(icon: ImageVector, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
    )
}
