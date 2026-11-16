package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

/** The root menu exactly as the physical Redmi/Airtel dialog showed it (user screenshot, 2026-11-06). */
class RealAirtelMenuTest {
    private val shown = "Carrier info\nSelect Option: State Bank Of India\n1. Send Money\n3. Check Balance\n4. My Profile\n5. Pending Requests\n6. Transactions\n7. UPI PIN"

    @Test fun theRealRootMenuYieldsSendMoney() {
        assertEquals("1", CarrierPaymentFlow.rootOption(shown))
    }

    @Test fun theSameMenuWithNonBreakingSpacesOrRepeatedTextStillYieldsSendMoney() {
        assertEquals("1", CarrierPaymentFlow.rootOption(shown.replace(' ', ' ')))
        assertEquals("1", CarrierPaymentFlow.rootOption(shown + "\n" + shown))
    }

    @Test fun theFlowOffersTheRootChoiceForTheRealMenuAndNeverRejectsIt() {
        for (text in listOf(shown, shown.replace(' ', ' '), shown + "\n" + shown)) {
            val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
            val input = flow.observe(text, true, 1)
            assertNotNull(text, input)
            assertEquals(CarrierInputKind.ROOT, input!!.kind); assertEquals("1", input.value)
            assertEquals(CarrierIssue.NONE, flow.progress.issue)
        }
    }

    @Test fun theRealMenuIsNotMistakenForAnAuthenticationOrRejectionPrompt() {
        assertFalse(UssdNavigationPolicy.isCarrierRejection(shown))
        assertFalse(AccessibilityTextPolicy.isSensitivePrompt(shown, true))
    }
}
