// SPDX-License-Identifier: GPL-3.0-only
package org.nothingessential.detector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neutral baseline palette. Replace these values when applying a custom design;
 * every Material 3 component picks the rest up from the theme.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF333333),
    onPrimary = Color(0xFFF8F8F8),
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF5F5F5F),
    onSecondary = Color(0xFFF8F8F8),
    secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = Color(0xFF1F1F1F),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE7E7E7),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F1),
    surfaceContainer = Color(0xFFECECEC),
    surfaceContainerHigh = Color(0xFFE7E7E7),
    surfaceContainerHighest = Color(0xFFE1E1E1),
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFC6C6C6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8E8E8),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3B3B3B),
    onPrimaryContainer = Color(0xFFF3F3F3),
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF353535),
    onSecondaryContainer = Color(0xFFEDEDED),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE9E9E9),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE9E9E9),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF444444),
)

@Composable
fun NothingEssentialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
