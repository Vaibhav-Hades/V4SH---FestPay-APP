package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase6Test {
    private val menu = "Select Option: State Bank Of India\n1. Send Money\n3. Check Balance"
    private val result = "Carrier info\nYour payment to NALLURI HARSHA VARDHANI, for Rs.1.00 is successful (RefId: 224434494707) ."
    private val exitMenu = "Select option to\n1. Save contact\n2. Exit"

    private fun atPin(): CarrierPaymentFlow = CarrierPaymentFlow("synthetic@upi", "1.00").also {
        it.inputResult(checkNotNull(it.observe(menu, true, 1)), true)
        it.inputResult(checkNotNull(it.observe("Send Money to\n1. Mobile No.\n3. UPI ID", true, 2)), true)
        it.inputResult(checkNotNull(it.observe("Enter UPI ID", true, 3)), true)
        it.inputResult(checkNotNull(it.observe("Enter Amount in Rs.", true, 4)), true)
        it.observeAuthentication(5, true, "Enter UPI PIN")
    }

    @Test fun exitIsStillChosenWhenTheMenuArrivesInALaterFrame() {
        val flow = atPin()
        assertNull("result frame has no input box", flow.observe(result, false, 6))
        assertEquals(CarrierStage.RESULT, flow.progress.stage); assertFalse(flow.progress.exited)
        val input = flow.observe(exitMenu, true, 7)!!
        assertEquals(CarrierInputKind.EXIT, input.kind); assertEquals("2", input.value)
        flow.inputResult(input, true)
        assertTrue(flow.progress.exited)
        assertNull("only once", flow.observe(exitMenu, true, 8))
    }

    @Test fun aDecidedResultIsNeverOverwrittenByALaterInterruption() {
        val flow = atPin()
        flow.observe(result, false, 6)
        flow.interrupt(CarrierIssue.INPUT_UNCERTAIN)
        assertEquals(CarrierStage.RESULT, flow.progress.stage)
        assertEquals(CarrierOutcome.SUCCESS, CarrierOutcomePolicy.decide(flow.progress))
    }

    @Test fun exitIsFoundWithOrWithoutPunctuationButNeverSave() {
        assertEquals("2", UssdNavigationPolicy.exitDigit("1 Save contact 2 Exit"))
        assertEquals("2", UssdNavigationPolicy.exitDigit("1:Save 2:Exit"))
        assertEquals("2", UssdNavigationPolicy.exitDigit(exitMenu))
        assertNull(UssdNavigationPolicy.exitDigit("Please exit the dialog"))
        assertNull(UssdNavigationPolicy.exitDigit("1. Save contact"))
    }

    @Test fun onlyAPlainNoticeCountsAsSomethingToDismissWithOk() {
        assertTrue(UssdNavigationPolicy.isInformationalNotice("Dear customer, UPI services will now start. Tap OK to continue.", false))
        assertFalse("has an input box", UssdNavigationPolicy.isInformationalNotice("Dear customer, tap OK", true))
        assertFalse("rejection", UssdNavigationPolicy.isInformationalNotice("Connection problem or invalid MMI code.", false))
        assertFalse("result", UssdNavigationPolicy.isInformationalNotice(result, false))
        assertFalse("menu", UssdNavigationPolicy.isInformationalNotice("1. Send Money 3. Check Balance", false))
        assertFalse("pin", UssdNavigationPolicy.isInformationalNotice("Enter UPI PIN", false))
        assertFalse("setup", UssdNavigationPolicy.isInformationalNotice("Please enter first 3 letters of your bank name", false))
        assertFalse("placeholder", UssdNavigationPolicy.isInformationalNotice("Processing...", false))
        assertFalse("empty", UssdNavigationPolicy.isInformationalNotice("   ", false))
    }

    @Test fun okButtonIsMatchedByExactLabelOnly() {
        assertTrue(UssdControlPolicy.isOk("OK")); assertTrue(UssdControlPolicy.isOk(" Ok. ")); assertTrue(UssdControlPolicy.isOk("okay"))
        assertFalse(UssdControlPolicy.isOk("Book now")); assertFalse(UssdControlPolicy.isOk("Cancel")); assertFalse(UssdControlPolicy.isOk(null))
    }

    @Test fun bankingGuardSleepsOnlyForBankingAppsWhenOnAndIdle() {
        assertTrue(BankingGuard.shouldSleep(true, "com.phonepe.app", false))
        assertFalse("guard off", BankingGuard.shouldSleep(false, "com.phonepe.app", false))
        assertFalse("own session running", BankingGuard.shouldSleep(true, "com.phonepe.app", true))
        assertFalse("other app", BankingGuard.shouldSleep(true, "com.whatsapp", false))
        assertFalse("v4sh itself", BankingGuard.shouldSleep(true, "com.festpay.app", false))
        assertFalse(BankingGuard.shouldSleep(true, null, false))
    }
}
