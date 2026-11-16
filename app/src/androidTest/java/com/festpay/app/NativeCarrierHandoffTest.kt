package com.festpay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import android.text.InputType
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.festpay.app.ui.screens.CarrierPaymentJourneyScreen
import com.festpay.app.data.*
import com.festpay.app.ui.screens.CarrierPaymentJourneyContent
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Isolated synthetic records/labels. No engine, real journal, carrier or authentication values. */
class NativeCarrierHandoffTest {
    @get:Rule val compose = createComposeRule()

    @Test fun observedClosureRoutesExistingJourneyToUnknownWithoutLaunchingOrSettling() {
        val session = FestPayAccessibilityService.UssdSessionData
        val id = UUID.randomUUID().toString()
        val model = PaymentViewModel(journalFactory = { error("No real journal") },
            repositoryFactory = { error("No real profile") }, sessionTransportFactory = { error("No launch") })
        model.selectedAttemptId = id
        try {
            session.reset(); session.attemptId = id
            compose.setContent {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "payment_journey") {
                    composable("payment_journey") { CarrierPaymentJourneyScreen(nav, model) }
                    composable("payment_status_unclear") { Text("Fixture outcome: UNKNOWN") }
                    composable("payment_successful") { Text("Forbidden success destination") }
                }
            }
            compose.runOnIdle { session.carrierDialogEnded = true }
            compose.onNodeWithText("Fixture outcome: UNKNOWN").assertExists()
            compose.onNodeWithText("Forbidden success destination").assertDoesNotExist()
            compose.runOnIdle { assertNotEquals(PaymentState.SUCCESS, model.paymentState) }
        } finally { compose.runOnIdle { session.reset() } }
    }

    @Test fun productionCollectorSkipsSyntheticPasswordWidgetWithoutModifyingOrClickingIt() {
        ActivityScenario.launch(UssdControlFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val canary = "SYNTHETIC_SECRET_CANARY"
                activity.field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                activity.field.setText(canary)
                val node = activity.field.createAccessibilityNodeInfo()
                assertTrue(node.isPassword)
                assertEquals("", FestPayAccessibilityService().collectAllTexts(node))
                assertEquals(canary, activity.field.text.toString()) // Synthetic local widget only.
                assertEquals(0, activity.clicks)
            }
        }
    }

    @Test fun nativeReviewAndPinHaveNoAppConfirmationOrEditableControl() {
        compose.setContent {
            CarrierPaymentJourneyContent(CarrierProgress(CarrierStage.REVIEW, 5,
                CarrierReview("synthetic@upi", "1.00", null), true), "synthetic@upi", "1.00", true, {})
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Confirm carrier details").assertDoesNotExist()
        compose.onNodeWithText("Confirm manually on the carrier screen only if its recipient and amount match. Keep the carrier dialog in front. V4SH will not press Confirm.").assertExists()
        compose.onNodeWithText("Financial outcome: UNKNOWN. Carrier observations do not establish settlement.").assertExists()
    }

    @Test fun nativeHandoffAndClosurePreserveSyntheticUnknownAndReservationAcrossReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_native_handoff_${UUID.randomUUID()}.db"
        try {
            val id = PaymentAttemptJournal(context, name).use { journal ->
                val id = journal.create(PaymentRequest("synthetic@upi", "", "1.00"))
                journal.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                val original = journal.records().single()
                val session = UUID.randomUUID().toString()
                val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
                listOf("1.Send Money 3.Check Balance", "Send money to 2.UPI ID", "Enter UPI ID", "Enter amount")
                    .forEachIndexed { i, label -> flow.inputResult(checkNotNull(flow.observe(label, true, i + 1L)), true) }
                assertNull(flow.observe("Confirm synthetic@upi INR 1.00 Press 1 to confirm", true, 5))
                journal.recordCarrierObservation(id, session, flow.progress)
                flow.observeAuthentication(6, true)
                assertEquals(CarrierStage.PIN, flow.progress.stage)
                journal.recordCarrierObservation(id, session, flow.progress)
                flow.observe("Transaction successful", false, 7)
                journal.recordCarrierObservation(id, session, flow.progress)
                val dialog = CarrierDialogLifecycle()
                dialog.observeOwnedWindow(10, 7); dialog.removedWindow(10, 8)
                assertTrue(dialog.consumeReturn(true, true, false, setOf(20)))
                // Only the display-only carrier wording may differ; identity, state and every financial field are identical.
                assertEquals(original.copy(carrierResult = "SUCCESS_WORDING"), journal.records().single())
                assertEquals(id, journal.unresolvedId())
                assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("Transaction successful"))
                try { journal.create(PaymentRequest("other@upi", "", "2.00")); fail("Reservation must block") }
                catch (_: IllegalStateException) { }
                id
            }
            PaymentAttemptJournal(context, name).use { journal ->
                journal.recover()
                assertEquals(id, journal.unresolvedId())
                assertEquals(PaymentState.UNKNOWN, journal.records().single().state)
                assertEquals(3, journal.carrierObservationCount(id))
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
}
