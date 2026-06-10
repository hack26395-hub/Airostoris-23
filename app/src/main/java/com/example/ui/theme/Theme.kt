package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPink,
    tertiary = NeonGreen,
    background = NeonObsidian,
    surface = NeonCardSlate,
    onPrimary = Color(0xFF0A0A0C),
    onSecondary = Color.White,
    onBackground = NeonOffWhite,
    onSurface = NeonOffWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Cyberneon theme by default for spectacular neon interface
    dynamicColor: Boolean = false, // Disable dynamic colors so we don't wash out our beautiful neon tints
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = Typography,
        content = content
    )
}
