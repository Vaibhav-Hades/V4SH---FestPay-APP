package com.festpay.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.festpay.app.data.*
import com.festpay.app.ui.screens.CarrierPaymentJourneyContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Non-settling UI fixtures. No service, engine, dialer or real journal access. */
class CarrierJourneyUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun carrierAmountStageShowsFrozenAmountWithoutAnotherEntryOrConfirmation() {
        compose.setContent {
            CarrierPaymentJourneyContent(CarrierProgress(CarrierStage.AMOUNT, 4), "synthetic@upi", "1.00", true,
                {})
        }
        compose.onNodeWithText("Reviewed amount: INR 1.00").assertExists()
        compose.onNodeWithText("Supplying the reviewed amount").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Use reviewed amount").assertDoesNotExist()
        compose.onNodeWithText("Confirm carrier details").assertDoesNotExist()
    }
    @Test fun carrierNameIsClearlyUnverifiedAndConfirmationRemainsNative() {
        var called = 0
        compose.setContent {
            CarrierPaymentJourneyContent(CarrierProgress(CarrierStage.REVIEW, 9,
                CarrierReview("synthetic@upi", "1.00", "Synthetic Name"), true), "synthetic@upi", "1.00", true,
                {})
        }
        compose.onNodeWithText("Carrier-displayed beneficiary name (unverified): Synthetic Name").assertExists()
        compose.onNodeWithText("Financial outcome: UNKNOWN. Carrier observations do not establish settlement.").assertExists()
        compose.runOnIdle { assertEquals(0, called) }
        compose.onNodeWithText("Confirm carrier details").assertDoesNotExist()
        compose.onNodeWithText("Confirm manually on the carrier screen only if its recipient and amount match. Keep the carrier dialog in front. V4SH will not press Confirm.").assertExists()
        compose.runOnIdle { assertEquals(0, called) }
    }
    @Test fun disconnectedSessionDisablesEveryCarrierSubmission() {
        compose.setContent {
            CarrierPaymentJourneyContent(CarrierProgress(CarrierStage.REVIEW, 9,
                CarrierReview("synthetic@upi", "1.00", null), true), "synthetic@upi", "1.00", false,
                {})
        }
        compose.onNodeWithText("Confirm carrier details").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun pinStageHasGuidanceOnlyAndNoEditableField() {
        compose.setContent {
            CarrierPaymentJourneyContent(CarrierProgress(CarrierStage.PIN, 10), "synthetic@upi", "1.00", true,
                {})
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Enter your UPI PIN manually only on the carrier screen. V4SH never reads, records or enters it. Return here after the carrier responds.").assertExists()
    }
    @Test fun pinPromptDetailsAreShownOnlyWhenObservedAndNeverAsSettlement() {
        var progress by mutableStateOf(CarrierProgress(CarrierStage.PIN, 11, CarrierReview("synthetic@upi", "1.00", null), true))
        compose.setContent { CarrierPaymentJourneyContent(progress, "synthetic@upi", "1.00", true, {}) }
        compose.onNodeWithText("The carrier's PIN prompt shows UPI ID synthetic@upi and INR 1.00, matching your reviewed payment.").assertExists()
        compose.onNodeWithText("Financial outcome: UNKNOWN. Carrier observations do not establish settlement.").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { progress = CarrierProgress(CarrierStage.PIN, 12) }
        compose.onNodeWithText("The carrier's PIN prompt did not show the recipient or amount, so V4SH could not verify them. If the carrier screen does not match your payment, cancel it there.").assertExists()
        compose.onNodeWithText("The carrier's PIN prompt shows UPI ID synthetic@upi and INR 1.00, matching your reviewed payment.").assertDoesNotExist()
        compose.runOnIdle { progress = CarrierProgress(CarrierStage.REMARK_SENT, 13) }
        compose.onNodeWithText("Optional remark skipped using the carrier's own skip instruction. No remark text was sent. Waiting for the carrier's next prompt.").assertExists()
    }
}
