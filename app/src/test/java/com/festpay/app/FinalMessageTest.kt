package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

/** The final carrier message exactly as the physical Redmi/Airtel dialog showed it (user screenshot, 2026-11-06). */
class FinalMessageTest {
    private val real = "Carrier info\nYour payment to NALLURI HARSHA VARDHANI, for Rs.1.00 is successful (RefId: 214749357315) .\nSelect option to\n1. Save contact\n2. Exit"
    private val pin = "Enter UPI PIN to pay Rs. 1.00 to synthetic@upi"
    private val menu = "Carrier info\nSelect Option: State Bank Of India\n1. Send Money\n3. Check Balance"

    private fun atPin(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        it.inputResult(checkNotNull(it.observe(menu, true, 1)), true)
        it.inputResult(checkNotNull(it.observe("Send Money to\n1. Mobile No.\n3. UPI ID", true, 2)), true)
        it.inputResult(checkNotNull(it.observe("Enter UPI ID", true, 3)), true)
        it.inputResult(checkNotNull(it.observe("Enter Amount in Rs.", true, 4)), true)
        assertNull(it.observe(pin, true, 5)); assertEquals(CarrierStage.PIN, it.progress.stage)
    }

    @Test fun theReferenceIsReadOnlyWhenTheMessageShowsOneReferenceAndTheReviewedAmount() {
        assertEquals("214749357315", CarrierReference.parse(real, "1.00"))
        assertEquals("214749357315", CarrierReference.parse(real.replace("RefId", "Ref ID").replace("Rs.1.00", "INR 1"), "1.00"))
        assertNull("different amount", CarrierReference.parse(real, "2.00"))
        assertNull("no amount", CarrierReference.parse("Payment is successful (RefId: 214749357315)", "1.00"))
        assertNull("two amounts", CarrierReference.parse(real + " fee Rs.0.50", "1.00"))
        assertNull("two references", CarrierReference.parse(real + " Ref: 999999999999", "1.00"))
        assertNull("too short to be a reference", CarrierReference.parse("for Rs.1.00 is successful (RefId: 123)", "1.00"))
        assertNull("no reference", CarrierReference.parse("Your payment for Rs.1.00 is successful", "1.00"))
    }

    @Test fun theRealFinalMessageIsRecognisedEvenThoughItEndsWithAnInputBox() {
        val flow = atPin()
        // Since 2026-11-09 the final menu is closed with the carrier's own Exit option (never Save contact).
        assertEquals(CarrierInput(CarrierInputKind.EXIT, "2"), flow.observe(real, true, 6))
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(CarrierResultObservation.SUCCESS_WORDING, flow.progress.resultWording)
        assertEquals("214749357315", flow.progress.reference)
        assertEquals(com.festpay.app.ui.viewmodel.PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome(real))
    }

    @Test fun aSuccessMessageForADifferentAmountIsRecordedWithoutAReference() {
        val flow = atPin()
        flow.observe(real.replace("Rs.1.00", "Rs.9.00"), true, 6)
        assertEquals(CarrierStage.RESULT, flow.progress.stage); assertNull(flow.progress.reference)
    }

    @Test fun aFailureMessageEndsTheJourneyWithoutAReference() {
        val flow = atPin()
        flow.observe("Carrier info\nTransaction failed. Select option to 1. Retry 2. Exit", true, 6)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(CarrierResultObservation.FAILURE_WORDING, flow.progress.resultWording); assertNull(flow.progress.reference)
    }

    @Test fun theBeneficiaryAddedMessageThatPrecedesTheSuccessMessageIsWaitedOut() {
        val flow = atPin()
        assertNull(flow.observe("Carrier info\nNALLURI HARSHA VARDHANI has been successfully added to your beneficiary", true, 6))
        assertEquals(CarrierStage.PIN, flow.progress.stage); assertTrue(flow.progress.waitingForResult)
        assertNull(flow.observe("Carrier info\nPlease wait...", false, 7))
        flow.observe(real.replace("214749357315", "224434494707"), true, 8)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals("224434494707", flow.progress.reference)
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(flow.progress))
    }

    @Test fun aWrongPinPromptIsStillAnAuthenticationPromptNotAResult() {
        val flow = atPin()
        flow.observe("Incorrect UPI PIN. Enter UPI PIN", true, 6)
        assertNotEquals(CarrierStage.RESULT, flow.progress.stage)
    }
}
