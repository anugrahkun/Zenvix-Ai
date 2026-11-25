package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ZenvixDarkColorScheme = darkColorScheme(
    primary = ZenvixTerracottaLight,
    onPrimary = Color(0xFF1A0A04),
    primaryContainer = Color(0xFF472114),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = ZenvixAmber,
    onSecondary = Color(0xFF2B170A),
    secondaryContainer = Color(0xFF33261E),
    onSecondaryContainer = Color(0xFFF5DFD0),
    tertiary = Color(0xFF81C7A8),
    onTertiary = Color(0xFF0A291C),
    background = ObsidianBackground,
    onBackground = IvoryInk,
    surface = ObsidianBackground,
    onSurface = IvoryInk,
    surfaceVariant = CharcoalSurface,
    onSurfaceVariant = WarmStoneInk,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    outline = BronzeOutline,
    outlineVariant = Color(0xFF2A2520),
    error = ErrorCoral,
    onError = Color(0xFF690005)
)

private val ZenvixLightColorScheme = lightColorScheme(
    primary = ZenvixTerracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DB),
    onPrimaryContainer = Color(0xFF471605),
    secondary = ZenvixBronze,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E4DA),
    onSecondaryContainer = Color(0xFF361D12),
    tertiary = EmeraldBadge,
    onTertiary = Color.White,
    background = ParchmentBackground,
    onBackground = WarmInk,
    surface = ParchmentBackground,
    onSurface = WarmInk,
    surfaceVariant = AlabasterSurface,
    onSurfaceVariant = WarmMutedInk,
    surfaceContainerLow = AlabasterSurface,
    surfaceContainer = AlabasterSurface,
    surfaceContainerHigh = AlabasterSurfaceHigh,
    outline = SandOutline,
    outlineVariant = Color(0xFFEBE4DA),
    error = ErrorCrimson,
    onError = Color.White
)

val ZenvixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZenvixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZenvixDarkColorScheme else ZenvixLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZenvixTypography,
        shapes = ZenvixShapes,
        content = content
    )
}
