package com.lynchscreener.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = Color(0xFF052E1B),
    primaryContainer = Color(0xFF0F3D2E),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color(0xFF05203D),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF402D00),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark,
    outlineVariant = BorderSubtleDark,
    error = ScorePoor,
    onError = Color(0xFF450A0A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    outlineVariant = BorderSubtleLight,
    error = ScorePoor,
    onError = Color(0xFFFFFFFF),
)

/**
 * Dark mode is the default (fintech/terminal aesthetic). Pass `darkTheme = false`
 * for the clean light alternative.
 */
@Composable
fun LynchScreenerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLynchColors else LightLynchColors
    val scheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(LocalLynchColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LynchTypography,
            content = content,
        )
    }
}
