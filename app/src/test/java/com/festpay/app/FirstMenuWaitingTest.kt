package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

/** From the physical log: unrecognised non-editable frames arrived before the real menu and ended assistance. */
class FirstMenuWaitingTest {
    private val menu = "Carrier info\nSelect Option: State Bank Of India\n1. Send Money\n3. Check Balance\n4. My Profile"

    @Test fun unrecognisedTransientFramesBeforeTheFirstMenuAreWaitedOutAndTheMenuIsThenHandled() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        for ((i, text) in listOf("Carrier info\nRunning MMI code", "USSD code running...", "Carrier info\nSending request to network").withIndex()) {
            assertNull(text, flow.observe(text, false, 1L + i))
            assertEquals(CarrierStage.WAITING, flow.progress.stage); assertEquals(CarrierIssue.NONE, flow.progress.issue)
        }
        val input = flow.observe(menu, true, 10)
        assertEquals(CarrierInputKind.ROOT, input!!.kind); assertEquals("1", input.value)
    }

    @Test fun anExplicitConnectionFailureBeforeTheMenuStillStopsWithAReason() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        assertNull(flow.observe("Carrier info\nConnection problem or invalid MMI code.", false, 1))
        assertEquals(CarrierIssue.CARRIER_REJECTED, flow.progress.issue)
        assertEquals(CarrierStage.INTERRUPTED, flow.progress.stage)
    }

    @Test fun afterSomethingWasTypedAnUnknownFrameStillStopsAssistance() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        flow.inputResult(checkNotNull(flow.observe(menu, true, 1)), true)
        assertNull(flow.observe("Something unexpected happened", false, 2))
        assertEquals(CarrierIssue.UNSUPPORTED, flow.progress.issue)
    }

    @Test fun anUnknownEditableFrameBeforeTheMenuStillStopsAssistance() {
        val flow = CarrierPaymentFlow("synthetic@upi", "1.00")
        assertNull(flow.observe("Enter something odd", true, 1))
        assertEquals(CarrierIssue.UNSUPPORTED, flow.progress.issue)
    }
}
