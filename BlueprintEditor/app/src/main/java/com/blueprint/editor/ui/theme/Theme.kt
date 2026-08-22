package com.blueprint.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BlueprintDarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Cyan,
    onSecondary = OnCyan,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgPanel,
    onSurface = TextPrimary,
    surfaceVariant = BgPanel2,
    onSurfaceVariant = TextMuted,
    error = Danger,
    outline = LineGridStrong
)

@Composable
fun BlueprintEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueprintDarkScheme,
        typography = AppTypography,
        content = content
    )
}
