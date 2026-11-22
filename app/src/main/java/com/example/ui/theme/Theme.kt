package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun buildDarkColorScheme(usePurpleAccent: Boolean) = darkColorScheme(
    primary = if (usePurpleAccent) PurpleAccent else CyanAccent,
    onPrimary = Color(0xFF140D26),
    primaryContainer = if (usePurpleAccent) PurpleContainer else CyanContainer,
    onPrimaryContainer = if (usePurpleAccent) PurpleAccent else CyanAccent,
    secondary = if (usePurpleAccent) CyanAccent else PurpleAccent,
    onSecondary = Color(0xFF0A2029),
    secondaryContainer = CardSurfaceElevated,
    onSecondaryContainer = SoftLavenderText,
    tertiary = MintGreen,
    onTertiary = Color(0xFF06261B),
    tertiaryContainer = MintContainer,
    onTertiaryContainer = MintGreen,
    background = ObsidianBackground,
    onBackground = SoftWhite,
    surface = CardSurface,
    onSurface = SoftWhite,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = SoftLavenderText,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = CardSurfaceSubtle,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = CardSurfaceElevated,
    outline = DividerStroke,
    outlineVariant = Color(0xFF252134),
    error = CoralRed,
    onError = Color(0xFF2D0B11),
    errorContainer = CoralContainer,
    onErrorContainer = CoralRed
)

private fun buildLightColorScheme(usePurpleAccent: Boolean) = lightColorScheme(
    primary = if (usePurpleAccent) PurpleAccentDeep else Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = if (usePurpleAccent) Color(0xFFE9E0FF) else Color(0xFFE0F2FE),
    onPrimaryContainer = if (usePurpleAccent) Color(0xFF23114D) else Color(0xFF082F49),
    secondary = Color(0xFF5D5675),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFD6CEE8),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun UltimateCleanerTheme(
    darkTheme: Boolean = true,
    usePurpleAccent: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        buildDarkColorScheme(usePurpleAccent)
    } else {
        buildLightColorScheme(usePurpleAccent)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
