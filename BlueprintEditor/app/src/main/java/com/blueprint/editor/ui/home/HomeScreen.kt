package com.blueprint.editor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blueprint.editor.ui.theme.Amber
import com.blueprint.editor.ui.theme.BgDeep
import com.blueprint.editor.ui.theme.BgPanel2
import com.blueprint.editor.ui.theme.Cyan
import com.blueprint.editor.ui.theme.TextMuted
import com.blueprint.editor.ui.theme.TextPrimary

/**
 * The app's landing screen — a simple picker between the two tools living in
 * this repo/app right now (Blueprint Editor and PixelLab). Deliberately dumb:
 * no shared state, no dependency on either tool's ViewModel. When these get
 * split into separate apps later, each tool's folder just moves out —
 * nothing here needs untangling.
 */
@Composable
fun HomeScreen(
    onOpenBlueprintEditor: () -> Unit,
    onOpenPixelLab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Klyra",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Choose a tool",
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        HomeCard(
            title = "Blueprint Editor",
            subtitle = "Dots, lines, and boxes mapped to exact pixel coordinates",
            icon = Icons.Filled.Architecture,
            accent = Amber,
            onClick = onOpenBlueprintEditor
        )

        Spacer(Modifier.height(16.dp))

        HomeCard(
            title = "PixelLab Editor",
            subtitle = "Crop, rotate, flip and more — general image editing",
            icon = Icons.Filled.Image,
            accent = Cyan,
            onClick = onOpenPixelLab
        )
    }
}

@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgPanel2)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
