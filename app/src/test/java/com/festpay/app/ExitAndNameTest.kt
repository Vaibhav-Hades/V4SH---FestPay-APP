package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ExitAndNameTest {
    private val real = "Carrier info\nYour payment to NALLURI HARSHA VARDHANI, for Rs.1.00 is successful (RefId: 224434494707) .\nSelect option to\n1. Save contact\n2. Exit"
    private val menu = "Select Option: State Bank Of India\n1. Send Money\n3. Check Balance"

    private fun atPin(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        it.inputResult(checkNotNull(it.observe(menu, true, 1)), true)
        it.inputResult(checkNotNull(it.observe("Send Money to\n1. Mobile No.\n3. UPI ID", true, 2)), true)
        it.inputResult(checkNotNull(it.observe("Enter UPI ID", true, 3)), true)
        it.inputResult(checkNotNull(it.observe("Enter Amount in Rs.", true, 4)), true)
        it.observeAuthentication(5, true, "Enter UPI PIN")
        assertEquals(CarrierStage.PIN, it.progress.stage)
    }

    @Test fun exitIsFoundOnlyWhenTheCarriersOwnMenuOffersExactlyOne() {
        assertEquals("2", UssdNavigationPolicy.exitDigit(real))
        assertEquals("0", UssdNavigationPolicy.exitDigit("Select option\n1. Save contact\n0. Exit"))
        assertNull(UssdNavigationPolicy.exitDigit("1. Save contact"))
        assertNull(UssdNavigationPolicy.exitDigit("1. Exit\n2. Exit now"))
        assertNull("not a menu item", UssdNavigationPolicy.exitDigit("Please exit the dialog"))
    }

    @Test fun theFinalMessageIsClosedWithExitNeverSaveContact() {
        val flow = atPin()
        val input = flow.observe(real, true, 6)!!
        assertEquals(CarrierInputKind.EXIT, input.kind); assertEquals("2", input.value)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertTrue(CarrierPaymentFlow.matchesInput(input, real, true))
        assertFalse(CarrierPaymentFlow.matchesInput(input, real.replace("2. Exit", "2. Something else"), true))
        assertFalse(flow.progress.exited)
        flow.inputResult(input, true)
        assertTrue(flow.progress.exited); assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(flow.progress))
        assertNull("offered once only", flow.observe(real, true, 7))
    }

    @Test fun aFailedExitNeverChangesTheVerdictAndNoExitIsOfferedWithoutAnInputBoxOrMenu() {
        val flow = atPin(); val input = flow.observe(real, true, 6)!!
        flow.inputResult(input, false)
        assertEquals(CarrierStage.RESULT, flow.progress.stage); assertFalse(flow.progress.exited)
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(flow.progress))
        val noInput = atPin(); assertNull(noInput.observe(real, false, 6)); assertEquals(CarrierStage.RESULT, noInput.progress.stage)
        val noMenu = atPin(); assertNull(noMenu.observe(real.substringBefore("Select option"), true, 6)); assertEquals(CarrierStage.RESULT, noMenu.progress.stage)
    }

    @Test fun thePayeeNameIsReadFromTheCarriersOwnMessagesOnly() {
        assertEquals("NALLURI HARSHA VARDHANI", CarrierPayee.parse(real))
        assertEquals("NALLURI HARSHA VARDHANI", CarrierPayee.parse("Carrier info\nNALLURI HARSHA VARDHANI has been successfully added to your beneficiary"))
        assertNull(CarrierPayee.parse("Enter UPI ID")); assertNull(CarrierPayee.parse("Your payment to 9556861465@ibl, for Rs.1.00 is successful"))
        assertNull(CarrierPayee.parse("payment to X, for Rs.1"))
    }

    @Test fun theNameIsKeptFromTheBeneficiaryMessageAndSurvivesToTheResult() {
        val flow = atPin()
        flow.observe("Carrier info\nNALLURI HARSHA VARDHANI has been successfully added to your beneficiary", true, 6)
        assertEquals("NALLURI HARSHA VARDHANI", flow.progress.payeeName)
        flow.observe(real.replace("NALLURI HARSHA VARDHANI", "N H VARDHANI"), true, 7)
        assertEquals("N H VARDHANI", flow.progress.payeeName)
    }

    @Test fun theProfileLetterIsTheFirstLetterOfTheNameOrAQuestionMark() {
        assertEquals("N", UiPresentation.initial("Nalluri Harsha"))
        assertEquals("A", UiPresentation.initial("  asha"))
        assertEquals("R", UiPresentation.initial("\"Ravi\""))
        assertEquals("?", UiPresentation.initial(null)); assertEquals("?", UiPresentation.initial("   "))
    }
}
