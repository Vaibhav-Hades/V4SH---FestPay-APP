package com.festpay.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.ui.theme.brandFonts
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The bundled brand fonts must really render heavier at Bold than at Normal (variable axes applied). */
class FontWeightRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun inkOf(tag: String): Int {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var dark = 0
        for (x in 0 until map.width) for (y in 0 until map.height) if (map[x, y].red < 0.5f) dark++
        return dark
    }

    private fun check(name: String, family: FontFamily) {
        compose.setContent {
            androidx.compose.foundation.layout.Column(Modifier.background(Color.White)) {
                for ((tag, weight) in listOf("normal" to FontWeight.Normal, "bold" to FontWeight.Bold)) {
                    Box(Modifier.testTag(tag)) {
                        Text("Hamburgefonstiv 1234", style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 28.sp, color = Color.Black))
                    }
                }
            }
        }
        compose.waitForIdle()
        val normal = inkOf("normal")
        val bold = inkOf("bold")
        assertTrue("$name: normal=$normal bold=$bold", normal > 0 && bold > normal * 1.12)
    }

    private val fonts get() = brandFonts(InstrumentationRegistry.getInstrumentation().targetContext.assets)
    @Test fun newsreaderHeadingBoldIsHeavierThanNormal() = check("Newsreader heading", fonts.heading)
    @Test fun newsreaderLabelBoldIsHeavierThanNormal() = check("Newsreader label", fonts.label)
    @Test fun newsreaderBodyBoldIsHeavierThanNormal() = check("Newsreader body", fonts.body)
}
