// File: Theme.kt
package com.festpay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = V4SHColors.Primary,
    secondary = V4SHColors.Secondary,
    background = V4SHColors.Tertiary,
    surface = V4SHColors.Tertiary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

/**
 * V4SHTheme – applies the frozen Warm Editorial design system.
 * Dark theme is intentionally disabled as per design constraints.
 */
@Composable
fun V4SHTheme(
    // The caller cannot enable dark theme; always use light colors.
    content: @Composable () -> Unit
) {
    // Force light mode regardless of system setting.
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(
            headlineLarge = V4SHTypography.headline,
            bodyLarge = V4SHTypography.body,
            labelMedium = V4SHTypography.label
        ),
        // Shapes could be added here if needed; using defaults.
        content = content
    )
}