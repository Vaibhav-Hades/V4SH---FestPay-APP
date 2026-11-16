package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic labels only. No Android/carrier calls or financial authority. */
class CarrierPaymentFlowTest {
    private val root = "Carrier info\n2.Send Money\n5.Check Balance"
    private val method = "Send money to\n1.Mobile No\n8.UPI ID"
    private val review = "Confirm transfer to synthetic@upi Rs.1.00\nBeneficiary name: Sample User\nPress 1 to confirm"
    private fun sent(flow: CarrierPaymentFlow, labels: String, time: Long, kind: CarrierInputKind): CarrierInput {
        val input = flow.observe(labels, true, time)!!
        assertEquals(kind, input.kind); flow.inputResult(input, true); return input
    }
    private fun amount(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        assertEquals("2", sent(it, root, 1, CarrierInputKind.ROOT).value)
        assertEquals("8", sent(it, method, 2, CarrierInputKind.METHOD).value)
        sent(it, "Enter UPI ID", 3, CarrierInputKind.RECIPIENT)
        assertEquals(CarrierStage.AMOUNT_PENDING, it.progress.stage)
    }
    private fun atReview(): CarrierPaymentFlow = amount().also {
        sent(it, "Enter amount", 5, CarrierInputKind.AMOUNT)
        assertFalse(it.progress.amountAccepted)
        assertNull(it.observe(review, true, 6))
        assertTrue(it.progress.amountAccepted); assertEquals(CarrierStage.REVIEW, it.progress.stage)
    }
    @Test fun menuChoicesComeFromObservedLabelsNotFixedDigits() {
        val flow = amount()
        assertNull(CarrierPaymentFlow.rootOption("1.Buy data 3.Check Balance"))
        assertNull(CarrierPaymentFlow.rootOption("1.Send Money 2.Send Money 3.Check Balance"))
        assertNull(CarrierPaymentFlow.methodOption("3.UPI ID"))
        assertFalse(flow.progress.amountAccepted)
    }
    @Test fun observedAmountPromptAutomaticallyUsesTheFrozenReviewedValue() {
        val flow = amount()
        val input = flow.observe("Enter amount", true, 4)!!
        assertEquals(CarrierInputKind.AMOUNT, input.kind)
        assertEquals("1.00", input.value)
        assertEquals(CarrierStage.AMOUNT, flow.progress.stage)
        assertFalse(flow.progress.amountAccepted)
        flow.inputResult(input, true)
        assertEquals(CarrierStage.AMOUNT_SENT, flow.progress.stage)
        assertNull(flow.observe("Enter amount", true, 5))
        for (bad in listOf("0", "-1", "1.001", "NaN", "1e0")) {
            try { CarrierPaymentFlow("synthetic@upi", bad); fail("Invalid amount must not create a flow") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun submittingTextDoesNotEstablishInputAcceptance() {
        val flow = amount()
        sent(flow, "Enter amount", 5, CarrierInputKind.AMOUNT)
        assertEquals(CarrierStage.AMOUNT_SENT, flow.progress.stage)
        assertFalse(flow.progress.amountAccepted)
        assertNull(flow.observe("Enter amount", true, 6))
        assertFalse(flow.progress.amountAccepted)
        flow.observe(review, true, 7)
        assertTrue(flow.progress.amountAccepted)
    }
    @Test fun matchingCarrierReviewRequiresSeparateExplicitConfirmation() {
        val flow = atReview()
        assertEquals("Sample User", flow.progress.review!!.displayedName)
        assertNull(flow.observe(review, true, 7))
        val snapshot = flow.progress
        assertNull(flow.observe(review, true, 8))
        assertEquals(snapshot, flow.progress)
        flow.observe("Enter UPI PIN", true, 9)
        assertEquals(CarrierStage.PIN, flow.progress.stage)
    }
    @Test fun changedReviewInvalidatesApprovalAndOldUiRevision() {
        val flow = atReview(); val original = flow.progress.revision
        assertNull(flow.observe(review.replace("Sample User", "Another User"), true, 7))
        assertFalse(flow.progress.authorized)
        assertTrue(flow.progress.revision > original)
        assertNull(flow.observe(review, true, 8))
        assertFalse(flow.progress.authorized)
    }

    @Test fun wrongAmountOrRecipientCannotAdvanceToPin() {
        for (bad in listOf(review.replace("1.00", "2.00"), review.replace("synthetic@upi", "other@upi"))) {
            val flow = amount()
            sent(flow, "Enter amount", 5, CarrierInputKind.AMOUNT)
            assertNull(flow.observe(bad, true, 6))
            assertEquals(CarrierIssue.MISMATCH, flow.progress.issue)
            assertFalse(flow.progress.authorized)
        }
    }
    @Test fun duplicateAndStaleEventsNeverReplayInputs() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        sent(flow, root, 5, CarrierInputKind.ROOT)
        val snapshot = flow.progress
        assertNull(flow.observe(root, true, 5)); assertNull(flow.observe(method, true, 4))
        assertEquals(snapshot, flow.progress)
        assertNull(flow.observe(root, true, 6)); assertEquals(snapshot, flow.progress)
    }
    @Test fun missingOrUnsupportedConfirmationIsNeverGuessed() {
        val flow = atReview()
        assertNull(flow.observe(review.replace("Press 1 to confirm", "Enter 2 to confirm"), true, 7))
        val ambiguous = atReview()
        assertNull(ambiguous.observe(review + " Enter 2 to confirm", true, 7))
        assertEquals(CarrierStage.REVIEW, ambiguous.progress.stage)
        assertEquals("5", CarrierPaymentFlow.methodOption("Send money using 2.Account and IFSC 5.UPI ID"))
        val unsupported = amount()
        assertNull(unsupported.observe("Enter remarks (optional)", true, 5))
        assertEquals(CarrierIssue.UNSUPPORTED, unsupported.progress.issue)
    }
    @Test fun inputFailureAndDisconnectNeverRetryOrResume() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        val input = flow.observe(root, true, 1)!!
        flow.inputResult(input, false)
        assertEquals(CarrierIssue.INPUT_UNCERTAIN, flow.progress.issue)
        assertNull(flow.observe(root, true, 2))
        val next = atReview(); next.interrupt()
        assertFalse(next.progress.authorized); assertNull(next.progress.review)
        assertNull(next.observe(review, true, 9))
        assertEquals(CarrierStage.WAITING, CarrierPaymentFlow("synthetic@upi", "1.00").progress.stage)
    }
    @Test fun unexpectedAuthenticationStopsWithoutRequestingAnyInput() {
        val flow = amount()
        assertNull(flow.observe("Enter UPI PIN", true, 5))
        assertEquals(CarrierIssue.UNEXPECTED_AUTHENTICATION, flow.progress.issue)
        assertFalse(flow.progress.authorized)
    }
    @Test fun manualPinBoundaryAndResultWordingNeverAuthorizeSettlement() {
        for (wording in listOf("Transaction successful", "Transaction failed")) {
            val flow = atReview()
            assertNull(flow.observe(review, true, 7))
            assertNull(flow.observe("Enter UPI PIN", true, 8))
            assertEquals(CarrierStage.PIN, flow.progress.stage)
            assertNull(flow.progress.review)
            assertNull(flow.observe(wording, false, 9))
            assertEquals(CarrierStage.RESULT, flow.progress.stage)
            assertEquals(com.festpay.app.ui.viewmodel.PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome(wording))
        }
    }
    @Test fun loadingAndOutOfOrderPromptsCannotProveProgress() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        flow.observe("Please wait", false, 1)
        flow.observe("Enter amount", true, 2)
        assertEquals(CarrierStage.WAITING, flow.progress.stage)
        assertFalse(flow.progress.amountAccepted)
    }
    @Test fun staleAuthenticationAndResultEventsCannotAdvanceTheJourney() {
        val flow = atReview()
        assertNull(flow.observe(review, true, 7))
        flow.observeAuthentication(6, true)
        assertEquals(CarrierStage.REVIEW, flow.progress.stage)
        flow.observeAuthentication(10, true)
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertNull(flow.observe("Transaction successful", false, 9))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        flow.observe("Transaction successful", false, 11)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
    }
    @Test fun automaticAmountOfferIsSingleUseEvenBeforeInputResult() {
        val flow = amount()
        val input = flow.observe("Enter amount", true, 4)!!
        assertNull(flow.observe("Enter amount", true, 5))
        flow.inputResult(input, true)
        assertEquals(CarrierStage.AMOUNT_SENT, flow.progress.stage)
        assertNull(flow.observe("Enter amount", true, 6))
        assertFalse(flow.progress.amountAccepted)
        flow.observe(review, true, 7)
        assertTrue(flow.progress.amountAccepted)
        assertFalse(flow.progress.authorized)
    }
    @Test fun automaticAmountRequiresEditablePromptAndNeverRetriesFailureOrCrossesPin() {
        val flow = amount()
        assertNull(flow.observe("Enter amount", false, 4))
        assertEquals(CarrierStage.AMOUNT_PENDING, flow.progress.stage)
        assertNull(flow.observe("Enter UPI PIN", true, 5))
        assertEquals(CarrierIssue.UNEXPECTED_AUTHENTICATION, flow.progress.issue)
        assertNull(flow.observe("Enter amount", true, 6))
        val failed = amount()
        val input = failed.observe("Enter amount", true, 4)!!
        failed.inputResult(input, false)
        assertEquals(CarrierIssue.INPUT_UNCERTAIN, failed.progress.issue)
        assertNull(failed.observe("Enter amount", true, 5))
    }
}

