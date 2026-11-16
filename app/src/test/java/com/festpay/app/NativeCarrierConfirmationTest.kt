package com.festpay.app

import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NativeCarrierConfirmationTest {
    @Test fun waitingForResultRequiresAnObservedNonSensitiveResponse() {
        val flow = reviewFlow()
        flow.observe("Enter UPI PIN", true, 6)
        assertFalse(flow.progress.waitingForResult)
        flow.observe("Please wait", false, 7)
        assertTrue(flow.progress.waitingForResult)
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        flow.observe("Enter UPI PIN", true, 8)
        assertFalse(flow.progress.waitingForResult)
        assertFalse(PaymentIntegrityPolicy.confirmationAvailable)
    }
    private val review = "Confirm transfer to synthetic@upi INR 1.00 Press 1 to confirm"
    private fun reviewFlow(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also { flow ->
        listOf("1.Send Money 3.Check Balance", "Send money to 2.UPI ID", "Enter UPI ID", "Enter amount")
            .forEachIndexed { i, text -> flow.inputResult(checkNotNull(flow.observe(text, true, i + 1L)), true) }
        assertNull(flow.observe(review, true, 5))
        assertEquals(CarrierStage.REVIEW, flow.progress.stage)
    }
    @Test fun exactReviewNeverOffersConfirmationEvenWithRepeatedOrChangedControls() {
        val flow = reviewFlow()
        for (time in 6L..10L) {
            assertNull(flow.observe(review, time % 2L == 0L, time))
            assertFalse(flow.progress.authorized)
            assertEquals(CarrierStage.REVIEW, flow.progress.stage)
        }
        assertNull(flow.observe("Enter UPI PIN", true, 11))
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertNull(flow.progress.review)
    }
    @Test fun incorrectReviewCannotAuthorizeSubsequentAuthentication() {
        for (bad in listOf(review.replace("1.00", "2.00"), review.replace("synthetic@upi", "other@upi"))) {
            val flow = reviewFlow()
            flow.observe(bad, true, 6)
            flow.observeAuthentication(7, true)
            assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
            assertEquals(CarrierIssue.MISMATCH, flow.progress.issue)
        }
    }
    @Test fun arbitraryPasswordFlagsAndUnsupportedInstructionsAreNotAuthentication() {
        val flow = reviewFlow()
        flow.observeAuthentication(6) // Password flag without supported static instruction.
        assertEquals(CarrierIssue.UNEXPECTED_AUTHENTICATION, flow.progress.issue)
        for (label in listOf("Password required", "Enter PIN", "Enter OTP", "Set UPI PIN", "Enter UPI PIN OTP required", "Incorrect UPI PIN")) {
            assertFalse(label, UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(label))
        }
        assertTrue(UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt("Enter your UPI PIN"))
    }
    @Test fun staleAuthenticationCannotConsumeExactReviewOrProveFinality() {
        val flow = reviewFlow()
        flow.observeAuthentication(4, true)
        assertEquals(CarrierStage.REVIEW, flow.progress.stage)
        flow.observeAuthentication(6, true)
        flow.observe("Transaction successful", false, 7)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("Transaction successful"))
        assertNull(AccessibilityTextPolicy.readLabel(true, true, true) { error("PIN reader invoked") })
    }
    @Test fun windowRemovalRequiresObservedCurrentWindowAndCompleteNonPhoneSnapshot() {
        val dialog = CarrierDialogLifecycle()
        dialog.removedWindow(10, 1)
        assertFalse(dialog.canReturn(true, true, false, setOf(20)))
        dialog.observeOwnedWindow(10, 2)
        dialog.removedWindow(9, 3)
        assertFalse(dialog.canReturn(true, true, false, setOf(20)))
        dialog.removedWindow(10, 1)
        assertFalse(dialog.canReturn(true, true, false, setOf(20)))
        dialog.removedWindow(10, 4)
        assertFalse(dialog.canReturn(false, true, false, setOf(20)))
        assertFalse(dialog.canReturn(true, false, false, setOf(20)))
        assertFalse(dialog.canReturn(true, true, true, setOf(20)))
        assertFalse(dialog.canReturn(true, true, false, setOf(10, 20)))
        assertTrue(dialog.consumeReturn(true, true, false, setOf(20)))
        assertFalse(dialog.consumeReturn(true, true, false, setOf(20)))
    }
    @Test fun replacementWindowInvalidatesPendingReturn() {
        val dialog = CarrierDialogLifecycle()
        dialog.observeOwnedWindow(10, 1); dialog.removedWindow(10, 2)
        dialog.observeOwnedWindow(11, 3)
        assertFalse(dialog.consumeReturn(true, true, false, setOf(20)))
        dialog.removedWindow(10, 4)
        assertFalse(dialog.consumeReturn(true, true, false, setOf(20)))
        dialog.removedWindow(11, 5)
        assertTrue(dialog.consumeReturn(true, true, false, setOf(20)))
    }
    @Test fun wrongAttemptCommandSessionAndConnectionNeverOwnHandoff() {
        val host = object : UssdSessionHost {
            override fun connectionAlive() = true
            override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String) = false
        }
        val connection = AccessibilityConnection()
        val epoch = connection.connect(host)
        val command = OwnedUssdCommand.payment(UUID.randomUUID().toString(), "synthetic@upi", "1.00")
        connection.claim(command); connection.initialize(host, command.sessionId, epoch)
        connection.acknowledge(command, epoch); connection.arm(host, command, epoch)
        assertTrue(connection.matches(command, epoch))
        assertFalse(connection.matches(command.copy(attemptId = UUID.randomUUID().toString()), epoch))
        assertFalse(connection.matches(command.copy(sessionId = UUID.randomUUID().toString()), epoch))
        assertFalse(connection.matches(command.copy(amount = "2.00"), epoch))
        assertFalse(connection.matches(command, "old-generation"))
        connection.release(command.sessionId)
        assertFalse(connection.matches(command, epoch))
    }
}
