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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blueprint.editor.data.loadImageFromUri
import com.blueprint.editor.ui.crop.ProCropDialog
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.BgPanel
import com.blueprint.editor.ui.theme.OnAmber
import com.blueprint.editor.ui.theme.TextMuted

@Composable
fun PixelLabScreen(onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = loadImageFromUri(context, uri) ?: return@rememberLauncherForActivityResult
        bitmap = loaded.bitmap
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PixelLab") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPanel)
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
                FloatingActionButton(
                    onClick = { showCrop = true },
                    containerColor = Amber,
                    contentColor = OnAmber,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
                ) {
                    Icon(Icons.Filled.Crop, contentDescription = "Crop")
                }
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
