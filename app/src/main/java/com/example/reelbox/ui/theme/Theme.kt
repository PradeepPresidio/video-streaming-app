package com.example.reelbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RbBackground = Color(0xFF0B0D12)
val RbSurface = Color(0xFF161A22)
val RbSurfaceHigh = Color(0xFF1F2430)
val RbAccent = Color(0xFFFF8A3D)
val RbKidsAccent = Color(0xFF3DD6FF)
val RbOnSurfaceMuted = Color(0xFFA7ADBA)

@Composable
fun ReelBoxTheme(accent: Color = RbAccent, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = RbBackground,
        secondary = accent,
        background = RbBackground,
        onBackground = Color.White,
        surface = RbBackground,
        onSurface = Color.White,
        surfaceVariant = RbSurface,
        onSurfaceVariant = RbOnSurfaceMuted,
        surfaceContainer = RbSurface,
        surfaceContainerHigh = RbSurfaceHigh,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
