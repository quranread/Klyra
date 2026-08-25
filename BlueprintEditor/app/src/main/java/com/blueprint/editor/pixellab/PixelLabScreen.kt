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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ColorLens
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.loadImageFromUri
import com.blueprint.editor.ui.crop.ProCropDialog
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.OnAmber
import com.blueprint.editor.ui.theme.TextMuted
import kotlin.math.cos
import kotlin.math.sin

/** Matches the reference toolbar's blue. */
private val ToolbarBlue = Color(0xFF4A7FE0)

/** The 5 tool categories along the bottom bar (reference screenshot). */
private enum class ToolCategory { COLOR, TEXT, FILTER, LAYERS, MAGIC }

// Reference measurements (Blueprint Editor, 1080x2321 screenshot), expressed
// as fractions of total screen height so they hold on any device:
// top bar 253px, presets placeholder strip 297px, bottom icon row 113px.
private const val TOPBAR_HEIGHT_FRACTION = 253f / 2321f
private const val PRESETS_STRIP_HEIGHT_FRACTION = 297f / 2321f
private const val ICON_ROW_HEIGHT_FRACTION = 113f / 2321f

@Composable
fun PixelLabScreen(onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    // Filters is selected by default, matching the reference screenshot.
    var selectedCategory by remember { mutableStateOf(ToolCategory.FILTER) }
    val context = LocalContext.current

    // No visible back arrow in the reference bar, so the system/gesture back
    // button is what returns to Home instead.
    BackHandler(onBack = onBack)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = loadImageFromUri(context, uri) ?: return@rememberLauncherForActivityResult
        bitmap = loaded.bitmap
    }

    // BoxWithConstraints instead of LocalConfiguration.screenHeightDp — the
    // latter doesn't reliably account for the status/navigation bar insets
    // this edge-to-edge app draws under, so the computed heights were
    // slightly off from the reference measurements. This measures the actual
    // Compose layout space, matching what a raw screenshot captures.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val topBarHeight = screenHeight * TOPBAR_HEIGHT_FRACTION
        val presetsStripHeight = screenHeight * PRESETS_STRIP_HEIGHT_FRACTION
        val iconRowHeight = screenHeight * ICON_ROW_HEIGHT_FRACTION

        Scaffold(
        topBar = {
            PixelLabTopBar(
                height = topBarHeight,
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
        },
        bottomBar = {
            // Just the placement for now, per your note — the preset strip's
            // actual content and each category's real behaviour come later.
            PixelLabBottomBar(
                presetsStripHeight = presetsStripHeight,
                iconRowHeight = iconRowHeight,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
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
}

/**
 * Two-row toolbar matching the reference screenshot: add / save / share /
 * quote / overflow on top, then a floating edit+delete pill on the left with
 * undo / zoom / grid / layers alongside it below. [height] is the exact
 * measured height (253px on a 2321px-tall reference screen) so the two
 * internal rows just split it evenly rather than each having its own
 * independent padding.
 */
@Composable
private fun PixelLabTopBar(
    height: Dp,
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
            .height(height)
            .background(ToolbarBlue)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
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
                .weight(1f)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TopBarIcon(Icons.Filled.Edit, onEdit, size = 20.dp)
                TopBarIcon(Icons.Filled.Delete, onDelete, size = 20.dp)
            }
            TopBarIcon(Icons.AutoMirrored.Filled.Undo, onUndo, size = 22.dp)
            TopBarIcon(Icons.Filled.ZoomIn, onZoom, size = 22.dp)
            TopBarIcon(Icons.Filled.GridOn, onGrid, size = 22.dp)
            TopBarIcon(Icons.Filled.Layers, onLayers, size = 22.dp)
        }
    }
}

@Composable
private fun TopBarIcon(icon: ImageVector, onClick: () -> Unit, size: Dp = 24.dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
    )
}

/**
 * Bottom tool-category bar matching the reference screenshot: an (empty for
 * now) preset/preview strip above 5 category icons — color, text, filters
 * (hexagon), layers, and magic/auto-enhance. [presetsStripHeight] (297px ref)
 * and [iconRowHeight] (113px ref) are the exact measured heights.
 */
@Composable
private fun PixelLabBottomBar(
    presetsStripHeight: Dp,
    iconRowHeight: Dp,
    selected: ToolCategory,
    onSelect: (ToolCategory) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(presetsStripHeight)
                .background(Color(0xFFECECEC)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Presets — coming soon",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(iconRowHeight)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(Icons.Filled.ColorLens, selected == ToolCategory.COLOR) { onSelect(ToolCategory.COLOR) }
            CategoryLetterIcon(selected == ToolCategory.TEXT) { onSelect(ToolCategory.TEXT) }
            CategoryHexagonIcon(selected == ToolCategory.FILTER) { onSelect(ToolCategory.FILTER) }
            CategoryIcon(Icons.Filled.Layers, selected == ToolCategory.LAYERS) { onSelect(ToolCategory.LAYERS) }
            CategoryIcon(Icons.Filled.AutoFixHigh, selected == ToolCategory.MAGIC) { onSelect(ToolCategory.MAGIC) }
        }
    }
}

@Composable
private fun CategoryIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) Amber else Color(0xFF9A9A9A),
        modifier = Modifier
            .size(26.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryLetterIcon(active: Boolean, onClick: () -> Unit) {
    Text(
        "A",
        color = if (active) Amber else Color(0xFF9A9A9A),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryHexagonIcon(active: Boolean, onClick: () -> Unit) {
    val tint = if (active) Amber else Color(0xFF9A9A9A)
    Canvas(
        modifier = Modifier
            .size(26.dp)
            .clickable(onClick = onClick)
    ) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path().apply {
            for (i in 0..5) {
                val angle = Math.toRadians((60 * i - 90).toDouble())
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5f))
    }
}
