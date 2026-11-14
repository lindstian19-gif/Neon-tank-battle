package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TankDuelColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ArenaAbyss,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = NeonMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = NeonMagentaDim,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonGold,
    onTertiary = ArenaAbyss,
    background = ArenaAbyss,
    onBackground = TextPrimary,
    surface = ArenaSurface,
    onSurface = TextPrimary,
    surfaceVariant = ArenaSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TankDuelColorScheme,
        typography = TankDuelTypography,
        content = content
    )
}
