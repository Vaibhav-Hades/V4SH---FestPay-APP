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
    val assets = androidx.compose.ui.platform.LocalContext.current.assets
    val typography = androidx.compose.runtime.remember(assets) { cachedTypeScale(assets) }
    MaterialTheme(
        colorScheme = LightColors,
        typography = typography,
        // Shapes could be added here if needed; using defaults.
        content = content
    )
}
/** Every role uses a brand family (headings, body, labels); sizes stay at the Material defaults unless set above. */
private fun v4shTypeScale(f: BrandFonts): androidx.compose.material3.Typography = androidx.compose.material3.Typography().let { d ->
    d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = f.heading),
        displayMedium = d.displayMedium.copy(fontFamily = f.heading),
        displaySmall = d.displaySmall.copy(fontFamily = f.heading),
        headlineLarge = V4SHTypography.headline(f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f.heading),
        headlineSmall = d.headlineSmall.copy(fontFamily = f.heading),
        titleLarge = d.titleLarge.copy(fontFamily = f.heading),
        titleMedium = d.titleMedium.copy(fontFamily = f.heading),
        titleSmall = d.titleSmall.copy(fontFamily = f.heading),
        bodyLarge = V4SHTypography.body(f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f.body),
        bodySmall = d.bodySmall.copy(fontFamily = f.body),
        labelLarge = d.labelLarge.copy(fontFamily = f.label),
        labelMedium = V4SHTypography.label(f),
        labelSmall = d.labelSmall.copy(fontFamily = f.label)
    )
}

@Volatile private var typeScaleCache: androidx.compose.material3.Typography? = null
/** Every screen wraps its content in V4SHTheme; build the font families and type scale once, not per screen. */
private fun cachedTypeScale(assets: android.content.res.AssetManager): androidx.compose.material3.Typography =
    typeScaleCache ?: v4shTypeScale(brandFonts(assets)).also { typeScaleCache = it }
