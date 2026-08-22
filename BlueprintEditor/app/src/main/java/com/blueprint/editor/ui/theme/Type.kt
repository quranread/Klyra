package com.blueprint.editor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO (Part 7 - UI polish): bundle the actual Space Grotesk + JetBrains Mono
// font files under res/font and swap these FontFamily.Default references so
// the app matches the web version's typography exactly.
val AppFontFamily = FontFamily.Default
val MonoFontFamily = FontFamily.Monospace

val AppTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.02.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        letterSpacing = 0.01.sp
    )
)
