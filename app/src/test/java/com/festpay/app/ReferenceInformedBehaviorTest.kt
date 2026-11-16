package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Behaviours learned from studying a reference USSD project, implemented independently here
 * (the reference carries no licence). Synthetic labels only: no Android, carrier or financial access.
 */
class ReferenceInformedBehaviorTest {
    private val root = "1. Send Money\n2. Request Money\n3. Check Balance"
    private val method = "Send Money to\n1. Mobile No.\n3. UPI ID\n4. Saved Beneficiary"
    private val remark = "Enter a remark (Enter 1 to skip)"
    private val pin = "Enter UPI PIN to pay Rs. 1.00 to synthetic@upi"

    private fun send(flow: CarrierPaymentFlow, labels: String, time: Long, kind: CarrierInputKind) {
        val input = checkNotNull(flow.observe(labels, true, time)) { labels }
        assertEquals(kind, input.kind); flow.inputResult(input, true)
    }
    private fun atAmountPending() = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        send(it, root, 1, CarrierInputKind.ROOT); send(it, method, 2, CarrierInputKind.METHOD)
        send(it, "Enter UPI ID", 3, CarrierInputKind.RECIPIENT)
        assertEquals(CarrierStage.AMOUNT_PENDING, it.progress.stage)
    }
    private fun atAmountSent() = atAmountPending().also {
        send(it, "Enter Amount in Rs.", 4, CarrierInputKind.AMOUNT)
        assertEquals(CarrierStage.AMOUNT_SENT, it.progress.stage)
    }

    // ---- transient filler frames ----
    @Test fun transientFillerFramesNeverStopAssistanceAndTheNextRealPromptStillWorks() {
        val flow = atAmountPending()
        for ((i, filler) in listOf("Processing...", "Processing your request", "Loading", "Connecting…").withIndex()) {
            assertNull(filler, flow.observe(filler, false, 4L + i))
            assertEquals(filler, CarrierStage.AMOUNT_PENDING, flow.progress.stage)
            assertEquals(filler, CarrierIssue.NONE, flow.progress.issue)
        }
        val input = checkNotNull(flow.observe("Enter Amount in Rs.", true, 20))
        assertEquals(CarrierInputKind.AMOUNT, input.kind)
    }

    @Test fun fillerDetectionIsNarrow() {
        assertTrue(UssdNavigationPolicy.isTransientPlaceholder("Processing...", false))
        assertFalse(UssdNavigationPolicy.isTransientPlaceholder("Processing...", true))
        assertFalse(UssdNavigationPolicy.isTransientPlaceholder("Enter amount. Processing fee applies", false))
        assertFalse(UssdNavigationPolicy.isTransientPlaceholder("Your payment is processing and may take a while. Check your bank later " + "x".repeat(40), false))
        assertFalse(UssdNavigationPolicy.isTransientPlaceholder("Payment processing", false))
    }

    @Test fun waitingForResultStillObservesWordingAtThePinStageButNeverDecidesTheOutcome() {
        val flow = atAmountSent()
        assertNull(flow.observe(pin, true, 5))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertNull(flow.observe("Processing...", false, 6))
        assertTrue(flow.progress.waitingForResult)
        assertNull(flow.observe("Transaction failed", false, 7))
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(CarrierIssue.NONE, flow.progress.issue)
        assertEquals(CarrierResultObservation.FAILURE_WORDING, flow.progress.resultWording)
        assertEquals(com.festpay.app.ui.viewmodel.PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("Transaction failed"))
    }

    // ---- carrier rejections before authentication ----
    @Test fun aRejectionAfterTheUpiIdStopsWithAnAccurateReasonInsteadOfWaitingSilently() {
        val flow = atAmountPending()
        // The carrier re-asks for the UPI ID with an error: previously this looked like a known prompt and V4SH waited.
        assertNull(flow.observe("Invalid UPI ID\nEnter UPI ID", true, 4))
        assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
        assertEquals(CarrierIssue.CARRIER_REJECTED, flow.progress.issue)
        assertFalse(flow.progress.authorized)
        // Nothing is ever replayed after a rejection.
        assertNull(flow.observe("Enter UPI ID", true, 5))
        assertNull(flow.observe("Enter Amount in Rs.", true, 6))
        assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
    }

    @Test fun rejectionsAreRecognisedAtEveryStageBeforeAuthentication() {
        val cases = listOf(
            "2 is not a valid selection\n1. Send Money 3. Check Balance" to CarrierStage.WAITING,
            "Could not find your bank" to CarrierStage.WAITING,
            "Invalid option" to CarrierStage.METHOD,
            "Insufficient balance" to CarrierStage.AMOUNT_SENT,
            "Amount exceeds limit. Enter Amount in Rs." to CarrierStage.AMOUNT_PENDING,
            "Service unavailable. Try again later." to CarrierStage.REMARK_SENT,
            "Session expired" to CarrierStage.AMOUNT_SENT)
        for ((text, stage) in cases) {
            val flow = when (stage) {
                CarrierStage.WAITING -> CarrierPaymentFlow("synthetic@upi", "1.00")
                CarrierStage.METHOD -> CarrierPaymentFlow("synthetic@upi", "1.00").also { send(it, root, 1, CarrierInputKind.ROOT) }
                CarrierStage.AMOUNT_PENDING -> atAmountPending()
                CarrierStage.REMARK_SENT -> atAmountSent().also { send(it, remark, 5, CarrierInputKind.REMARK_SKIP) }
                else -> atAmountSent()
            }
            assertNull(text, flow.observe(text, true, 50))
            assertEquals(text, CarrierIssue.CARRIER_REJECTED, flow.progress.issue)
            assertEquals(text, CarrierStage.INTERRUPTED, flow.progress.stage)
        }
    }

    @Test fun supportedPromptsAreNeverMistakenForRejections() {
        for (ok in listOf(root, method, "Enter UPI ID", "Enter Amount in Rs.", remark, pin,
                "Confirm transfer to synthetic@upi Rs.1.00\nPress 1 to confirm", "Paying to SAMPLE USER\nEnter Amount in Rs.")) {
            assertFalse(ok, UssdNavigationPolicy.isCarrierRejection(ok))
        }
    }

    @Test fun aRejectionNeverBecomesAFinancialOutcomeOrReleasesAnything() {
        val flow = atAmountSent()
        flow.observe("Transaction declined", true, 9)
        assertEquals(CarrierIssue.CARRIER_REJECTED, flow.progress.issue)
        assertEquals(com.festpay.app.ui.viewmodel.PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("Transaction declined"))
        assertFalse(PaymentIntegrityPolicy.allows(com.festpay.app.ui.viewmodel.PaymentState.PROCESSING, com.festpay.app.ui.viewmodel.PaymentState.FAILED))
        assertFalse(PaymentIntegrityPolicy.allows(com.festpay.app.ui.viewmodel.PaymentState.UNKNOWN, com.festpay.app.ui.viewmodel.PaymentState.CANCELLED))
    }

    // ---- submit control labels ----
    @Test fun submitButtonsAreMatchedExactlyAndSendIsPreferred() {
        assertEquals(0, UssdControlPolicy.submitRank("Send"))
        assertEquals(0, UssdControlPolicy.submitRank(" SEND "))
        assertEquals(1, UssdControlPolicy.submitRank("Reply"))
        assertEquals(2, UssdControlPolicy.submitRank("Submit"))
        assertEquals(3, UssdControlPolicy.submitRank("OK"))
        assertEquals(3, UssdControlPolicy.submitRank("Ok."))
        for (bad in listOf("Cancel", "Dismiss", "Send money", "Resend", "Okay later", "Block", "", null)) {
            assertNull(bad, UssdControlPolicy.submitRank(bad))
        }
    }
}
