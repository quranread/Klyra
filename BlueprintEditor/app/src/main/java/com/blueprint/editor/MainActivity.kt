package com.blueprint.editor

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.BlueprintViewModel
import com.blueprint.editor.data.DrawMode
import com.blueprint.editor.data.loadImageFromUri
import com.blueprint.editor.export.buildAiInstructions
import com.blueprint.editor.export.buildAnnotatedBitmap
import com.blueprint.editor.export.buildBlueprintJson
import com.blueprint.editor.ui.canvas.BlueprintCanvas
import com.blueprint.editor.ui.canvas.CanvasTransformState
import com.blueprint.editor.ui.crop.ProCropDialog
import com.blueprint.editor.ui.home.HomeScreen
import com.blueprint.editor.pixellab.PixelLabScreen
import com.blueprint.editor.ui.components.AiInstructionsSheet
import com.blueprint.editor.ui.components.EditSheet
import com.blueprint.editor.ui.components.ElementsListSheet
import com.blueprint.editor.ui.components.ImageInfoSheet
import com.blueprint.editor.ui.components.ModeHintBar
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BlueprintEditorTheme
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.TextMuted
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private val viewModel: BlueprintViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueprintEditorTheme {
                Surface {
                    // Landing screen picks between the two tools in this app.
                    // Kept as simple local nav state (no navigation library)
                    // since there are only three destinations.
                    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                    when (currentScreen) {
                        AppScreen.HOME -> HomeScreen(
                            onOpenBlueprintEditor = { currentScreen = AppScreen.BLUEPRINT_EDITOR },
                            onOpenPixelLab = { currentScreen = AppScreen.PIXELLAB }
                        )
                        AppScreen.BLUEPRINT_EDITOR -> EditorScreen(
                            viewModel,
                            onHome = { currentScreen = AppScreen.HOME }
                        )
                        AppScreen.PIXELLAB -> PixelLabScreen(
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}

private enum class AppScreen { HOME, BLUEPRINT_EDITOR, PIXELLAB }

@Composable
private fun EditorScreen(viewModel: BlueprintViewModel, onHome: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val transform = remember { CanvasTransformState() }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var showList by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var showImageInfo by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) } // awaiting wipe-confirmation
    var showCrop by remember { mutableStateOf(false) }
    var showRulers by remember { mutableStateOf(true) }
    var showGrid by remember { mutableStateOf(false) }
    // Moved up from inside the canvas Box so the new docked ZoomControlsBar
    // (in bottomBar, a separate lambda/scope from the canvas content) can
    // also trigger a re-fit via the same onFit callback.
    var hasFit by remember(bitmap) { mutableStateOf(false) }
    var lastMeasuredSize by remember(bitmap) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
    val context = LocalContext.current
    val jsonFormat = remember { Json { prettyPrint = true } }

    fun applyLoadedImage(uri: Uri) {
        val loaded = loadImageFromUri(context, uri) ?: return
        bitmap = loaded.bitmap
        viewModel.loadImage(loaded.uri.toString(), loaded.filename, loaded.width, loaded.height)
        // Don't fit here — canvasSize may be stale (zero on first launch, or
        // left over from a previous image) since it's only updated from
        // BlueprintCanvas's own onContainerSizeChanged below. The real fit
        // happens once that callback reports a settled container size.
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Don't silently wipe existing work — same bug the web version had
        // until a user caught it via re-selecting the same file with no
        // warning. Ask first whenever there's something to lose.
        if (viewModel.hasElements) {
            pendingImageUri = uri
        } else {
            applyLoadedImage(uri)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val doc = buildBlueprintJson(viewModel.filename, viewModel.naturalW, viewModel.naturalH, viewModel.elements)
            val text = jsonFormat.encodeToString(doc)
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val currentBitmap = bitmap
        if (uri != null && currentBitmap != null) {
            val annotated: Bitmap = buildAnnotatedBitmap(
                currentBitmap, viewModel.naturalW, viewModel.naturalH, viewModel.elements
            )
            context.contentResolver.openOutputStream(uri)?.use {
                annotated.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    fun baseName(): String = viewModel.filename.substringBeforeLast('.').ifBlank { "blueprint" }

    Scaffold(
        topBar = {
            EditorTopBar(
                viewModel = viewModel,
                hasImage = bitmap != null,
                onPickImage = { pickImage.launch("image/*") },
                onOpenList = { showList = true },
                onOpenInstructions = { showInstructions = true },
                onOpenImageInfo = { showImageInfo = true },
                onExportJson = { exportJsonLauncher.launch("${baseName()}_blueprint.json") },
                onExportPng = { exportPngLauncher.launch("${baseName()}_annotated.png") },
                onClearAll = { showClearConfirm = true },
                onCrop = { showCrop = true },
                onHome = onHome
            )
        },
        bottomBar = {
            if (bitmap != null) {
                // FIX: the whole group now gets navigation-bar clearance ONCE,
                // at this outer Column — not from NavigationBar's own internal
                // inset handling, which only protected itself and let
                // ZoomControlsBar (a plain Row below it, with no inset
                // awareness of its own) get pushed down into the system
                // gesture-navigation strip. ToolBar is now the LAST/bottom-most
                // element again (matches the original, familiar layout), with
                // ZoomControlsBar docked just above it.
                Column(modifier = Modifier.navigationBarsPadding()) {
                    ZoomControlsBar(
                        transform = transform,
                        containerSize = canvasSize,
                        onFit = { hasFit = false; lastMeasuredSize = null },
                        showRulers = showRulers,
                        onToggleRulers = { showRulers = !showRulers },
                        showGrid = showGrid,
                        onToggleGrid = { showGrid = !showGrid }
                    )
                    ToolBar(viewModel, onCropClick = { showCrop = true })
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (bitmap != null) {
                ModeHintBar(
                    drawMode = viewModel.drawMode,
                    hasPendingLineStart = viewModel.pendingLineStart != null,
                    hasPendingBoxStart = viewModel.pendingBoxStart != null
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val currentBitmap = bitmap
            if (currentBitmap == null) {
                EmptyState(onPickImage = { pickImage.launch("image/*") })
            } else {
                BlueprintCanvas(
                    viewModel = viewModel,
                    bitmap = currentBitmap,
                    transform = transform,
                    modifier = Modifier.fillMaxSize(),
                    showRulers = showRulers,
                    showGrid = showGrid,
                    onContainerSizeChanged = { size ->
                        canvasSize = size
                        // Fit-to-container right after a fresh image loads — mirrors
                        // the original calling setZoom('fit') immediately after
                        // loadImage(). With edge-to-edge enabled, the first reported
                        // size can land before window insets finish settling, so we
                        // keep re-fitting on every size change and only lock once two
                        // consecutive measurements agree — that's what "settled" means
                        // here. This now reads from onContainerSizeChanged (the inner
                        // canvas box's own size, excluding the ruler strips) rather
                        // than a raw Modifier.onSizeChanged on the whole composable —
                        // that outer size would include the ~22dp ruler strips once
                        // showRulers is on, which very slightly overshoots the actual
                        // drawable area and clips the fitted image against the rulers.
                        if (!hasFit && viewModel.naturalW > 0 && viewModel.naturalH > 0 &&
                            size.width > 0 && size.height > 0
                        ) {
                            if (size == lastMeasuredSize) {
                                hasFit = true
                            } else {
                                lastMeasuredSize = size
                                transform.fitToContainer(size.width, size.height, viewModel.naturalW, viewModel.naturalH)
                            }
                        }
                    }
                )
            }

            val selectedElement = viewModel.elementById(viewModel.selectedId)
            if (selectedElement != null) {
                EditSheet(
                    element = selectedElement,
                    naturalW = viewModel.naturalW,
                    naturalH = viewModel.naturalH,
                    onUpdate = { viewModel.updateElement(it) },
                    onDelete = {
                        viewModel.deleteElement(selectedElement.id)
                        viewModel.closeSelection()
                    },
                    onDismiss = { viewModel.closeSelection() }
                )
            }

            if (showList) {
                ElementsListSheet(
                    elements = viewModel.elements,
                    selectedId = viewModel.selectedId,
                    naturalW = viewModel.naturalW,
                    naturalH = viewModel.naturalH,
                    onSelect = { id -> viewModel.openElement(id); showList = false },
                    onDelete = { id -> viewModel.deleteElement(id) },
                    onDismiss = { showList = false }
                )
            }

            if (showInstructions) {
                AiInstructionsSheet(
                    text = buildAiInstructions(viewModel.filename, viewModel.naturalW, viewModel.naturalH, viewModel.elements),
                    onDismiss = { showInstructions = false }
                )
            }

            if (showImageInfo) {
                ImageInfoSheet(
                    filename = viewModel.filename,
                    naturalW = viewModel.naturalW,
                    naturalH = viewModel.naturalH,
                    zoomPercent = transform.zoomPercent,
                    elementCount = viewModel.elements.size,
                    onDismiss = { showImageInfo = false }
                )
            }
            }
        }
    }

    // "Continue with new upload?" — only shown when there's existing work to lose.
    pendingImageUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { Text("Replace current image?") },
            text = {
                Text(
                    "Aapke paas is image ke ${viewModel.elements.size} dots/lines already mapped hain.\n\n" +
                        "Nayi image upload karne se ye sab clear ho jayenge. Agar purana data chahiye to pehle " +
                        "Export JSON ya Export Image kar lein."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    applyLoadedImage(uri)
                    pendingImageUri = null
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImageUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete all mapped elements?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearConfirm = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Pro Crop tool — its own floating Dialog window (see ProCropDialog.kt),
    // positioned per the exact reference measurements rather than taking the
    // full screen like the old CropScreen did.
    if (showCrop) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            ProCropDialog(
                sourceBitmap = currentBitmap,
                onDismiss = { showCrop = false },
                onApply = { result, cropLeft, cropTop, geometryChanged ->
                    bitmap = result
                    if (geometryChanged) {
                        // Rotate/flip was used at least once — old dot/line
                        // coordinates no longer map onto this image, so start
                        // this image's mapping fresh instead of corrupting them.
                        viewModel.replaceImageGeometry(result.width, result.height)
                    } else {
                        // Plain crop only — safe to re-anchor existing elements.
                        viewModel.applyCrop(cropLeft, cropTop, result.width, result.height)
                    }
                    showCrop = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    viewModel: BlueprintViewModel,
    hasImage: Boolean,
    onPickImage: () -> Unit,
    onOpenList: () -> Unit,
    onOpenInstructions: () -> Unit,
    onOpenImageInfo: () -> Unit,
    onExportJson: () -> Unit,
    onExportPng: () -> Unit,
    onClearAll: () -> Unit,
    onCrop: () -> Unit,
    onHome: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text("Blueprint Editor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (viewModel.filename.isNotEmpty()) {
                    Text(viewModel.filename, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = com.blueprint.editor.ui.theme.BgPanel,
            titleContentColor = com.blueprint.editor.ui.theme.TextPrimary
        ),
        actions = {
            IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            if (viewModel.hasElements) {
                BadgedBox(badge = { Badge { Text(viewModel.elements.size.toString()) } }) {
                    IconButton(onClick = onOpenList) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Elements list")
                    }
                }
            } else {
                IconButton(onClick = onOpenList, enabled = false) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Elements list")
                }
            }
            IconButton(onClick = onPickImage) {
                Icon(Icons.Filled.Image, contentDescription = "Open image")
            }
            if (hasImage) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Export JSON") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        enabled = viewModel.hasElements,
                        onClick = { menuExpanded = false; onExportJson() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Annotated Image") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        enabled = viewModel.hasElements,
                        onClick = { menuExpanded = false; onExportPng() }
                    )
                    DropdownMenuItem(
                        text = { Text("AI Instructions") },
                        leadingIcon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpenInstructions() }
                    )
                    DropdownMenuItem(
                        text = { Text("Image Info") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpenImageInfo() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Crop Image") },
                        leadingIcon = { Icon(Icons.Filled.Crop, contentDescription = null) },
                        onClick = { menuExpanded = false; onCrop() }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear All") },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                        enabled = viewModel.hasElements,
                        onClick = { menuExpanded = false; onClearAll() }
                    )
                }
            }
        }
    )
}

@Composable
private fun ToolBar(viewModel: BlueprintViewModel, onCropClick: () -> Unit) {
    // windowInsets = WindowInsets(0) here on purpose: the outer bottomBar
    // Column (in EditorScreen) now applies .navigationBarsPadding() ONCE for
    // the whole ZoomControlsBar + ToolBar group. If NavigationBar also
    // applied its own default bottom inset on top of that, the group would
    // get double bottom padding (an odd empty gap above the system bar)
    // instead of sitting flush against it like before.
    NavigationBar(
        containerColor = com.blueprint.editor.ui.theme.BgPanel3,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        ToolBarItem(Icons.Filled.RadioButtonChecked, "Dot", viewModel.drawMode == DrawMode.DOT) { viewModel.selectDrawMode(DrawMode.DOT) }
        ToolBarItem(Icons.AutoMirrored.Filled.TrendingFlat, "Line", viewModel.drawMode == DrawMode.LINE) { viewModel.selectDrawMode(DrawMode.LINE) }
        ToolBarItem(Icons.Filled.CropSquare, "Box", viewModel.drawMode == DrawMode.BOX) { viewModel.selectDrawMode(DrawMode.BOX) }
        ToolBarItem(Icons.Filled.PanTool, "Pan", viewModel.drawMode == DrawMode.PAN) { viewModel.selectDrawMode(DrawMode.PAN) }
        // Opens the Pro Crop dialog directly from the toolbar — doesn't
        // change drawMode, so it's never "selected" like the others.
        ToolBarItem(Icons.Filled.Crop, "Crop", selected = false, onClick = onCropClick)
    }
}

@Composable
private fun RowScope.ToolBarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(selectedIconColor = Amber, indicatorColor = Amber.copy(alpha = 0.18f))
    )
}

@Composable
private fun ZoomControlsBar(
    transform: CanvasTransformState,
    containerSize: androidx.compose.ui.geometry.Size,
    onFit: () -> Unit,
    showRulers: Boolean,
    onToggleRulers: () -> Unit,
    showGrid: Boolean,
    onToggleGrid: () -> Unit
) {
    // Docked in the bottomBar (below the Dot/Line/Box/Pan/Crop row) instead
    // of floating over the canvas — floating meant it stayed pinned to the
    // bottom-right of the *viewport* while the image scrolled underneath it,
    // so it ended up covering whatever content happened to scroll there.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { transform.zoomStep(1.25f, containerSize.width, containerSize.height) }) { Icon(Icons.Filled.Add, null) }
        Text("${transform.zoomPercent}%", style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = { transform.zoomStep(1 / 1.25f, containerSize.width, containerSize.height) }) { Icon(Icons.Filled.Remove, null) }
        IconButton(onClick = onFit) { Icon(Icons.Filled.FitScreen, null) }
        IconButton(onClick = { transform.toggleLock() }) {
            Icon(
                if (transform.zoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                null,
                tint = if (transform.zoomLocked) Cyan else LocalContentColor.current
            )
        }
        VerticalDivider(modifier = Modifier.padding(horizontal = 2.dp).height(24.dp))
        IconButton(onClick = onToggleRulers) {
            Icon(Icons.Filled.Straighten, null, tint = if (showRulers) Amber else LocalContentColor.current)
        }
        IconButton(onClick = onToggleGrid) {
            Icon(Icons.Filled.GridOn, null, tint = if (showGrid) Amber else LocalContentColor.current)
        }
    }
}

@Composable
private fun EmptyState(onPickImage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Amber.copy(alpha = 0.12f), shape = MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.GridOn, contentDescription = null, tint = Amber, modifier = Modifier.size(32.dp))
            }
            Text(
                "Upload a screenshot to begin mapping",
                style = MaterialTheme.typography.titleLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Original resolution locks the moment it's uploaded. Every coordinate you record stays anchored to those original pixels — zoom never changes it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onPickImage, modifier = Modifier.padding(top = 6.dp)) {
                Text("\u2B06  Upload Image")
            }
            Text(
                "v1.2",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** Full edit sheet lives in ui/components/EditSheet.kt. */
