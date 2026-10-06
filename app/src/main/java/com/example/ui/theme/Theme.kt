package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FpvCyan = Color(0xFF00E5FF)
val FpvSky = Color(0xFF38BDF8)
val FpvAmber = Color(0xFFFF9100)
val FpvDarkBg = Color(0xFF0A0F1D)
val FpvDarkSurface = Color(0xFF0F172A)

private val FpvDarkColorScheme = darkColorScheme(
    primary = FpvCyan,
    secondary = FpvSky,
    tertiary = FpvAmber,
    background = FpvDarkBg,
    surface = FpvDarkSurface,
    onPrimary = Color(0xFF0A0F1D),
    onSecondary = Color(0xFF0A0F1D),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FpvDarkColorScheme,
        typography = Typography,
        content = content
    )
}
