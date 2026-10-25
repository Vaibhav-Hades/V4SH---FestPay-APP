// File: Type.kt
package com.festpay.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle

// NOTE: Replace with actual font resources when available. Fallback to system fonts.
val BodoniModa = FontFamily.Serif

val IBMPlexSerif = FontFamily.SansSerif

val LibreCaslonText = FontFamily.Monospace

object V4SHTypography {
    val headline = TextStyle(
        fontFamily = BodoniModa,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
    val body = TextStyle(
        fontFamily = IBMPlexSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
    val label = TextStyle(
        fontFamily = LibreCaslonText,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
}