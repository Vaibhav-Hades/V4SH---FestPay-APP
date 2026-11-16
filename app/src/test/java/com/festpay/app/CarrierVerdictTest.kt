package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CarrierVerdictTest {
    // ---- the PIN prompt: the failure seen on the physical phone (UNEXPECTED_AUTHENTICATION after the remark step) ----
    @Test fun realWorldPinPromptsAreRecognisedWhateverTheSurroundingWording() {
        for (ok in listOf("Enter UPI PIN", "Enter your UPI PIN", "Please enter 6 digit UPI PIN", "Enter 4 or 6 digit UPI PIN",
                "Do not share your UPI PIN. Enter UPI PIN to pay Rs.1.00", "Carrier info\nUPI PIN", "Pay Rs.1.00 to NAME. UPI PIN:",
                "Enter UPI PIN (never share it)")) {
            assertTrue(ok, UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(ok))
        }
    }

    @Test fun otpPasswordPinManagementAndErrorScreensAreStillNotAcceptedAsThePaymentPinPrompt() {
        for (bad in listOf("Enter OTP", "Password required", "Enter PIN", "Set UPI PIN", "Change UPI PIN", "Reset your UPI PIN",
                "Forgot UPI PIN", "Generate new UPI PIN", "Enter UPI PIN OTP required", "Incorrect UPI PIN", "Invalid UPI PIN. Enter UPI PIN",
                "Wrong UPI PIN", "Enter CVV and UPI PIN")) {
            assertFalse(bad, UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(bad))
        }
    }

    @Test fun theFlowReachesPinFromTheRemarkStepForARealisticPrompt() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        val menu = "Select Option: State Bank Of India\n1. Send Money\n3. Check Balance"
        flow.inputResult(checkNotNull(flow.observe(menu, true, 1)), true)
        flow.inputResult(checkNotNull(flow.observe("Send Money to\n1. Mobile No.\n3. UPI ID", true, 2)), true)
        flow.inputResult(checkNotNull(flow.observe("Enter UPI ID", true, 3)), true)
        flow.inputResult(checkNotNull(flow.observe("Enter Amount in Rs.", true, 4)), true)
        flow.inputResult(checkNotNull(flow.observe("Enter a remark (Enter 1 to skip)", true, 5)), true)
        assertEquals(CarrierStage.REMARK_SENT, flow.progress.stage)
        flow.observeAuthentication(6, UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt("Do not share your UPI PIN\nEnter UPI PIN"), "Do not share your UPI PIN\nEnter UPI PIN")
        assertEquals(CarrierStage.PIN, flow.progress.stage)
        assertEquals(CarrierIssue.NONE, flow.progress.issue)
    }

    // ---- the verdict policy ----
    private fun result(wording: CarrierResultObservation?, reference: String?, stage: CarrierStage = CarrierStage.RESULT) =
        CarrierProgress(stage, 1, resultWording = wording, reference = reference)

    @Test fun successNeedsSuccessWordingAndTheCarriersReference() {
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(result(CarrierResultObservation.SUCCESS_WORDING, "214749357315")))
        assertNull("no reference", CarrierOutcomePolicy.decide(result(CarrierResultObservation.SUCCESS_WORDING, null)))
    }

    @Test fun failureWordingClosesAsFailedAndUnclearWordingDecidesNothing() {
        assertEquals(CarrierOutcome.FAILED, CarrierOutcomePolicy.decide(result(CarrierResultObservation.FAILURE_WORDING, null)))
        assertNull(CarrierOutcomePolicy.decide(result(CarrierResultObservation.INCONCLUSIVE, null)))
        assertNull(CarrierOutcomePolicy.decide(result(null, null)))
    }

    @Test fun nothingIsDecidedBeforeTheResultStage() {
        for (stage in CarrierStage.values().filter { it != CarrierStage.RESULT }) {
            assertNull(stage.name, CarrierOutcomePolicy.decide(result(CarrierResultObservation.SUCCESS_WORDING, "214749357315", stage)))
        }
    }

    @Test fun theRealFinalMessageProducesASuccessVerdictAndAMismatchedAmountDoesNot() {
        val real = "Your payment to NALLURI HARSHA VARDHANI, for Rs.1.00 is successful (RefId: 214749357315) .\nSelect option to\n1. Save contact\n2. Exit"
        fun at(text: String): CarrierProgress {
            val f = CarrierPaymentFlow("synthetic@upi", "1.00")
            f.inputResult(checkNotNull(f.observe("Select Option:\n1. Send Money\n3. Check Balance", true, 1)), true)
            f.inputResult(checkNotNull(f.observe("Send Money to\n1. Mobile No.\n3. UPI ID", true, 2)), true)
            f.inputResult(checkNotNull(f.observe("Enter UPI ID", true, 3)), true)
            f.inputResult(checkNotNull(f.observe("Enter Amount in Rs.", true, 4)), true)
            f.observeAuthentication(5, true, "Enter UPI PIN")
            f.observe(text, true, 6)
            return f.progress
        }
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(at(real)))
        assertNull(CarrierOutcomePolicy.decide(at(real.replace("Rs.1.00", "Rs.9.00"))))
        assertEquals(CarrierOutcome.FAILED, CarrierOutcomePolicy.decide(at("Transaction failed. Select option to 1. Retry 2. Exit")))
    }

    @Test fun historyWordingNamesTheCarrierAsTheSource() {
        assertEquals("Successful · confirmed by carrier", UiPresentation.carrierOutcomeLabel("SUCCESS"))
        assertEquals("Failed · reported by carrier", UiPresentation.carrierOutcomeLabel("FAILED"))
        assertNull(UiPresentation.carrierOutcomeLabel(null))
    }
}
