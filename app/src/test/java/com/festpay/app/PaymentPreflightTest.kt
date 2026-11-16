package com.festpay.app

import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import org.junit.Assert.*
import org.junit.Test

class PaymentPreflightTest {
    @Test fun closingAnUninitiatedDraftHasNoFinancialRecord() {
        val model = PaymentViewModel()
        assertTrue(model.markCancelled())
        assertEquals(PaymentState.CANCELLED, model.paymentState)
        assertTrue(model.transactionLedger.isEmpty())
        assertNull(model.selectedAttemptId)
    }
    @Test fun `busy session rejects before creating a financial attempt`() {
        val session = FestPayAccessibilityService.UssdSessionData
        val previous = session.operationActive
        try {
            session.operationActive = true
            val model = PaymentViewModel()
            var returned: PaymentState? = null
            model.startPayment("recipient@upi", "Recipient", "1.00", null) { returned = it }
            assertEquals(PaymentState.IDLE, returned)
            assertEquals(PaymentState.IDLE, model.paymentState)
            assertTrue(model.transactionLedger.isEmpty())
            assertNull(model.selectedAttemptId)
            assertNotNull(model.operationError)
        } finally { session.operationActive = previous }
    }

    @Test fun `invalid preflight does not manufacture an unknown transaction`() {
        val session = FestPayAccessibilityService.UssdSessionData
        val previous = session.operationActive
        try {
            session.operationActive = false
            val model = PaymentViewModel()
            var returned: PaymentState? = null
            model.startPayment("9876543210", "Recipient", "1.00", null) { returned = it }
            assertEquals(PaymentState.IDLE, returned)
            assertEquals(PaymentState.IDLE, model.paymentState)
            assertTrue(model.transactionLedger.isEmpty())
            assertNull(model.selectedAttemptId)
        } finally { session.operationActive = previous }
    }
}
