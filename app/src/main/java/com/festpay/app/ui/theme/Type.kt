// File: Type.kt
package com.festpay.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Newsreader (SIL Open Font License 1.1; licence text in docs/licenses). One variable font file in assets/fonts,
// loaded by path so Android applies the weight and optical-size axes (the res/font route ignored the weight axis;
// see FontWeightRenderTest). Optical size 36 for headings, 16 for body and labels.
class BrandFonts(val heading: FontFamily, val body: FontFamily, val label: FontFamily)

@OptIn(ExperimentalTextApi::class)
private fun newsreader(assets: AssetManager, opticalSize: Float): FontFamily =
    FontFamily(listOf(400, 500, 600, 700).map { w ->
        Font("fonts/Newsreader.ttf", assets, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)))
    })

fun brandFonts(assets: AssetManager): BrandFonts = BrandFonts(
    heading = newsreader(assets, 36f),
    body = newsreader(assets, 16f),
    label = newsreader(assets, 16f)
)

object V4SHTypography {
    fun headline(f: BrandFonts) = TextStyle(fontFamily = f.heading, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    fun body(f: BrandFonts) = TextStyle(fontFamily = f.body, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    fun label(f: BrandFonts) = TextStyle(fontFamily = f.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
}
