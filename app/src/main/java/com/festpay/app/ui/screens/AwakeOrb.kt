package com.festpay.app.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.festpay.app.FestPayAccessibilityService
import kotlinx.coroutines.delay

enum class OrbState { ASLEEP, AWAKE, WORKING }

/**
 * A living status light for Accessibility: a slow grey breath when V4SH sleeps, a steady burgundy glow when it is awake,
 * fast ripples while a carrier session runs, and a shrinking ring that counts down to the automatic sleep.
 */
@Composable
fun AwakeOrb(awake: Boolean, size: Dp = 160.dp, autoSleepOn: Boolean = true, modifier: Modifier = Modifier) {
    val session = FestPayAccessibilityService.UssdSessionData
    val working = awake && session.operationActive
    val state = when { working -> OrbState.WORKING; awake -> OrbState.AWAKE; else -> OrbState.ASLEEP }
    val periodMs = when (state) { OrbState.ASLEEP -> 4200; OrbState.AWAKE -> 2400; OrbState.WORKING -> 900 }
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "pulse")

    // Countdown to the automatic sleep, from the end of the last session.
    var remaining by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(awake, working, autoSleepOn) {
        while (true) {
            val ended = session.lastSessionEndedElapsed
            remaining = if (awake && !working && autoSleepOn && ended != null)
                (1f - (SystemClock.elapsedRealtime() - ended) / 9_000f).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }

    val core = when (state) { OrbState.ASLEEP -> Color(0xFF9A8F88); OrbState.AWAKE -> Color(0xFF6B1F2A); OrbState.WORKING -> Color(0xFFB3261E) }
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            val coreR = r * 0.42f
            val ripples = if (state == OrbState.ASLEEP) 1 else 2
            for (i in 0 until ripples) {
                val t = (pulse + i * 0.5f) % 1f
                val alpha = (1f - t) * if (state == OrbState.ASLEEP) 0.18f else 0.35f
                drawCircle(core.copy(alpha = alpha), radius = coreR + (r - coreR) * t, center = c)
            }
            val breathe = if (state == OrbState.ASLEEP) 1f + 0.04f * kotlin.math.sin(pulse * 6.2832f) else 1f
            drawCircle(core, radius = coreR * breathe, center = c)
            drawCircle(Color.White.copy(alpha = if (state == OrbState.ASLEEP) 0.25f else 0.9f), radius = coreR * 0.28f, center = c)
            if (remaining > 0f) drawArc(Color(0xFF2B4C3F), -90f, 360f * remaining, false,
                topLeft = Offset(c.x - r * 0.95f, c.y - r * 0.95f), size = Size(r * 1.9f, r * 1.9f), style = Stroke(width = 6f))
        }
    }
}
