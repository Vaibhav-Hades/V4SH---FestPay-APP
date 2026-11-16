package com.festpay.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentViewModel

@Composable
fun CarrierPaymentJourneyScreen(navController: NavHostController, model: PaymentViewModel) {
    val session = FestPayAccessibilityService.UssdSessionData
    val connection = AccessibilityRuntime.connection.snapshot
    LaunchedEffect(session.carrierDialogEnded) {
        if (session.carrierDialogEnded && session.attemptId == model.selectedAttemptId) {
            navController.navigate("payment_status_unclear") { popUpTo("payment_journey") { inclusive = true } }
        }
    }
    val record = model.transactionLedger.singleOrNull { it.localTransactionId == model.selectedAttemptId }
    val correlated = record != null && record.localTransactionId == session.attemptId && !session.isBalanceCheck &&
        connection.sessionId == session.sessionId && connection.armed
    fun stop() { model.stopWaiting(); navController.navigate("payment_status_unclear") { popUpTo("payment_journey") { inclusive = true } } }
    BackHandler { stop() }
    Scaffold(topBar = { V4shTopBar(title = "Payment") }) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            CarrierPaymentJourneyContent(
                progress = if (correlated) session.paymentProgress else null,
                recipient = if (correlated) record!!.payeeVpa else "",
                amount = if (correlated) record!!.amount else "",
                active = correlated && connection.connected && session.operationActive,
                onStop = ::stop,
                carrierDetected = correlated && session.launchDisposition == LaunchDisposition.CARRIER_OBSERVED
            )
        }
    }
}

/** Same content exercised with isolated synthetic fixtures; no engine/launch capability. */
@Composable
fun CarrierPaymentJourneyContent(
    progress: CarrierProgress?, recipient: String, amount: String, active: Boolean,
    onStop: () -> Unit, carrierDetected: Boolean = false
) {
    val waitingOnCarrier = active && progress?.stage in setOf(CarrierStage.WAITING, CarrierStage.METHOD, CarrierStage.RECIPIENT,
        CarrierStage.AMOUNT_PENDING, CarrierStage.AMOUNT, CarrierStage.AMOUNT_SENT, CarrierStage.REMARK_SENT, CarrierStage.CONFIRMATION_SENT)
    Column(Modifier.fillMaxSize().background(SurfaceCream).verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Carrier payment journey", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
        // Shown only while an observed carrier step is genuinely pending; never at the PIN or after a result.
        if (waitingOnCarrier) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBurgundy)
        Text("Financial outcome: UNKNOWN. Carrier observations do not establish settlement.")
        if (carrierDetected) {
            Text("Airtel carrier interface detected. Payment outcome unverified.")
        }
        if (recipient.isNotBlank()) {
            Text("Intended UPI ID: $recipient")
            Text("Reviewed amount: INR $amount")
        }
        if (!active && progress?.stage !in setOf(CarrierStage.RESULT, CarrierStage.INTERRUPTED)) {
            Text("Screen assistance is inactive. No payment will be retried. Check Transactions and your official bank history.")
        }
        when (progress?.stage) {
            CarrierStage.AMOUNT -> {
                Text("Supplying the reviewed amount")
                Text("V4SH uses the amount you already reviewed when the carrier asks for it. You do not need to enter it again. Acceptance is not yet verified.")
            }
            CarrierStage.AMOUNT_SENT -> Text("Amount submission requested. Waiting for the carrier's next prompt; acceptance is verified only when the carrier shows the intended UPI ID and exact amount.")
            CarrierStage.REMARK_SENT -> Text("Optional remark skipped using the carrier's own skip instruction. No remark text was sent. Waiting for the carrier's next prompt.")
            CarrierStage.REVIEW -> {
                Text("Review carrier details", style = MaterialTheme.typography.titleLarge)
                Text("Observed on the carrier screen. This is not independently verified bank identity or payment completion.")
                progress.review?.let {
                    it.displayedName?.let { name -> Text("Carrier-displayed beneficiary name (unverified): $name") }
                    Text("Carrier UPI ID: ${it.recipient}")
                    Text("Carrier amount: INR ${it.amount}")
                }
                Text(if (progress.amountAccepted) "Carrier review matches the intended recipient and amount." else "The intended input has not been verified.")
                Text("Confirm manually on the carrier screen only if its recipient and amount match. Keep the carrier dialog in front. V4SH will not press Confirm.")
            }
            CarrierStage.CONFIRMATION_SENT -> Text("Confirmation requested. Waiting to observe the carrier's authentication screen. No authentication is inferred from a timer.")
            CarrierStage.PIN -> {
                if (!progress.waitingForResult) {
                    Text("Authentication required", style = MaterialTheme.typography.titleLarge)
                    Text(when {
                        progress.review != null -> "The carrier's PIN prompt shows UPI ID ${progress.review.recipient} and INR ${progress.review.amount}, matching your reviewed payment."
                        progress.amountAccepted -> "The carrier's PIN prompt shows INR $amount, matching your reviewed amount. It did not show the UPI ID; check the recipient on the carrier screen."
                        else -> "The carrier's PIN prompt did not show the recipient or amount, so V4SH could not verify them. If the carrier screen does not match your payment, cancel it there."
                    })
                    Text("Entering your UPI PIN on the carrier screen is your confirmation of this payment.")
                    Text("Enter your UPI PIN manually only on the carrier screen. V4SH never reads, records or enters it. Return here after the carrier responds.")
                } else Text("A non-sensitive carrier response was observed. Waiting for a usable carrier result; authentication and payment completion remain unverified.")
            }
            CarrierStage.RESULT -> Text("Carrier wording observed: ${progress.resultWording?.name ?: "INCONCLUSIVE"}. The saved financial outcome remains UNKNOWN because authorized transaction confirmation is unavailable. Do not pay again.")
            CarrierStage.INTERRUPTED -> if (progress.issue == CarrierIssue.TIMEOUT)
                Text("No carrier result was observed in time. The payment outcome remains UNKNOWN. Check the carrier screen and your official bank history, and do not pay again. No automatic retry was performed.")
            else Text("Assistance stopped: ${progress.issue.name.replace('_', ' ').lowercase()}. The payment outcome remains UNKNOWN. No automatic retry was performed. Inspect the carrier screen yourself; if its instructions or details are unclear, cancel it and check your official bank history before any further payment.")
            CarrierStage.WAITING -> Text("Carrier launch requested. Waiting for the actual supported menu; no response or acceptance is assumed.")
            CarrierStage.METHOD -> Text("Supported payment menu selection requested. Waiting for the carrier's next menu.")
            CarrierStage.RECIPIENT -> Text("UPI ID method selection requested. Waiting for the carrier's recipient prompt.")
            CarrierStage.AMOUNT_PENDING -> Text("Reviewed UPI ID submission requested. Waiting for the carrier's amount prompt; recipient acceptance is not yet verified.")
            else -> Text("Keep the carrier dialog in front. V4SH follows supported prompts using the payment details you already reviewed. Confirm and enter your PIN manually there. If the dialog ends, do not restart the payment.")
        }
        Text("Stay in the carrier interface until it ends. V4SH returns only when dialog closure can be observed; otherwise open V4SH yourself after closing it. Closing a dialog never proves payment completion. No automatic retry.")
        OutlinedButton(onClick = onStop) { Text("Stop assistance and view outcome") }
    }
}
