package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ADMusicPlayerTheme(
    themeMode: String = "Dark", // "Dark" or "Light"
    themeColor: String = "Cosmic Blue", // "Cosmic Blue", "Deep Emerald", "Cyberpunk Purple", "Neon Red"
    content: @Composable () -> Unit
) {
    val isDark = themeMode == "Dark"

    val primaryColor = when (themeColor) {
        "Deep Emerald" -> DeepEmerald
        "Cyberpunk Purple" -> CyberpunkPurple
        "Neon Red" -> NeonRed
        else -> CosmicBlue // Cosmic Blue
    }

    val secondaryColor = when (themeColor) {
        "Deep Emerald" -> DeepEmeraldSecondary
        "Cyberpunk Purple" -> CyberpunkPurpleSecondary
        "Neon Red" -> NeonRedSecondary
        else -> CosmicBlueSecondary
    }

    val tertiaryColor = when (themeColor) {
        "Deep Emerald" -> DeepEmeraldTertiary
        "Cyberpunk Purple" -> CyberpunkPurpleTertiary
        "Neon Red" -> NeonRedTertiary
        else -> CosmicBlueTertiary
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = tertiaryColor,
            background = DarkBg,
            surface = DarkSurface,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = tertiaryColor,
            background = LightBg,
            surface = LightSurface,
            onBackground = Color(0xFF141417),
            onSurface = Color(0xFF141417)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
