package com.festpay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.screens.PaymentProgressScreen
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Synthetic journals only. A carrier's wording is shown in history as unverified and never changes the financial state. */
class CarrierResultHistoryTest {
    @get:Rule val compose = createComposeRule()
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun theCarriersLastWordingIsExposedButTheAttemptStaysUnknownAndReserved() {
        val name = "test_carrier_result_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, name).use { j ->
                val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))
                j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH")
                assertNull(j.records().single().carrierResult)
                j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.PIN, 1))
                assertNull("No wording observed yet", j.records().single().carrierResult)
                val session = UUID.randomUUID().toString()
                j.recordCarrierObservation(id, session, CarrierProgress(CarrierStage.RESULT, 2, resultWording = CarrierResultObservation.FAILURE_WORDING))
                assertEquals("FAILURE_WORDING", j.records().single().carrierResult)
                Thread.sleep(5)
                j.recordCarrierObservation(id, session, CarrierProgress(CarrierStage.RESULT, 3, resultWording = CarrierResultObservation.SUCCESS_WORDING))
                val record = j.records().single()
                assertEquals("The latest wording wins", "SUCCESS_WORDING", record.carrierResult)
                assertNotEquals(PaymentState.SUCCESS, record.state); assertNotEquals(PaymentState.FAILED, record.state)
                assertNull(record.bankReference); assertEquals(id, j.unresolvedId())
            }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun theProgressScreenShowsOperationalStepsAndTheUnverifiedNote() {
        val session = FestPayAccessibilityService.UssdSessionData
        compose.runOnIdle { session.recipientVpa = "synthetic@upi"; session.amount = "1.00"; session.paymentProgress = CarrierProgress(CarrierStage.PIN, 5) }
        compose.setContent { PaymentProgressScreen(rememberNavController()) }
        compose.onNodeWithText("Payment in progress").assertExists()
        compose.onNodeWithText("synthetic@upi").assertExists()
        compose.onNodeWithText("Type your UPI PIN on the carrier screen. V4SH never sees or enters it.").assertExists()
        // Owner request: the verdict screens. Success needs the carrier's reference; no confirmation is shown as unsuccessful.
        compose.runOnIdle { session.paymentProgress = CarrierProgress(CarrierStage.RESULT, 6, resultWording = CarrierResultObservation.SUCCESS_WORDING, reference = "214749357315") }
        compose.onNodeWithText("Payment successful").assertExists()
        compose.onNodeWithText("Ref 214749357315").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.runOnIdle { session.paymentProgress = CarrierProgress(CarrierStage.RESULT, 7, resultWording = CarrierResultObservation.SUCCESS_WORDING) }
        compose.onNodeWithText("Payment unsuccessful").assertExists()
        compose.onNodeWithText("Payment successful").assertDoesNotExist()
        compose.runOnIdle { session.paymentProgress = CarrierProgress(CarrierStage.INTERRUPTED, 8, issue = CarrierIssue.CARRIER_REJECTED) }
        compose.onNodeWithText("Payment unsuccessful").assertExists()
        compose.runOnIdle { session.paymentProgress = null; session.recipientVpa = ""; session.amount = "" }
    }
}
