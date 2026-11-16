package com.festpay.app

import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic labels shaped like the publicly documented *99# sequence (optional remark, then a
 * UPI PIN prompt stating the payment). Not carrier evidence; no Android/carrier/financial access.
 */
class StandardUssdFlowTest {
    private val root = "1. Send Money\n2. Request Money\n3. Check Balance\n4. My Profile"
    private val method = "Send Money to\n1. Mobile No.\n3. UPI ID\n4. Saved Beneficiary\n5. IFSC and A/c No."
    private val remark = "Enter a remark (Enter 1 to skip)"
    private val pin = "Enter UPI PIN to pay Rs. 1.00 to synthetic@upi"

    private fun step(flow: CarrierPaymentFlow, labels: String, time: Long, kind: CarrierInputKind): CarrierInput {
        val input = checkNotNull(flow.observe(labels, true, time))
        assertEquals(kind, input.kind); flow.inputResult(input, true); return input
    }
    private fun amountSent(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        assertEquals("1", step(it, root, 1, CarrierInputKind.ROOT).value)
        assertEquals("3", step(it, method, 2, CarrierInputKind.METHOD).value)
        step(it, "Enter UPI ID", 3, CarrierInputKind.RECIPIENT)
        assertEquals("1.00", step(it, "Paying to SAMPLE\nEnter Amount in Rs.", 4, CarrierInputKind.AMOUNT).value)
        assertEquals(CarrierStage.AMOUNT_SENT, it.progress.stage)
    }
    private fun remarkSkipped(): CarrierPaymentFlow = amountSent().also {
        assertEquals("1", step(it, remark, 5, CarrierInputKind.REMARK_SKIP).value)
        assertEquals(CarrierStage.REMARK_SENT, it.progress.stage)
        assertFalse(it.progress.amountAccepted)
    }

    @Test fun completeStandardSequenceStopsAtManualPinWithMatchingDetails() {
        val flow = remarkSkipped()
        assertNull(flow.observe(pin, true, 6))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertEquals(CarrierIssue.NONE, flow.progress.issue)
        assertTrue(flow.progress.amountAccepted)
        assertEquals(CarrierReview("synthetic@upi", "1.00", null), flow.progress.review)
        assertFalse(flow.progress.authorized)
        // Repeated PIN frames keep the observation and never offer an input.
        assertNull(flow.observe(pin, true, 7))
        assertNotNull(flow.progress.review)
        assertNull(flow.observe("Transaction successful", false, 8))
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("Transaction successful"))
    }

    @Test fun passwordFlaggedPinPromptUsesStaticInstructionOnly() {
        val flow = remarkSkipped()
        flow.observeAuthentication(6, UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(pin), pin)
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertTrue(flow.progress.amountAccepted)
    }

    @Test fun pinPromptDirectlyAfterAmountIsSupported() {
        val flow = amountSent()
        assertNull(flow.observe("Enter UPI PIN to pay Rs.1.00 to synthetic@upi.", true, 5))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertNotNull(flow.progress.review)
        val amountOnly = amountSent()
        assertNull(amountOnly.observe("Enter your UPI PIN to pay INR 1 to SAMPLE", true, 5))
        assertEquals(CarrierStage.PIN, amountOnly.progress.stage)
        assertTrue(amountOnly.progress.amountAccepted)
        assertNull(amountOnly.progress.review)
    }

    @Test fun pinPromptWithoutDetailsIsNotVerified() {
        val flow = remarkSkipped()
        assertNull(flow.observe("Enter UPI PIN", true, 6))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertFalse(flow.progress.amountAccepted)
        assertNull(flow.progress.review)
        // A recipient alone does not prove the amount.
        val recipientOnly = remarkSkipped()
        recipientOnly.observe("Enter UPI PIN to pay synthetic@upi", true, 6)
        assertFalse(recipientOnly.progress.amountAccepted)
    }

    // Changed 2026-11-06: on a physical payment this prompt check stopped observation before the carrier's success message.
    // The PIN screen cannot be blocked by V4SH, so a mismatch is now flagged loudly and observation continues.
    @Test fun wrongAmountOrRecipientInPinPromptIsFlaggedAndNeverTreatedAsAVerifiedMatch() {
        for (bad in listOf(pin.replace("1.00", "2.00"), pin.replace("synthetic@upi", "other@upi"),
                pin + " fee Rs. 1.00 and Rs. 0.50", "Enter UPI PIN to pay Rs. 1.00 to synthetic@upi.evil")) {
            val flow = remarkSkipped()
            assertNull(flow.observe(bad, true, 6))
            assertEquals(bad, CarrierStage.PIN, flow.progress.stage)
            assertTrue(bad, flow.progress.detailsMismatch)
            assertFalse(bad, flow.progress.amountAccepted); assertNull(bad, flow.progress.review)
            assertFalse(bad, flow.progress.authorized)
        }
    }

    @Test fun aMismatchedPinPromptStillAllowsTheFinalMessageToBeReadButAWrongAmountNeverYieldsAReference() {
        val flow = remarkSkipped()
        flow.observe(pin.replace("1.00", "2.00"), true, 6)
        assertTrue(flow.progress.detailsMismatch)
        flow.observe("Your payment to NAME, for Rs.2.00 is successful (RefId: 224434494707) .\nSelect option to 1. Save contact 2. Exit", true, 7)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertNull("the amount differs from the request", flow.progress.reference)
        assertNull(CarrierOutcomePolicy.decide(flow.progress))
    }

    @Test fun remarkSkipRequiresTheCarriersOwnUniqueInstruction() {
        assertEquals("1", UssdNavigationPolicy.remarkSkipDigit(remark))
        assertEquals("0", UssdNavigationPolicy.remarkSkipDigit("Enter remarks\n0. Skip"))
        for (bad in listOf("Enter remarks (optional)", "Enter remark. Press 1 to skip or 2 to skip",
                "Enter remark or UPI PIN (Enter 1 to skip)", "Enter amount or remark (Enter 1 to skip)", "Press 1 to skip")) {
            assertNull(bad, UssdNavigationPolicy.remarkSkipDigit(bad))
        }
        val unsupported = amountSent()
        assertNull(unsupported.observe("Enter remarks (optional)", true, 5))
        assertEquals(CarrierIssue.UNSUPPORTED, unsupported.progress.issue)
        val readOnly = amountSent()
        assertNull(readOnly.observe(remark, false, 5))
        assertEquals(CarrierIssue.UNSUPPORTED, readOnly.progress.issue)
    }

    @Test fun remarkBeforeAmountIsOutOfOrder() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        step(flow, root, 1, CarrierInputKind.ROOT); step(flow, method, 2, CarrierInputKind.METHOD)
        step(flow, "Enter UPI ID", 3, CarrierInputKind.RECIPIENT)
        assertNull(flow.observe(remark, true, 4))
        assertEquals(CarrierIssue.UNSUPPORTED, flow.progress.issue)
    }

    @Test fun remarkSkipIsSingleUseAndFailureNeverRetries() {
        val flow = remarkSkipped()
        assertNull(flow.observe(remark, true, 6))
        assertEquals(CarrierStage.REMARK_SENT, flow.progress.stage)
        assertEquals(CarrierIssue.NONE, flow.progress.issue)
        val pending = amountSent()
        val input = checkNotNull(pending.observe(remark, true, 5))
        assertNull(pending.observe(remark, true, 6))
        pending.inputResult(input, false)
        assertEquals(CarrierIssue.INPUT_UNCERTAIN, pending.progress.issue)
        assertNull(pending.observe(remark, true, 7))
    }

    @Test fun remarkSkipIsRevalidatedBeforeEachOsAction() {
        val input = CarrierInput(CarrierInputKind.REMARK_SKIP, "1")
        assertTrue(CarrierPaymentFlow.matchesInput(input, remark, true))
        assertFalse(CarrierPaymentFlow.matchesInput(input, remark, false))
        assertFalse(CarrierPaymentFlow.matchesInput(input, "Enter a remark (Enter 0 to skip)", true))
        assertFalse(CarrierPaymentFlow.matchesInput(input, pin, true))
    }

    @Test fun timeoutAtPinKeepsOutcomeUnknownAndIgnoresLaterWording() {
        val flow = remarkSkipped()
        flow.observe(pin, true, 6)
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        flow.interrupt(CarrierIssue.TIMEOUT)
        assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
        assertEquals(CarrierIssue.TIMEOUT, flow.progress.issue)
        assertNull(flow.progress.review); assertFalse(flow.progress.authorized)
        assertNull(flow.observe("Transaction successful", false, 7))
        assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
        assertNull(flow.progress.resultWording)
    }

    @Test fun reviewScreenAfterRemarkStillRequiresExactMatch() {
        val flow = remarkSkipped()
        assertNull(flow.observe("Confirm transfer to synthetic@upi Rs.1.00\nPress 1 to confirm", true, 6))
        assertEquals(CarrierStage.REVIEW, flow.progress.stage)
        val bad = remarkSkipped()
        bad.observe("Confirm transfer to other@upi Rs.1.00\nPress 1 to confirm", true, 6)
        assertEquals(CarrierIssue.MISMATCH, bad.progress.issue)
    }
}
