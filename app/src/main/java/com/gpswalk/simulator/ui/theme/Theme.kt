package com.gpswalk.simulator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StatusColors {
    val Ok = Color(0xFF4CAF50)
    val Warn = Color(0xFFFFB300)
    val Error = Color(0xFFEF5350)
    val Neutral = Color(0xFF90A4AE)
    val Active = Color(0xFF4FC3F7)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00222E),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE2E6EB),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFE2E6EB),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFFA9B4C0),
    surfaceContainer = Color(0xFF181E26),
    surfaceContainerHigh = Color(0xFF1E252E),
    error = Color(0xFFEF5350),
    outline = Color(0xFF3A4450),
)

@Composable
fun GpsWalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
