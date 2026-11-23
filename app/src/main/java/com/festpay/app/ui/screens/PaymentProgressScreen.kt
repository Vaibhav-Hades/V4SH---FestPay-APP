package com.festpay.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.CarrierOutcome
import com.festpay.app.data.CarrierOutcomePolicy
import com.festpay.app.data.CarrierStage
import com.festpay.app.data.UiPresentation

/** Round profile letter, e.g. the first letter of the payee's name. */
@Composable
internal fun InitialAvatar(name: String?, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFFFD4BE)), contentAlignment = Alignment.Center) {
        Text(UiPresentation.initial(name), style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
    }
}

/** Animated tick (success) or cross (unsuccessful). Draws only after the verdict is known; never plays mid-payment. */
@Composable
internal fun ResultMark(success: Boolean) {
    val draw = remember(success) { Animatable(0f) }
    LaunchedEffect(success) { draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val color = if (success) Color(0xFF2B4C3F) else Color(0xFF93000A)
    Canvas(Modifier.size(112.dp)) {
        val p = draw.value
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        // ring sweeps in over the first half
        drawArc(color = color, startAngle = -90f, sweepAngle = 360f * minOf(1f, p * 2f), useCenter = false, style = stroke,
            topLeft = Offset(stroke.width, stroke.width), size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke.width, size.height - 2 * stroke.width))
        val t = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
        fun seg(a: Offset, b: Offset, from: Float, to: Float) {
            val local = ((t - from) / (to - from)).coerceIn(0f, 1f)
            if (local > 0f) drawLine(color, a, Offset(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local), stroke.width, StrokeCap.Round)
        }
        val w = size.width; val h = size.height
        if (success) {
            seg(Offset(w * 0.28f, h * 0.52f), Offset(w * 0.44f, h * 0.68f), 0f, 0.4f)
            seg(Offset(w * 0.44f, h * 0.68f), Offset(w * 0.74f, h * 0.34f), 0.4f, 1f)
        } else {
            seg(Offset(w * 0.34f, h * 0.34f), Offset(w * 0.66f, h * 0.66f), 0f, 0.5f)
            seg(Offset(w * 0.66f, h * 0.34f), Offset(w * 0.34f, h * 0.66f), 0.5f, 1f)
        }
    }
}

/**
 * Live view of what V4SH is doing while the carrier dialog is on top, then the result. Steps are operational only; the
 * verdict comes from the carrier's own final message (success needs its printed reference and the reviewed amount).
 */
@Composable
fun PaymentProgressScreen(navController: NavHostController) {
    val session = FestPayAccessibilityService.UssdSessionData
    val vpa = remember { session.recipientVpa }
    val amount = remember { session.amount }
    val progress = session.paymentProgress
    var furthest by remember { mutableIntStateOf(0) }
    val rank = progress?.let { UiPresentation.carrierRank(it.stage) } ?: 0
    if (rank > furthest) furthest = rank
    val interrupted = progress?.stage == CarrierStage.INTERRUPTED
    val finished = progress?.stage == CarrierStage.RESULT
    val verdict = progress?.let { CarrierOutcomePolicy.decide(it) }
    val success = verdict == CarrierOutcome.SUCCESS
    SpeakOnSuccess(success, amount, progress?.payeeName)
    // Owner request: no carrier success message means it is shown as unsuccessful (the attempt itself stays open in history).
    val unsuccessful = interrupted || (finished && !success)
    val name = progress?.payeeName
    val goHome = { navController.navigate("home") { popUpTo("home") { inclusive = false } }; Unit }
    BackHandler { goHome() }

    Scaffold(topBar = { V4shTopBar(title = "Payment") }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (success || unsuccessful) {
                ResultMark(success)
                Text(if (success) "Payment successful" else "Payment unsuccessful",
                    style = MaterialTheme.typography.headlineLarge.copy(color = if (success) PrimaryBurgundy else MaterialTheme.colorScheme.error), textAlign = TextAlign.Center)
            } else {
                Text("Payment in progress", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), modifier = Modifier.fillMaxWidth())
            }
            if (vpa.isNotBlank()) {
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(name)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (name != null) Text(name, style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                            Text(vpa, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text("₹$amount", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                            if (success && progress?.reference != null) Text("Ref ${progress.reference}", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            if (name != null) Text("Name printed by the carrier", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                    }
                }
            }
            if (!success && !unsuccessful) {
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        UiPresentation.PROGRESS_STEPS.forEachIndexed { index, step ->
                            val done = furthest > step.first
                            val active = !done && (index == 0 || furthest > UiPresentation.PROGRESS_STEPS[index - 1].first)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    done -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2B4C3F))
                                    active -> CircularProgressIndicator(Modifier.size(22.dp), color = PrimaryBurgundy, strokeWidth = 2.dp)
                                    else -> Icon(Icons.Default.RadioButtonUnchecked, null, tint = SecondaryUmber)
                                }
                                Text(step.second, style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (active) PrimaryBurgundy else OnSurfaceVariant, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal))
                            }
                        }
                    }
                }
            }
            when {
                success -> Text("Confirmed by the carrier's own message. V4SH does not query your bank.", style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                interrupted -> Text(UiPresentation.interruptionText(progress!!.issue), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error), textAlign = TextAlign.Center)
                finished -> Text(UiPresentation.carrierResultText(progress!!.resultWording), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error), textAlign = TextAlign.Center)
                progress?.detailsMismatch == true -> Text("The carrier's PIN prompt shows a different UPI ID or amount than you requested. Check it carefully before entering your PIN, or cancel on the carrier screen.", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold))
                furthest >= 6 -> Text("Type your UPI PIN on the carrier screen. V4SH never sees or enters it.", style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy))
                else -> Text("V4SH is filling the carrier menu for you. Keep the carrier dialog open.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            }
            if (unsuccessful) session.lastCarrierMessage?.let { Text("The carrier said: \"$it\"", style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center) }
            if (unsuccessful) Text("Not confirmed by the carrier means V4SH could not see a success message. If money left your account, check Transactions and resolve the attempt.",
                style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), textAlign = TextAlign.Center)
            Button(onClick = { goHome() }, shape = largeCardShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (success || unsuccessful) "Done" else "Back to Home")
            }
        }
    }
}
